package com.example.todo.service;

import com.example.todo.model.Priority;
import com.example.todo.model.RecurrenceRule;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * タスクに関するビジネスロジックを提供するサービスクラス。
 * <p>
 * このクラスはタスクの取得、保存、状態更新、削除などの操作を行います。
 * </p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

	private final TaskRepository taskRepository;

	/**
	 * 全タスクをソート順・作成日時の昇順で取得します（読み取り専用トランザクション）。
	 *
	 * @return タスクのリスト
	 */
	@Transactional(readOnly = true)
	public List<Task> getAllTasks() {
		// ソート条件を作成（sortOrderの昇順、次にcreatedAtの昇順）
		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));

		// トップレベルタスク（parentTaskIdがnull＝サブタスクではない）のみを対象にする
		Specification<Task> spec = Specification.where(TaskSpecifications.isTopLevel());
		List<Task> tasks = taskRepository.findAll(spec, sort);
		attachSubtaskProgress(tasks);
		return tasks;
	}

	/**
	 * 指定 ID のタスクを取得します（読み取り専用）。
	 *
	 * @param id タスクID
	 * @return Optional に包まれた Task
	 */
	@Transactional(readOnly = true)
	public Optional<Task> getTaskById(Long id) {

		// タスクをIDで取得し、Optionalで返す
		return taskRepository.findById(id);
	}

	/**
	 * 複数条件（AND結合）でタスクを検索する。
	 * <p>
	 * 各条件は未指定（null・空文字・"ALL"相当）の場合は絞り込まれない。 結果は一覧表示と同じ順序（ソート順・作成日時の昇順）で返す。
	 * </p>
	 *
	 * @param status     絞り込み対象のステータス（null可）
	 * @param due        期限フィルタ種別（{@link TaskSpecifications#DUE_TODAY_OR_EARLIER}
	 *                   等、null・空可）
	 * @param priority   絞り込み対象の優先度（null可）
	 * @param keyword    タイトル・詳細に対する部分一致キーワード（null・空可）
	 * @param categoryId 絞り込み対象のカテゴリID（null可）
	 * @param noCategory 「カテゴリなし」のタスクのみに絞り込む場合 true
	 * @return 条件に合致するタスクのリスト
	 */
	@Transactional(readOnly = true)
	public List<Task> searchTasks(TaskStatus status, String due, Priority priority, String keyword, Long categoryId,
			boolean noCategory) {

		// Specificationは検索条件を表現するためのインターフェース
		// SQLのWHERE句にあたる条件を、Javaのコードとして組み立てられるようにする仕組み
		// 動的な検索条件（条件が来たり来なかったりする）」を、きれいに組み合わせられるようにする
		// 条件に応じて作っていたメソッドをここだけに集約できる。
		Specification<Task> spec = Specification.where(TaskSpecifications.hasStatus(status))
				.and(TaskSpecifications.hasPriority(priority))
				.and(TaskSpecifications.hasCategory(categoryId, noCategory))
				.and(TaskSpecifications.keywordContains(keyword))
				.and(TaskSpecifications.dueFilter(due, LocalDate.now())).and(TaskSpecifications.isTopLevel());

		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
		List<Task> tasks = taskRepository.findAll(spec, sort);
		attachSubtaskProgress(tasks);
		return tasks;
	}

	/**
	 * トップレベルタスクのリストに、各タスクのサブタスク進捗（{@code subtaskDoneCount} /
	 * {@code subtaskTotalCount}）を集計して設定する。
	 * <p>
	 * タスク1件ごとに問い合わせる（N+1）のを避けるため、対象タスクIDをまとめて1回のクエリで取得する。
	 * </p>
	 *
	 * @param topLevelTasks 進捗を設定する対象のトップレベルタスクのリスト
	 */
	private void attachSubtaskProgress(List<Task> topLevelTasks) {
		if (topLevelTasks.isEmpty()) {
			return;
		}
		List<Long> ids = topLevelTasks.stream().map(Task::getId).collect(Collectors.toList());
		List<Task> allSubtasks = taskRepository.findByParentTaskIdIn(ids);
		Map<Long, List<Task>> subtasksByParent = allSubtasks.stream()
				.collect(Collectors.groupingBy(Task::getParentTaskId));

		for (Task task : topLevelTasks) {
			List<Task> subtasks = subtasksByParent.getOrDefault(task.getId(), List.of());
			// 進捗 = DONE件数 ÷ （CANCELLEDを除いた全サブタスク件数）。
			// サブタスクが0件、またはすべてCANCELLEDの場合はtotalが0になる
			// （0除算は呼び出し側＝テンプレート/JSで「total > 0」を見て回避する）
			long total = subtasks.stream().filter(s -> s.getStatus() != TaskStatus.CANCELLED).count();
			long done = subtasks.stream().filter(s -> s.getStatus() == TaskStatus.DONE).count();
			task.setSubtaskTotalCount((int) total);
			task.setSubtaskDoneCount((int) done);
		}
	}

	/**
	 * 指定した親タスクに紐づくサブタスクを、一覧と同じ順序（ソート順・作成日時の昇順）で取得する。
	 * <p>
	 * 一覧画面の展開アイコンクリック時のAjax取得（{@code GET /api/tasks/{id}/subtasks}）から呼び出される。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 * @return サブタスクのリスト
	 * @throws IllegalArgumentException 指定IDの親タスクが存在しない場合（{@code RestApiExceptionHandler}により404に変換される）
	 */
	@Transactional(readOnly = true)
	public List<Task> getSubtasks(Long parentId) {
		if (!taskRepository.existsById(parentId)) {
			throw new IllegalArgumentException("Task not found: " + parentId);
		}
		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
		return taskRepository.findByParentTaskId(parentId, sort);
	}

	/**
	 * タスクを保存します（新規作成／更新）。
	 * <p>
	 * 更新対象のタスクIDが指定されている場合、対象タスクが存在するか確認し、存在しない場合は例外をスローします。
	 * これは、編集中に対象タスクが別タブ等で既に削除されていた場合に、 そのタスクが保存操作によって復活してしまう挙動を防ぐための仕様です。
	 * </p>
	 * <p>
	 * また、{@code form.html}（タスク編集フォーム）は {@code completedAt} を保持する項目を持たないため、
	 * 何もしなければステータスが変化していない場合でも、フォーム送信のたびに完了日時が {@code null} で
	 * 上書きされてしまう不具合がある。これを防ぐため、更新時は必ず既存レコードを取得し、
	 * {@link #resolveCompletedAt(TaskStatus, TaskStatus, LocalDateTime)}
	 * でステータスの変化前後を比較した上で 完了日時を引き継ぐ／再設定／解除する。
	 * </p>
	 *
	 * @param task 保存対象のタスク
	 * @return 保存後のタスク
	 * @throws IllegalArgumentException 更新対象のタスクIDが指定されているが既に削除されている場合、 または
	 *                                  {@code parentTaskId} に指定した親タスクが存在しないか、
	 *                                  既にサブタスク（1階層制限違反）である場合
	 */
	public Task saveTask(Task task) {

		// 新規作成時は「もともとDONEでなかった」ものとして扱う（フォーム送信経由での繰り返し判定用）
		TaskStatus previousStatus = null;

		// idが指定されている（＝更新のつもり）場合、対象がまだ存在するか確認する
		if (task.getId() != null) {
			Task existing = taskRepository.findById(task.getId())
					.orElseThrow(() -> new IllegalArgumentException("Task not found: " + task.getId()));

			previousStatus = existing.getStatus();

			// フォームにstatus欄が無い（新規作成直後の遷移等）場合は、既存のステータスを「変更なし」として扱う
			TaskStatus newStatus = task.getStatus() != null ? task.getStatus() : existing.getStatus();
			task.setCompletedAt(resolveCompletedAt(existing.getStatus(), newStatus, existing.getCompletedAt()));
		}

		// サブタスクは1階層まで
		// parentTaskIdが設定されている場合、その親タスク自身がさらに親（parentTaskId）を持っていないか確認する。
		// 親タスクが存在しない場合、またはその親自身が既にサブタスクである場合は保存を拒否する。
		if (task.getParentTaskId() != null) {
			Task parent = taskRepository.findById(task.getParentTaskId())
					.orElseThrow(() -> new IllegalArgumentException("親タスクが見つかりません: " + task.getParentTaskId()));
			if (parent.getParentTaskId() != null) {
				throw new IllegalArgumentException("サブタスクの下にさらにサブタスクを作成することはできません。");
			}
			// 自分自身（保存対象のタスク）が既に他タスクの親（＝サブタスクを持つ）である場合、
			// それを誰かのサブタスクにしてしまうと「孫階層」ができてしまうため禁止する
			// （フォームの「親タスク」選択欄経由での1階層制限違反を防ぐために必要な対応）。
			if (task.getId() != null && !taskRepository.findByParentTaskId(task.getId(), Sort.unsorted()).isEmpty()) {
				throw new IllegalArgumentException("既にサブタスクを持つタスクを別のタスクのサブタスクにすることはできません。");
			}

			// サブタスク自身には繰り返し設定を持たせない
			// 繰り返しは親タスク（トップレベルタスク）側でのみ扱い、親タスクが繰り返る際にサブタスクも一緒に複製する
			// フォーム改ざん等でrecurrenceRuleが送られてきた場合に備え、ここで強制的にNONEへ戻す。
			task.setRecurrenceRule(RecurrenceRule.NONE);
		}

		// タスクを保存（新規作成または更新）し、保存後のタスクを返す
		Task saved = taskRepository.save(task);

		// フォーム経由でステータスをDONEに変更した場合も、繰り返し設定があれば次回分を自動生成する
		triggerRecurrenceIfNewlyCompleted(saved, previousStatus);

		return saved;
	}

	/**
	 * タスク編集フォームの「親タスク」選択欄に表示する候補一覧を取得する。
	 * <p>
	 * 候補はトップレベルタスク（{@code parentTaskId} が null）のうち、 編集対象のタスク自身（自己参照を防ぐため）を除外したもの。
	 * </p>
	 * <p>
	 * 注意: 既にサブタスクを持つタスク（他タスクの親）も候補から除外しない。 1つの親タスクに複数のサブタスクを紐づけることは許容される仕様であり、
	 * ここで除外してしまうと「1個目のサブタスクを追加した時点で、2個目以降の サブタスクの親として選べなくなる」という不具合が起きていたため。
	 * 「孫階層を作らせない」という制約は、編集対象のタスク自身が既にサブタスクを 持っている場合に{@link #saveTask}側で弾く形で別途対応済み。
	 * </p>
	 *
	 * @param excludeTaskId 除外する（編集中の）タスクID。新規作成時は null
	 * @return 親タスク選択欄の候補一覧
	 */
	@Transactional(readOnly = true)
	public List<Task> getParentTaskCandidates(Long excludeTaskId) {
		return getAllTasks().stream().filter(t -> excludeTaskId == null || !t.getId().equals(excludeTaskId))
				.collect(Collectors.toList());
	}

	/**
	 * タスクの状態を更新します。状態が DONE の場合は完了日時を設定し、それ以外では完了日時をクリアします。
	 * <p>
	 * サブタスク機能に伴い、親子間のステータスは以下の通り連動します
	 * </p>
	 * <ul>
	 * <li>親→子: トップレベルタスクをDONEにすると、紐づく全サブタスクもDONEにする</li>
	 * <li>子→親: サブタスクをDONEにした結果、兄弟サブタスク（CANCELLEDを除く）が
	 * 全てDONEになった場合、親タスクも自動的にDONEにする</li>
	 * <li>子→親: 親タスクがDONEの状態で、紐づくサブタスクをDONE以外に変更した場合、
	 * 親タスクをIN_PROGRESSに戻す（完了日時もクリアする）</li>
	 * </ul>
	 *
	 * @param id     更新対象のタスクID
	 * @param status 設定するステータス
	 * @return 更新後のタスク
	 * @throws IllegalArgumentException 指定IDのタスクが存在しない場合
	 */
	public Task updateStatus(Long id, TaskStatus status) {

		// タスクをIDで取得し、存在しない場合は例外をスロー
		// findByIdはOptionalを返すので、存在しない場合の処理を簡潔に書ける
		// orElseThrowを使って、タスクが存在しない場合にIllegalArgumentExceptionをスローする
		Task task = taskRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

		TaskStatus oldStatus = task.getStatus();
		task.setStatus(status);
		task.setCompletedAt(resolveCompletedAt(oldStatus, status, task.getCompletedAt()));
		Task saved = taskRepository.save(task);

		// 繰り返し設定（recurrenceRule）があるタスクがDONE以外のステータスだったタスクをDONEにした場合、
		// 次回期日を持つ新しいタスクを自動生成する。
		// 既にDONEだったタスクを再度DONEで保存しただけの場合は生成しない。
		triggerRecurrenceIfNewlyCompleted(saved, oldStatus);

		if (saved.getParentTaskId() == null) {
			// 親タスク（parentTaskIdがnull＝トップレベル）をDONEにした場合、紐づく全サブタスクもDONEにする
			// 確認ダイアログ自体はフロント（app.js）側の責務のため、このメソッドが呼ばれた時点では
			// ユーザーが既に確認済みとみなして無条件にカスケードする）
			if (status == TaskStatus.DONE) {
				cascadeDoneToSubtasks(saved.getId());
			}
		} else {
			// サブタスク自身のステータスを変更した場合の「子→親」連動（独自追加仕様）
			if (status == TaskStatus.DONE) {
				cascadeDoneToParentIfAllSubtasksDone(saved.getParentTaskId());
			} else {
				revertParentFromDoneIfNeeded(saved.getParentTaskId());
			}
		}

		// タスクを保存して更新後の状態を返す
		return saved;
	}

	/**
	 * サブタスクがDONEになった結果、兄弟サブタスク（{@code CANCELLED} を除く）が全てDONEになった場合、 親タスクも自動的に
	 * {@link TaskStatus#DONE} にする。
	 * <p>
	 * 親タスクが既にDONEの場合は何もしない。
	 * サブタスクが存在しない、またはサブタスクが全て{@code CANCELLED}の場合は対象外（親自身のステータスのみで判断する）。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void cascadeDoneToParentIfAllSubtasksDone(Long parentId) {
		Task parent = taskRepository.findById(parentId).orElse(null);
		if (parent == null || parent.getStatus() == TaskStatus.DONE) {
			return;
		}
		List<Task> siblings = taskRepository.findByParentTaskId(parentId, Sort.unsorted());
		List<Task> effectiveSiblings = siblings.stream().filter(s -> s.getStatus() != TaskStatus.CANCELLED)
				.collect(Collectors.toList());
		if (effectiveSiblings.isEmpty()) {
			// 全てCANCELLED、またはサブタスクが存在しない場合は親自身の判断に委ねる（連動しない）
			return;
		}
		boolean allDone = effectiveSiblings.stream().allMatch(s -> s.getStatus() == TaskStatus.DONE);
		if (!allDone) {
			return;
		}
		TaskStatus parentOldStatus = parent.getStatus();
		parent.setStatus(TaskStatus.DONE);
		parent.setCompletedAt(resolveCompletedAt(parentOldStatus, TaskStatus.DONE, parent.getCompletedAt()));
		Task savedParent = taskRepository.save(parent);
		// 子→親の自動DONE化経由でも、親タスク自身に繰り返し設定があれば次回分を生成する
		triggerRecurrenceIfNewlyCompleted(savedParent, parentOldStatus);
	}

	/**
	 * 親タスクがDONEの状態で、紐づくサブタスクがDONE以外に変更された場合、 親タスクを {@link TaskStatus#IN_PROGRESS}
	 * に戻す。
	 * <p>
	 * 親タスクが既にDONE以外の場合は何もしない。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void revertParentFromDoneIfNeeded(Long parentId) {
		Task parent = taskRepository.findById(parentId).orElse(null);
		if (parent == null || parent.getStatus() != TaskStatus.DONE) {
			return;
		}
		parent.setStatus(TaskStatus.IN_PROGRESS);
		parent.setCompletedAt(resolveCompletedAt(TaskStatus.DONE, TaskStatus.IN_PROGRESS, parent.getCompletedAt()));
		taskRepository.save(parent);
	}

	/**
	 * 指定した親タスクに紐づく全サブタスクのステータスを {@link TaskStatus#DONE} にする。
	 * <p>
	 * 既に {@code DONE} のサブタスクは完了日時を上書きしないよう {@link #resolveCompletedAt} を経由する。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void cascadeDoneToSubtasks(Long parentId) {
		List<Task> subtasks = taskRepository.findByParentTaskId(parentId, Sort.unsorted());
		for (Task sub : subtasks) {
			TaskStatus subOldStatus = sub.getStatus();
			if (subOldStatus == TaskStatus.DONE) {
				continue;
			}
			sub.setStatus(TaskStatus.DONE);
			sub.setCompletedAt(resolveCompletedAt(subOldStatus, TaskStatus.DONE, sub.getCompletedAt()));
			taskRepository.save(sub);
		}
	}

	/**
	 * ステータスの変化前後を比較し、適切な完了日時（{@code completedAt}）を決定する。
	 * <p>
	 * {@link #saveTask}（フォーム経由の保存）と {@link #updateStatus}（ステータス変更API）の
	 * 両方から共通で呼び出すことで、どちらの経路でステータスを変更しても一貫した挙動になるようにする。
	 * 特に「DONE→DONE（ステータスに変化がない）」を区別できるようにしておくことは、
	 * 「本当に今完了したのか、既に完了済みのタスクを再保存しただけなのか」を正しく見分けるためにも必要となる。
	 * </p>
	 * <ul>
	 * <li>DONE以外 → DONE（新たに完了した）: 現在日時を新たに設定する</li>
	 * <li>DONE → DONE（ステータスに変化なし）: 既存の完了日時をそのまま引き継ぐ（上書きしない）</li>
	 * <li>DONE → DONE以外: {@code null} に戻す</li>
	 * <li>DONE以外 → DONE以外: 変化なし（{@code null} のまま）</li>
	 * </ul>
	 *
	 * @param oldStatus           変更前のステータス（新規作成の場合は {@code null} でもよい）
	 * @param newStatus           変更後のステータス
	 * @param existingCompletedAt 変更前の完了日時（DONE→DONEの場合に引き継ぐ値）
	 * @return 決定後の完了日時
	 */
	private LocalDateTime resolveCompletedAt(TaskStatus oldStatus, TaskStatus newStatus,
			LocalDateTime existingCompletedAt) {
		if (newStatus != TaskStatus.DONE) {
			return null;
		}
		return oldStatus == TaskStatus.DONE ? existingCompletedAt : LocalDateTime.now();
	}

	/**
	 * 「今まさにDONEへ遷移したか」を判定し、該当する場合のみ繰り返しタスクの次回分生成を行う。
	 * <p>
	 * {@link #updateStatus}・{@link #saveTask}・{@link #cascadeDoneToParentIfAllSubtasksDone}
	 * の3つの「DONEに遷移しうる経路」すべてから共通で呼び出すための窓口
	 * （{@link #cascadeDoneToSubtasks} はサブタスク自身が対象であり、サブタスクは常に
	 * {@code recurrenceRule = NONE} 固定のため、そもそも繰り返し生成の対象外であり呼び出さない）。
	 * 判定条件（{@code oldStatus != DONE && 現在のstatus == DONE}）をここに集約することで、
	 * 各呼び出し元で条件式を重複して書く必要をなくし、 今後DONE遷移時の処理が増えた場合の修正漏れを防ぐ。
	 * </p>
	 *
	 * @param task      ステータス変更・保存後のタスク（保存済み）
	 * @param oldStatus 変更前のステータス（新規作成時は {@code null} でもよい）
	 */
	private void triggerRecurrenceIfNewlyCompleted(Task task, TaskStatus oldStatus) {
		if (oldStatus != TaskStatus.DONE && task.getStatus() == TaskStatus.DONE) {
			generateNextRecurrenceIfNeeded(task);
		}
	}

	/**
	 * 繰り返しタスク機能
	 * <p>
	 * 完了したタスクの {@code recurrenceRule} が {@link RecurrenceRule#NONE} 以外の場合、
	 * 次回期日（{@link #calculateNextDueDate}）を持つ新しいタスクを自動生成する。 {@code title} /
	 * {@code description} / {@code priority} / {@code categoryId} /
	 * {@code recurrenceRule} は元タスクから引き継ぎ、{@code dueDate} のみ次回期日に差し替える。
	 * </p>
	 * <p>
	 * 新規タスクは既存の「新規作成時はステータスを強制的にTODOにする」方針（{@code TaskViewController}） と同様に
	 * {@code status = TO-DO} / {@code completedAt = null} を明示的に設定する。
	 * また、生成後のタスクは元タスクへの参照を持たない独立したレコードとして保存する
	 * </p>
	 *
	 * @param completedTask 今まさに {@link TaskStatus#DONE} になったタスク（保存済み）
	 */
	private void generateNextRecurrenceIfNeeded(Task completedTask) {
		// サブタスクは常にrecurrenceRule=NONEで保存される実装になっているが、万一のデータ不整合に備えて二重に防御する。
		if (completedTask.getParentTaskId() != null) {
			return;
		}

		RecurrenceRule rule = completedTask.getRecurrenceRule();
		if (rule == null || rule == RecurrenceRule.NONE) {
			return;
		}

		// 次回期日計算は「日付」のみを扱うため、completedAt（LocalDateTime）から
		// toLocalDate()で日付部分のみを取り出して起点とする（completedAt未設定時は当日を起点にする）
		LocalDate baseDate = completedTask.getCompletedAt() != null ? completedTask.getCompletedAt().toLocalDate()
				: LocalDate.now();

		Task next = new Task();
		next.setTitle(completedTask.getTitle());
		next.setDescription(completedTask.getDescription());
		next.setPriority(completedTask.getPriority());
		next.setCategoryId(completedTask.getCategoryId());
		next.setRecurrenceRule(rule);
		next.setDueDate(calculateNextDueDate(baseDate, rule));
		// 新規作成時はステータスを強制的にTODOにする既存方針（TaskViewController）を踏襲する
		next.setStatus(TaskStatus.TODO);
		next.setCompletedAt(null);
		// parentTaskId・idはあえて設定しない（この次回タスク自身はトップレベルタスクとして生成する。
		// 元タスクがサブタスクを持っていた場合は、下記でまとめて複製する）

		Task savedNext = taskRepository.save(next);

		// 元タスクに紐づく既存サブタスクを、新しい次回タスクの下にまとめて複製する
		duplicateSubtasksForRecurrence(completedTask.getId(), savedNext.getId());
	}

	/**
	 * 繰り返しタスクの次回分生成に伴い、元タスクに紐づく既存のサブタスクを新しい親タスクの下に複製する。
	 * <p>
	 * 複製されたサブタスクは、親タスクの次回分と同様に必ず {@code status = TO-DO} /
	 * {@code completedAt = null} から始まる（{@code DONE} / {@code CANCELLED}
	 * だったサブタスクも含め、 存在する全てのサブタスクが複製対象になる）。次回以降不要なサブタスクは、ユーザーが個別に削除する
	 * ことで複製対象から除外する運用とする。
	 * </p>
	 * <p>
	 * {@code recurrenceRule} はサブタスクには持たせない方針のため、複製後も常に {@link RecurrenceRule#NONE}
	 * を設定する。{@code dueDate} は元のサブタスクの値をそのまま引き継ぐ（サブタスク自体の次回期日を
	 * 自動計算する仕組みは持たないため、単純な複製に留める）。
	 * </p>
	 *
	 * @param sourceParentId 複製元（完了した元タスク）のID
	 * @param newParentId    複製先（新しく生成された次回タスク）のID
	 */
	private void duplicateSubtasksForRecurrence(Long sourceParentId, Long newParentId) {
		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
		List<Task> subtasks = taskRepository.findByParentTaskId(sourceParentId, sort);
		for (Task sub : subtasks) {
			Task newSub = new Task();
			newSub.setTitle(sub.getTitle());
			newSub.setDescription(sub.getDescription());
			newSub.setPriority(sub.getPriority());
			newSub.setCategoryId(sub.getCategoryId());
			newSub.setDueDate(sub.getDueDate());
			newSub.setSortOrder(sub.getSortOrder());
			newSub.setParentTaskId(newParentId);
			newSub.setRecurrenceRule(RecurrenceRule.NONE);
			newSub.setStatus(TaskStatus.TODO);
			newSub.setCompletedAt(null);
			taskRepository.save(newSub);
		}
	}

	/**
	 * 繰り返し設定（{@link RecurrenceRule}）に基づき、完了日を起点とした次回期日を計算する。
	 * <ul>
	 * <li>{@link RecurrenceRule#DAILY}（毎日） = 完了日の翌日</li>
	 * <li>{@link RecurrenceRule#WEEKLY}（毎週） = 完了日の7日後</li>
	 * <li>{@link RecurrenceRule#MONTHLY}（毎月） = 完了日の1か月後</li>
	 * </ul>
	 * <p>
	 * 月末調整（例: 1/31→2月は28日/29日になる）は、{@link LocalDate#plusMonths(long)} が
	 * 「加算先の月に存在しない日付の場合は自動的にその月の末日に調整する」仕様を持つため、 追加の実装なしでそのまま対応できる。
	 * </p>
	 *
	 * @param baseDate 起点となる日付（完了日）
	 * @param rule     繰り返し設定（{@link RecurrenceRule#NONE} は呼び出し側で除外済みを想定）
	 * @return 次回期日
	 */
	private LocalDate calculateNextDueDate(LocalDate baseDate, RecurrenceRule rule) {
		switch (rule) {
		case DAILY:
			return baseDate.plusDays(1);
		case WEEKLY:
			return baseDate.plusDays(7);
		case MONTHLY:
			return baseDate.plusMonths(1);
		default:
			// NONEはgenerateNextRecurrenceIfNeeded側で既に除外されているため通常到達しないが、
			// 念のため安全なフォールバックとして起点日をそのまま返す
			return baseDate;
		}
	}

	/**
	 * タスクを削除します。
	 * <p>
	 * 指定IDのタスクが存在しない場合は何もせず正常終了します。 存在する、しないが判別できてしまうと脆弱性になるので、そのままとする。
	 * </p>
	 * <p>
	 * {@link org.springframework.data.jpa.repository.JpaRepository#deleteById} は、
	 * Spring Data JPAの実装（{@code SimpleJpaRepository#deleteById}）上
	 * {@code findById(id).ifPresent(this::delete)} となっており、対象が存在しない場合は
	 * 例外を投げず何もしないため、追加のガード処理なしでそのまま上記の仕様を満たす。
	 * </p>
	 *
	 * @param id 削除対象のタスクID
	 */
	public void deleteTask(Long id) {
		// 削除対象が親タスクの場合、紐づく全サブタスクも一括削除する
		// 対象がサブタスク自身、または紐づくサブタスクが無い場合は何も削除されず正常終了する。

		// deleteByParentTaskIdは、親タスクIDを指定して紐づくサブタスクを一括削除するメソッド
		taskRepository.deleteByParentTaskId(id);

		// deleteByIdは、指定したIDのタスクを削除するメソッド
		taskRepository.deleteById(id);
	}

	/**
	 * ドラッグ＆ドロップ後のタスクの並び順を一括で更新・永続化する。
	 * <p>
	 * 渡されたタスクIDの並び順どおりに、先頭から {@code 0, 1, 2, ...} を {@code sortOrder} として設定する。
	 * 存在しないタスクIDが含まれていた場合は無視する（一覧取得後、別タブ等で削除された場合を考慮）。
	 * </p>
	 *
	 * @param orderedTaskIds ドロップ後の並び順どおりのタスクIDリスト
	 */
	public void reorderTasks(List<Long> orderedTaskIds) {
		int order = 0;
		for (Long taskId : orderedTaskIds) {
			Optional<Task> taskOpt = taskRepository.findById(taskId);
			if (taskOpt.isEmpty()) {
				// 一覧取得後に削除されていた場合等は無視して続行する
				order++;
				continue;
			}
			Task task = taskOpt.get();
			// 並び替えの対象はトップレベルタスクのみ対象＝トップレベル一覧の行と揃える。
			// サブタスクのIDが紛れ込んでいた場合は無視して続行する
			// （サブタスクの採番空間は別で扱う想定のため、ここでは触れない）。
			if (task.getParentTaskId() != null) {
				order++;
				continue;
			}
			task.setSortOrder(order);
			taskRepository.save(task);
			order++;
		}
	}
}