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
	 * [親タスク] 
	 * 全タスクをソート順・作成日時の昇順で取得します（読み取り専用トランザクション）。
	 *
	 * @return タスクのリスト
	 */
	@Transactional(readOnly = true)
	public List<Task> getAllTasks() {
		// ソート条件を作成（sortOrderの昇順、次にcreatedAtの昇順）
		// Sort.Order.asc("sortOrder") は、sortOrderフィールドを昇順でソートすることを指定
		// Sort.Order.asc("createdAt") は、createdAtフィールドを昇順でソートすることを指定
		// Sort.by(...) は、複数のソート条件をまとめて指定するためのメソッド
		// つまり、まずsortOrderで昇順に並べ、その後同じsortOrderの中ではcreatedAtで昇順に並べるという意味
		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));

		// トップレベルタスク（parentTaskIdがnull＝サブタスクではない）のみを対象にする
		// Specification.where(TaskSpecifications.isTopLevel())
		// は、トップレベルタスクの条件を表すSpecificationを作成
		// Specificationは、JPAのクエリ条件をプログラム的に組み立てるための仕組みで、ここでは「parentTaskIdがnullである」という条件を表す
		// これにより、サブタスクは除外され、トップレベルタスクのみが取得される
		// つまり、トップレベルタスクを取得し、ソート条件に従って並べ替えた結果を返す
		// さらに、attachSubtaskProgress(tasks) を呼び出して、各トップレベルタスクに紐づくサブタスクの進捗情報を集計して設定する
		// これにより、トップレベルタスクのリストには、各タスクのサブタスク進捗（subtaskDoneCount / subtaskTotalCount）が含まれる
		Specification<Task> spec = Specification.where(TaskSpecifications.isTopLevel());
		List<Task> tasks = taskRepository.findAll(spec, sort);
		attachSubtaskProgress(tasks);
		return tasks;
	}

	/**
	 * [共通] 
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
	 * [共通] 
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
		// キーワード検索は、トップレベルタスク自身だけでなく、紐づくサブタスクのタイトル・詳細も
		// 対象にする（5-1）。これにより、キーワードがサブタスクのみにマッチした場合でも、
		// 親タスクが検索結果から消えてしまわないようにする。
		Specification<Task> spec = Specification.where(TaskSpecifications.hasStatus(status))
				.and(TaskSpecifications.hasPriority(priority))
				.and(TaskSpecifications.hasCategory(categoryId, noCategory))
				.and(TaskSpecifications.keywordContainsIncludingSubtasks(keyword))
				.and(TaskSpecifications.dueFilter(due, LocalDate.now())).and(TaskSpecifications.isTopLevel());

		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
		List<Task> tasks = taskRepository.findAll(spec, sort);
		attachSubtaskProgress(tasks);
		return tasks;
	}

	/**
	 * [親タスク] 
	 * トップレベルタスクのリストに、各タスクのサブタスク進捗（{@code subtaskDoneCount} /
	 * {@code subtaskTotalCount}）を集計して設定する。
	 * <p>
	 * タスク1件ごとに問い合わせる（N+1）のを避けるため、対象タスクIDをまとめて1回のクエリで取得する。
	 * </p>
	 *
	 * @param topLevelTasks 進捗を設定する対象のトップレベルタスクのリスト
	 */
	private void attachSubtaskProgress(List<Task> topLevelTasks) {
		// 対象が空なら処理不要
		if (topLevelTasks.isEmpty()) {
			return;
		}
		// トップレベルタスクのIDを取得し、List<Long>に変換する
		List<Long> ids = topLevelTasks.stream().map(Task::getId).collect(Collectors.toList());
		// まとめて1回のクエリで、指定IDの親タスクに紐づく全サブタスクを取得する
		List<Task> allSubtasks = taskRepository.findByParentTaskIdIn(ids);
		// サブタスクを親タスクIDごとにグループ化する
		// これにより、subtasksByParentはMap<Long,
		// List<Task>>型となり、キーが親タスクID、値がその親タスクに紐づくサブタスクのリストとなる
		Map<Long, List<Task>> subtasksByParent = allSubtasks.stream()
				.collect(Collectors.groupingBy(Task::getParentTaskId));

		// 各トップレベルタスクに対して、紐づくサブタスクの進捗を集計して設定する
		// 進捗 = DONE件数 ÷ （CANCELLEDを除いた全サブタスク件数）
		// サブタスクが0件、またはすべてCANCELLEDの場合はtotalが0になる
		// （0除算は呼び出し側＝テンプレート/JSで「total > 0」を見て回避する）
		// subtasksByParent.getOrDefault(task.getId(), List.of())
		// は、親タスクIDに紐づくサブタスクのリストを取得する
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
	 * [サブタスク] 
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
		// 親タスクが存在しない場合は例外
		if (!taskRepository.existsById(parentId)) {
			throw new IllegalArgumentException("Task not found: " + parentId);
		}
		Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
		return taskRepository.findByParentTaskId(parentId, sort);
	}

	/**
	 * [共通] 
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
		// 新規作成時は更新前状態が存在しないため null で初期化する
		TaskStatus previousStatus = null;
		Long previousParentTaskId = null;

		// [共通] 
		// 更新時のみ、対象がまだ存在するか確認する
		if (task.getId() != null) {
			Task existing = taskRepository.findById(task.getId())
					.orElseThrow(() -> new IllegalArgumentException("Task not found: " + task.getId()));

			previousStatus = existing.getStatus();
			previousParentTaskId = existing.getParentTaskId();

			TaskStatus newStatus = task.getStatus() != null ? task.getStatus() : existing.getStatus();
			task.setCompletedAt(resolveCompletedAt(existing.getStatus(), newStatus, existing.getCompletedAt()));
		}

		// [サブタスク] 
		// サブタスクは1階層まで（親の親を持つタスクは親にできない）
		if (task.getParentTaskId() != null) {
			Task parent = taskRepository.findById(task.getParentTaskId())
					.orElseThrow(() -> new IllegalArgumentException("親タスクが見つかりません: " + task.getParentTaskId()));
			// 親候補がサブタスクなら1階層制限違反
			if (parent.getParentTaskId() != null) {
				throw new IllegalArgumentException("サブタスクの下にさらにサブタスクを作成することはできません。");
			}
			// 自身が子を持つ場合は孫階層化を防ぐ
			if (task.getId() != null && !taskRepository.findByParentTaskId(task.getId(), Sort.unsorted()).isEmpty()) {
				throw new IllegalArgumentException("既にサブタスクを持つタスクを別のタスクのサブタスクにすることはできません。");
			}
			
			// サブタスクの親タスクが変更された場合、以前の親タスクの進捗を更新する必要がある
			// そのため、以前の親タスクIDを保持しておく
			boolean isNewSubtaskAssignment = task.getId() == null
					|| !task.getParentTaskId().equals(previousParentTaskId);
			// 完了/中止の親へ新規追加・付け替えは不可
			if (isNewSubtaskAssignment
					&& (parent.getStatus() == TaskStatus.DONE || parent.getStatus() == TaskStatus.CANCELLED)) {
				throw new IllegalArgumentException("完了済み、または中止済みの親タスクにはサブタスクを追加できません。");
			}

			// 新規追加/付け替え時のみ sortOrder を再採番
			if (isNewSubtaskAssignment) {
				task.setSortOrder(nextSubtaskSortOrder(task.getParentTaskId()));
			}
			
			// サブタスクは繰り返し設定を持たない
			task.setRecurrenceRule(RecurrenceRule.NONE);
		}

		// タスクを保存（新規作成または更新）し、保存後のタスクを返す
		Task saved = taskRepository.save(task);

		// フォーム経由でステータスをDONEに変更した場合も、繰り返し設定があれば次回分を自動生成する
		triggerRecurrenceIfNewlyCompleted(saved, previousStatus);

		// 編集フォーム（POST /tasks）経由でステータスを変更した場合も、一覧画面のドロップダウン
		// （PATCH /api/tasks/{id}/status 経由の updateStatus）と同じ親子カスケードを適用する
		// （以前はここが抜けており、編集画面経由の更新だけ連動がスキップされる不具合があった）。
		applyParentChildCascade(saved, previousStatus, saved.getStatus());

		return saved;
	}

	/**
	 * [サブタスク] 
	 * 新規にサブタスクを追加・付け替えする際に設定する {@code sortOrder} を計算する（6-2）。
	 * <p>
	 * 指定した親タスクに既に紐づく兄弟サブタスクの中で最大の {@code sortOrder} + 1（＝一番下）を返す。 サブタスクが1件も存在しない場合は
	 * {@code 0} を返す。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 * @return 新規サブタスクに設定すべき {@code sortOrder}
	 */
	private int nextSubtaskSortOrder(Long parentId) {
		List<Task> siblings = taskRepository.findByParentTaskId(parentId, Sort.unsorted());
		return siblings.stream().mapToInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0).max().orElse(-1) + 1;
	}

	/**
	 * [親タスク] 
	 * 新規作成フォーム・編集フォーム共通で使う「親タスク候補のベース一覧」を取得する。
	 * <p>
	 * 候補はトップレベルタスク（{@code parentTaskId} が null）のうち、 {@code excludeTaskId}
	 * が指定されている場合は当該タスク自身（自己参照防止）を除外したもの。
	 * </p>
	 * <p>
	 * 注意: 既にサブタスクを持つタスク（他タスクの親）もここでは除外しない。 1つの親タスクに複数のサブタスクを紐づけることは許容仕様であり、ここで除外すると
	 * 「1個目のサブタスク追加後に2個目以降の親として選べない」不具合になるため。 また、DONE/CANCELLEDの除外は本メソッドでは行わず、
	 * {@link #filterAvailableParentCandidates(List, Long)} 側で一元的に適用する。
	 * </p>
	 *
	 * @param excludeTaskId 候補から除外するタスクID（自己参照防止用）。新規作成時は null
	 * @return 親タスク候補のベース一覧
	 */
	@Transactional(readOnly = true)
	public List<Task> getParentTaskCandidates(Long excludeTaskId) {
		return getAllTasks().stream().filter(t -> excludeTaskId == null || !t.getId().equals(excludeTaskId))
				.collect(Collectors.toList());
	}

	/**
	 * [親タスク] 
	 * 新規作成フォームの「親タスク」選択欄に表示する候補一覧を取得する。
	 * <p>
	 * {@link #getParentTaskCandidates(Long)} の条件に加えて、完了済み（DONE）・中止済み（CANCELLED）の
	 * タスクを候補から除外する。これにより、保存時にサーバー側バリデーションで弾かれる親タスクを 事前に選択肢から隠し、UI上の操作ミスを防ぐ。
	 * </p>
	 *
	 * @param excludeTaskId            除外する（編集中の）タスクID。新規作成時は null
	 * @param keepSelectedParentTaskId 事前選択済みとして候補に残す親タスクID。不要な場合は null
	 * @return 新規作成フォーム用の親タスク候補一覧
	 */
	@Transactional(readOnly = true)
	public List<Task> getAvailableParentTaskCandidatesForCreate(Long excludeTaskId, Long keepSelectedParentTaskId) {
		return filterAvailableParentCandidates(getParentTaskCandidates(excludeTaskId), keepSelectedParentTaskId);
	}

	/**
	 * [親タスク] 
	 * 編集フォームの「親タスク」選択欄に表示する候補一覧を取得する。
	 * <p>
	 * DONE/CANCELLEDは原則除外するが、現在すでに選択されている親タスクは そのまま保持できるよう候補に残す。
	 * </p>
	 *
	 * @param excludeTaskId       編集対象タスクID（自己参照防止の除外対象）
	 * @param currentParentTaskId 現在選択されている親タスクID（保持対象）
	 * @return 編集フォーム用の親タスク候補一覧
	 */
	@Transactional(readOnly = true)
	public List<Task> getAvailableParentTaskCandidatesForEdit(Long excludeTaskId, Long currentParentTaskId) {
		return filterAvailableParentCandidates(getParentTaskCandidates(excludeTaskId), currentParentTaskId);
	}

	/**
	 * [共通] 新規作成フォーム・編集フォーム共通の親タスク候補フィルタリング処理。
	 * <p>
	 * DONE/CANCELLEDは原則除外するが、現在すでに選択されている親タスクは そのまま保持できるよう候補に残す。
	 * </p>
	 *
	 * @param candidates               親タスク候補一覧
	 * @param keepSelectedParentTaskId 事前選択済みとして候補に残す親タスクID。不要な場合は null
	 * @return フィルタリング後の親タスク候補一覧
	 */
	private List<Task> filterAvailableParentCandidates(List<Task> baseCandidates, Long keepSelectedParentTaskId) {
		// 親タスク候補を状態でフィルタリングする
		return baseCandidates.stream().filter(t -> {
			// [共通] 編集時に既に選択済みの親は、状態に関わらず候補として残す
			if (keepSelectedParentTaskId != null && keepSelectedParentTaskId.equals(t.getId())) {
				return true;
			}
			// [共通] 上記以外は DONE/CANCELLED を除外する
			TaskStatus status = t.getStatus();
			return status == null || (status != TaskStatus.DONE && status != TaskStatus.CANCELLED);
		}).collect(Collectors.toList());
	}

	/**
	 * [共通] 
	 * タスクの状態を更新します。状態が DONE の場合は完了日時を設定し、それ以外では完了日時をクリアします。
	 * <p>
	 * サブタスク機能に伴い、親子間のステータスは以下の通り連動します
	 * </p>
	 * <ul>
	 * <li>親→子: トップレベルタスクをDONEにすると、紐づく全サブタスクもDONEにする</li>
	 * <li>親→子: トップレベルタスクをCANCELLEDにすると、紐づく未完了（DONE以外）のサブタスクのみを
	 * 一括でCANCELLEDにする（既にDONEのサブタスクは維持する）</li>
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

		// ステータスを更新し、完了日時を解決する
		// resolveCompletedAtメソッドは、ステータスの変化前後を比較して完了日時を決定する
		TaskStatus oldStatus = task.getStatus();
		task.setStatus(status);
		task.setCompletedAt(resolveCompletedAt(oldStatus, status, task.getCompletedAt()));
		Task saved = taskRepository.save(task);

		// 繰り返し設定（recurrenceRule）があるタスクがDONE以外のステータスだったタスクをDONEにした場合、
		// 次回期日を持つ新しいタスクを自動生成する。
		// 既にDONEだったタスクを再度DONEで保存しただけの場合は生成しない。
		triggerRecurrenceIfNewlyCompleted(saved, oldStatus);

		// 親子間のステータス連動（親→子のDONE一括化、子→親のDONE自動化・差し戻し等）を適用する。
		// saveTask（編集フォーム経由の更新）からも同じ処理を呼び出すことで、更新経路によって
		// 連動の有無が変わらないようにしている（詳細は applyParentChildCascade のJavadoc参照）。
		applyParentChildCascade(saved, oldStatus, status);

		// タスクを保存して更新後の状態を返す
		return saved;
	}

	/**
	 * [共通] 
	 * ステータス変更後の親子間カスケード処理をまとめて適用する。
	 * <p>
	 * {@link #updateStatus}（一覧画面のドロップダウン、{@code PATCH /api/tasks/{id}/status}）と
	 * {@link #saveTask}（編集フォーム、{@code POST /tasks}）の両方の更新経路から共通で呼び出すことで、
	 * どちらの経路でステータスを変更しても同じ連動結果になるようにする （以前は {@code saveTask}
	 * 側でこの処理が呼ばれておらず、編集フォーム経由の更新だけ 親子カスケードがスキップされる不具合があった）。
	 * </p>
	 * <ul>
	 * <li>対象がトップレベルタスクの場合: 新しいステータスが {@code DONE} なら {@link #cascadeDoneToSubtasks}
	 * で全サブタスクをDONEにする</li>
	 * <li>対象がサブタスクの場合: 新しいステータスが {@code DONE} なら
	 * {@link #cascadeDoneToParentIfAllSubtasksDone}、それ以外なら
	 * {@link #revertParentFromDoneIfNeeded} を呼び出す</li>
	 * </ul>
	 * <p>
	 * ステータスに変化がない場合（{@code oldStatus == newStatus}）も、既存の {@code updateStatus}
	 * の挙動を踏襲し、無条件に再実行する（カスケード先の各メソッドは 冪等になるよう作られているため、変化がない場合に呼び出しても害はない）。
	 * </p>
	 *
	 * @param saved     保存後のタスク
	 * @param oldStatus 変更前のステータス（新規作成時は {@code null} でもよい）
	 * @param newStatus 変更後のステータス
	 */
	private void applyParentChildCascade(Task saved, TaskStatus oldStatus, TaskStatus newStatus) {
		// [親タスク]
		// 親 -> 子の連動
		if (saved.getParentTaskId() == null) {
			// 親をDONEにした場合は子へDONEを連動
			if (newStatus == TaskStatus.DONE) {
				cascadeDoneToSubtasks(saved.getId());
			} else if (newStatus == TaskStatus.CANCELLED) {
				// 親をCANCELLEDにした場合は子へCANCELLEDを連動
				cascadeCancelledToSubtasks(saved.getId());
			}

			// [サブタスク]
			// 子 -> 親の連動
		} else {
			// 子がDONEなら親を自動DONE判定
			if (newStatus == TaskStatus.DONE) {
				cascadeDoneToParentIfAllSubtasksDone(saved.getParentTaskId());
			} else {
				// 子がDONE以外なら必要に応じて親を差し戻す
				revertParentFromDoneIfNeeded(saved.getParentTaskId());
			}
		}
	}

	/**
	 * [サブタスク] 
	 * サブタスクがDONEになった結果、兄弟サブタスク（{@code CANCELLED} を除く）が全てDONEになった場合、
	 * 親タスクも自動的に {@link TaskStatus#DONE} にする。
	 * <p>
	 * 親タスクが既にDONEの場合は何もしない。
	 * サブタスクが存在しない、またはサブタスクが全て{@code CANCELLED}の場合は対象外（親自身のステータスのみで判断する）。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void cascadeDoneToParentIfAllSubtasksDone(Long parentId) {
		Task parent = taskRepository.findById(parentId).orElse(null);
		// 親が無い、または既にDONEなら処理不要
		if (parent == null || parent.getStatus() == TaskStatus.DONE) {
			return;
		}
		List<Task> siblings = taskRepository.findByParentTaskId(parentId, Sort.unsorted());
		List<Task> effectiveSiblings = siblings.stream().filter(s -> s.getStatus() != TaskStatus.CANCELLED)
				.collect(Collectors.toList());
		// 有効な子が無い場合は連動しない
		if (effectiveSiblings.isEmpty()) {
			// 全てCANCELLED、またはサブタスクが存在しない場合は親自身の判断に委ねる（連動しない）
			return;
		}
		boolean allDone = effectiveSiblings.stream().allMatch(s -> s.getStatus() == TaskStatus.DONE);
		// 未完了の子があれば親はDONEにしない
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
	 * [サブタスク] 
	 * 親タスクがDONEの状態で、紐づくサブタスクがDONE以外に変更された場合、 親タスクを
	 * {@link TaskStatus#IN_PROGRESS} に戻す。
	 * <p>
	 * 親タスクが既にDONE以外の場合は何もしない。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void revertParentFromDoneIfNeeded(Long parentId) {
		Task parent = taskRepository.findById(parentId).orElse(null);
		// 親が無い、またはDONEでないなら処理不要
		if (parent == null || parent.getStatus() != TaskStatus.DONE) {
			return;
		}
		parent.setStatus(TaskStatus.IN_PROGRESS);
		parent.setCompletedAt(resolveCompletedAt(TaskStatus.DONE, TaskStatus.IN_PROGRESS, parent.getCompletedAt()));
		taskRepository.save(parent);
	}

	/**
	 * [親タスク] 
	 * 指定した親タスクに紐づくサブタスクのうち、未中止のもののステータスを {@link TaskStatus#DONE} にする。
	 * <p>
	 * 既に {@code DONE} のサブタスク、および {@code CANCELLED} のサブタスクは状態を維持する。
	 * （中止済みの作業を親の完了操作で自動復活させないため）
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void cascadeDoneToSubtasks(Long parentId) {
		List<Task> subtasks = taskRepository.findByParentTaskId(parentId, Sort.unsorted());
		for (Task sub : subtasks) {
			TaskStatus subOldStatus = sub.getStatus();
			// 既にDONE/CANCELLEDの子は維持する
			if (subOldStatus == TaskStatus.DONE || subOldStatus == TaskStatus.CANCELLED) {
				continue;
			}
			sub.setStatus(TaskStatus.DONE);
			sub.setCompletedAt(resolveCompletedAt(subOldStatus, TaskStatus.DONE, sub.getCompletedAt()));
			taskRepository.save(sub);
		}
	}

	/**
	 * [親タスク] 
	 * 指定した親タスクに紐づく未完了（{@code DONE} 以外）のサブタスクのステータスを
	 * {@link TaskStatus#CANCELLED} にする。
	 * <p>
	 * 既に {@code DONE} のサブタスクは状態を維持する（中止にしない）。 {@code CANCELLED}
	 * に変更する際は完了日時は設定しない（{@link #resolveCompletedAt} により {@code null} に解決される）。
	 * </p>
	 *
	 * @param parentId 親タスクのID
	 */
	private void cascadeCancelledToSubtasks(Long parentId) {
		List<Task> subtasks = taskRepository.findByParentTaskId(parentId, Sort.unsorted());
		for (Task sub : subtasks) {
			TaskStatus subOldStatus = sub.getStatus();
			// DONEの子は維持する
			if (subOldStatus == TaskStatus.DONE) {
				continue;
			}
			// 既にCANCELLEDの子は維持する
			if (subOldStatus == TaskStatus.CANCELLED) {
				continue;
			}
			sub.setStatus(TaskStatus.CANCELLED);
			sub.setCompletedAt(resolveCompletedAt(subOldStatus, TaskStatus.CANCELLED, sub.getCompletedAt()));
			taskRepository.save(sub);
		}
	}

	/**
	 * [共通] 
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
		// [共通]
		// DONE以外は completedAt をクリア
		if (newStatus != TaskStatus.DONE) {
			return null;
		}
		return oldStatus == TaskStatus.DONE ? existingCompletedAt : LocalDateTime.now();
	}

	/**
	 * [共通] 
	 * 「今まさにDONEへ遷移したか」を判定し、該当する場合のみ繰り返しタスクの次回分生成を行う。
	 * <p>
	 * {@link #updateStatus}・{@link #saveTask}・{@link #cascadeDoneToParentIfAllSubtasksDone}
	 * の3つの「DONEに遷移しうる経路」すべてから共通で呼び出すための窓口 （{@link #cascadeDoneToSubtasks}
	 * はサブタスク自身が対象であり、サブタスクは常に {@code recurrenceRule = NONE}
	 * 固定のため、そもそも繰り返し生成の対象外であり呼び出さない）。
	 * 判定条件（{@code oldStatus != DONE && 現在のstatus == DONE}）をここに集約することで、
	 * 各呼び出し元で条件式を重複して書く必要をなくし、 今後DONE遷移時の処理が増えた場合の修正漏れを防ぐ。
	 * </p>
	 *
	 * @param task      ステータス変更・保存後のタスク（保存済み）
	 * @param oldStatus 変更前のステータス（新規作成時は {@code null} でもよい）
	 */
	private void triggerRecurrenceIfNewlyCompleted(Task task, TaskStatus oldStatus) {
		// [共通]
		// 変更前がDONE以外で、変更後がDONEなら次回分を生成
		if (oldStatus != TaskStatus.DONE && task.getStatus() == TaskStatus.DONE) {
			generateNextRecurrenceIfNeeded(task);
		}
	}

	/**
	 * [親タスク] 
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
		// サブタスクは繰り返し生成対象外
		if (completedTask.getParentTaskId() != null) {
			return;
		}

		RecurrenceRule rule = completedTask.getRecurrenceRule();
		// 繰り返し設定なし（NONE/null）なら生成しない
		if (rule == null || rule == RecurrenceRule.NONE) {
			return;
		}

		// 次回期日の計算基準を「完了日（completedAt）」から「元の期日（dueDate）」へ変更する
		// （万一 dueDate が未設定の場合は、安全なフォールバックとして当日または完了日を使用）
		LocalDate baseDate = completedTask.getDueDate();
		// 期日未設定時は完了日または当日を基準日にフォールバック
		if (baseDate == null) {
			baseDate = completedTask.getCompletedAt() != null ? completedTask.getCompletedAt().toLocalDate()
					: LocalDate.now();
		}

		Task next = new Task();
		next.setTitle(completedTask.getTitle());
		next.setDescription(completedTask.getDescription());
		next.setPriority(completedTask.getPriority());
		next.setCategoryId(completedTask.getCategoryId());
		next.setRecurrenceRule(rule);

		// 次回タスクへ「毎月末フラグ」を引き継ぐ
		next.setIsLastDayOfMonth(completedTask.getIsLastDayOfMonth());

		// 次回期日を計算して設定
		next.setDueDate(calculateNextDueDate(baseDate, rule, completedTask.getIsLastDayOfMonth()));

		// 新規作成時はステータスを強制的にTODOにする既存方針（TaskViewController）を踏襲する
		next.setStatus(TaskStatus.TODO);
		next.setCompletedAt(null);

		Task savedNext = taskRepository.save(next);

		// 元タスクに紐づく既存サブタスクを、新しい次回タスクの下にまとめて複製する
		duplicateSubtasksForRecurrence(completedTask.getId(), savedNext.getId());
	}

	/**
	 * [サブタスク] 
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
	 * [共通] 
	 * 繰り返し設定（{@link RecurrenceRule}）および毎月末フラグ（{@code isLastDayOfMonth}）に基づき、
	 * 元の期日（{@code dueDate}）を起点とした次回期日を計算する。
	 * 
	 * <ul>
	 * <li>{@link RecurrenceRule#DAILY}（毎日） = 元の期日の翌日</li>
	 * <li>{@link RecurrenceRule#WEEKLY}（毎週） = 元の期日の7日後</li>
	 * <li>{@link RecurrenceRule#MONTHLY}（毎月） + フラグOFF =
	 * 元の期日の1か月後（2月等の存在しない日は自動補正・翌月復元）</li>
	 * <li>{@link RecurrenceRule#MONTHLY}（毎月） + フラグON = 元の期日の1か月後の当月末（その月の最終日）</li>
	 * </ul>
	 * 
	 * @param baseDate         起点となる日付（元の期日 dueDate）
	 * @param rule             繰り返し設定
	 * @param isLastDayOfMonth 毎月末フラグ（true: 当月末固定, false/null: 日付指定）
	 * @return 計算された次回期日
	 */
	private LocalDate calculateNextDueDate(LocalDate baseDate, RecurrenceRule rule, Boolean isLastDayOfMonth) {
		// 起点日が無い場合は計算できない
		if (baseDate == null) {
			return null;
		}

		switch (rule) {
		case DAILY:
			return baseDate.plusDays(1);
		case WEEKLY:
			return baseDate.plusDays(7);
		case MONTHLY:
			LocalDate nextMonth = baseDate.plusMonths(1);

			// 当月末フラグが ON の場合は、次月の最終日（当月末）を設定する
			if (Boolean.TRUE.equals(isLastDayOfMonth)) {
				return nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
			}

			// フラグ OFF: 日付指定モード（2月などの存在しない月は plusMonths により自動で補正・復元される）
			return nextMonth;
		default:
			// NONEはgenerateNextRecurrenceIfNeeded側で既に除外されているため通常到達しないが、
			// 念のため安全なフォールバックとして起点日をそのまま返す
			return baseDate;
		}
	}

	/**
	 * [共通] 
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
	 * [親タスク] 
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
			// 取得後に削除されていたIDは無視
			if (taskOpt.isEmpty()) {
				order++;
				continue;
			}
			Task task = taskOpt.get();
			// サブタスクIDが紛れ込んでいた場合は無視
			if (task.getParentTaskId() != null) {
				order++;
				continue;
			}
			task.setSortOrder(order);
			taskRepository.save(task);
			order++;
		}
	}

	/**
	 * [サブタスク] 
	 * 同一親タスク内でのドラッグ＆ドロップ後のサブタスクの並び順を一括で更新・永続化する。
	 * <p>
	 * トップレベルタスクの並び替え（{@link #reorderTasks}）とは採番空間を分離するため、専用のメソッドとして 切り出している。渡された
	 * {@code orderedSubtaskIds} の並び順どおりに、先頭から {@code 0, 1, 2, ...} を
	 * {@code sortOrder} として設定する。
	 * </p>
	 * <p>
	 * 渡されたIDのうち、指定した {@code parentId} に属さないもの（異なる親のサブタスク、
	 * またはトップレベルタスク自身のID）が1件でも含まれていた場合は、不正なリクエストとみなし 何も保存せずに
	 * {@link IllegalArgumentException} をスローする（部分的な保存は行わない）。
	 * </p>
	 *
	 * @param parentId          対象の親タスクのID
	 * @param orderedSubtaskIds ドロップ後の並び順どおりのサブタスクIDリスト（{@code parentId} の
	 *                          サブタスクのみで構成されている必要がある）
	 * @throws IllegalArgumentException 指定IDの親タスクが存在しない場合、または
	 *                                  {@code orderedSubtaskIds} に {@code parentId}
	 *                                  に属さない サブタスクIDが含まれている場合
	 */
	public void reorderSubtasks(Long parentId, List<Long> orderedSubtaskIds) {
		// 親タスクが存在しない場合は例外
		if (!taskRepository.existsById(parentId)) {
			throw new IllegalArgumentException("Task not found: " + parentId);
		}

		// 存在しないタスクID（一覧取得後に別タブ等で削除された場合）は無視して続行するが、
		// 「parentIdに属さないサブタスクID」が混在していた場合は、不正なリクエストとして
		// 何も保存せずに例外を投げる（トップレベルの並び替え時と異なり、こちらは検証を厳格にする）。
		List<Task> targetTasks = orderedSubtaskIds.stream().map(taskRepository::findById).filter(Optional::isPresent)
				.map(Optional::get).collect(Collectors.toList());
		for (Task task : targetTasks) {
			// 指定親に属さないIDが混在していたら例外
			if (!parentId.equals(task.getParentTaskId())) {
				throw new IllegalArgumentException("指定された親タスクに属さないサブタスクIDが含まれています: " + task.getId());
			}
		}

		int order = 0;
		for (Task task : targetTasks) {
			task.setSortOrder(order);
			taskRepository.save(task);
			order++;
		}
	}
}