package com.example.todo.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;

import jakarta.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaskService の単体テストクラス。
 * <p>
 * タスクのステータス変更や保存などのビジネスロジックを検証します。
 * </p>
 */
@SpringBootTest
@Transactional
class TaskServiceTest {

	@Autowired
	private TaskService taskService;

	// DBへの反映を1次キャッシュ経由ではなく本当に確認するためのEntityManager
	@Autowired
	private EntityManager entityManager;

	@Test
	void ステータスをDONEに変更するとcompletedAtが設定される() {
		
		//------------準備--------------------------
		
		// タスクを作成して保存する
		Task task = new Task();
		
		// タスク名は必須のため仮で設定する
		task.setTitle("テストタスク");
		
		// ステータスは初期値でTODOのままにする
		Task saved = taskService.saveTask(task);

		//------------実行--------------------------
		
		// ステータスをDONEに変更する
		Task updated = taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		// 1次キャッシュに頼らず、本当にDBへ反映されたかを確認する
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		//------------比較--------------------------
		
		// ステータスがDONEに変更されていること
		assertThat(updated.getStatus()).isEqualTo(TaskStatus.DONE);
		
		// completedAtがnullではないこと
		assertThat(updated.getCompletedAt()).isNotNull();

		// DBから読み直した値でも同様にDONE・completedAt設定済みであること
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(reloaded.getCompletedAt()).isNotNull();
	}

	@Test
	void ステータスをDONEから他のステータスに変更するとcompletedAtが解除される() {

		//------------準備--------------------------

		// タスクを作成し、いったんDONEにしてcompletedAtを設定させておく
		Task task = new Task();
		task.setTitle("テストタスク2");
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		//------------実行--------------------------

		// DONEからIN_PROGRESSへ変更する
		Task updated = taskService.updateStatus(saved.getId(), TaskStatus.IN_PROGRESS);

		// 本当にDBへ反映されたかを確認する
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		//------------比較--------------------------

		// ステータスがIN_PROGRESSに変更されていること
		assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

		// completedAtがnullに解除されていること
		assertThat(updated.getCompletedAt()).isNull();

		// DBから読み直した値でも同様に解除されていること
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(reloaded.getCompletedAt()).isNull();
	}

	@Test
	void 削除操作を行うとタスクが実際にデータベースから削除される() {

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("削除対象タスク");
		Task saved = taskService.saveTask(task);

		//------------実行--------------------------

		taskService.deleteTask(saved.getId());

		// 1次キャッシュに頼らず、本当にDBから消えたかを確認する
		entityManager.flush();
		entityManager.clear();

		//------------比較--------------------------

		// 再度検索しても見つからない（DBから実際に削除されている）こと
		assertThat(taskService.getTaskById(saved.getId())).isEmpty();
	}

	@Test
	void 存在しないIDでステータス更新しようとすると例外がスローされる() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （updateStatusの存在しないIDに対する異常系分岐が未網羅だったため）

		//------------準備--------------------------

		// DBに存在し得ない十分大きなIDを使用する
		long notExistId = 999999999L;

		//------------実行 & 比較--------------------------

		// IllegalArgumentExceptionがスローされること
		assertThatThrownBy(() -> taskService.updateStatus(notExistId, TaskStatus.DONE))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 存在しないIDでタスクを取得しようとすると空のOptionalが返る() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （getTaskByIdの空Optional分岐が未網羅だったため）

		//------------準備--------------------------

		long notExistId = 999999999L;

		//------------実行--------------------------

		var result = taskService.getTaskById(notExistId);

		//------------比較--------------------------

		assertThat(result).isEmpty();
	}

	@Test
	void 新規タスク保存時にステータス_ソート順_作成更新日時のデフォルト値が設定される() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （saveTask新規作成時のデフォルト値設定処理が未網羅だったため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("デフォルト値確認用タスク");
		// status, sortOrder, createdAt, updatedAt はあえて未設定のまま保存する

		//------------実行--------------------------

		Task saved = taskService.saveTask(task);
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		//------------比較--------------------------

		// ステータスは初期値TODOであること
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.TODO);

		// ソート順は初期値0であること
		assertThat(reloaded.getSortOrder()).isEqualTo(0);

		// 作成日時・更新日時がライフサイクルコールバックにより自動設定されていること
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();

		// 完了日時は未完了のためnullであること
		assertThat(reloaded.getCompletedAt()).isNull();
	}

	@Test
	void 全タスク取得はソート順_作成日時の昇順で返る() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （getAllTasksのソート順分岐が未網羅だったため）

		//------------準備--------------------------

		Task first = new Task();
		first.setTitle("並び順確認タスクA");
		first.setSortOrder(2);
		taskService.saveTask(first);

		Task second = new Task();
		second.setTitle("並び順確認タスクB");
		second.setSortOrder(1);
		taskService.saveTask(second);

		//------------実行--------------------------

		List<Task> tasks = taskService.getAllTasks();

		//------------比較--------------------------

		// sortOrderが小さい「並び順確認タスクB」が「並び順確認タスクA」より前に来ること
		int indexA = tasks.indexOf(tasks.stream().filter(t -> t.getTitle().equals("並び順確認タスクA")).findFirst().orElseThrow());
		int indexB = tasks.indexOf(tasks.stream().filter(t -> t.getTitle().equals("並び順確認タスクB")).findFirst().orElseThrow());
		assertThat(indexB).isLessThan(indexA);
	}

	@Test
	void 絵文字や改行を含む詳細が保存され表示できること() {

		// ※作業指示書「フェーズ2」この段階で実施するテスト対応
		// 「絵文字や改行を含む詳細（description）の入力が保存・表示できること
		//  （MySQL/MariaDBの文字コードがutf8mb4になっているか確認）」
		//
		// 絵文字（😀🎉📝等）はUTF-8で4バイトとなるため、DB・接続文字コードが
		// utf8mb4（4バイト対応）になっていないと、保存時に文字化けしたり、
		// INSERT自体が失敗したりする。実際のMySQL/MariaDBへ保存・再読込することで、
		// 文字コード設定が正しいことを確認する。

		//------------準備--------------------------

		String descriptionWithEmojiAndNewline = "買い物リスト📝\n- 牛乳🥛\n- 卵🥚\n- パン🍞\n完了したら🎉で祝う！";

		Task task = new Task();
		task.setTitle("絵文字_改行確認タスク");
		task.setDescription(descriptionWithEmojiAndNewline);

		//------------実行--------------------------

		Task saved = taskService.saveTask(task);

		// 1次キャッシュに頼らず、本当にDBへ正しく保存・読み込みできているかを確認する
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		//------------比較--------------------------

		// 絵文字・改行を含む文字列が、文字化けや欠損なくそのまま保存・取得できていること
		assertThat(reloaded.getDescription()).isEqualTo(descriptionWithEmojiAndNewline);
	}

	@Test
	void reorderTasksで渡した順序どおりにsortOrderが振り直される() {

		// ※指示書「並び替え仕様」に基づき追加
		// （ドラッグ&ドロップ後の並び順永続化ロジックを検証する）

		//------------準備--------------------------

		Task a = new Task();
		a.setTitle("並び替えAPI確認タスクA");
		Task savedA = taskService.saveTask(a);

		Task b = new Task();
		b.setTitle("並び替えAPI確認タスクB");
		Task savedB = taskService.saveTask(b);

		Task c = new Task();
		c.setTitle("並び替えAPI確認タスクC");
		Task savedC = taskService.saveTask(c);

		//------------実行--------------------------

		// C→A→Bの順に並び替える
		taskService.reorderTasks(List.of(savedC.getId(), savedA.getId(), savedB.getId()));

		entityManager.flush();
		entityManager.clear();

		//------------比較--------------------------

		Task reloadedA = taskService.getTaskById(savedA.getId()).orElseThrow();
		Task reloadedB = taskService.getTaskById(savedB.getId()).orElseThrow();
		Task reloadedC = taskService.getTaskById(savedC.getId()).orElseThrow();

		assertThat(reloadedC.getSortOrder()).isEqualTo(0);
		assertThat(reloadedA.getSortOrder()).isEqualTo(1);
		assertThat(reloadedB.getSortOrder()).isEqualTo(2);
	}

	@Test
	void reorderTasksに存在しないIDが含まれていても無視して残りのタスクの並び順が正しく振り直される() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （reorderTasksの「存在しないタスクIDは無視して続行する」分岐
		// （taskOpt.isEmpty()がtrueの場合）が未網羅だったため）

		//------------準備--------------------------

		Task a = new Task();
		a.setTitle("並び替え存在しないID確認タスクA");
		Task savedA = taskService.saveTask(a);

		Task b = new Task();
		b.setTitle("並び替え存在しないID確認タスクB");
		Task savedB = taskService.saveTask(b);

		// 一覧取得後に別タブ等で削除された状況を再現する
		long deletedId = 999999999L;

		//------------実行--------------------------

		// 削除済み(存在しない)ID→B→AのIDリストで並び替える
		taskService.reorderTasks(List.of(deletedId, savedB.getId(), savedA.getId()));

		entityManager.flush();
		entityManager.clear();

		//------------比較--------------------------

		Task reloadedA = taskService.getTaskById(savedA.getId()).orElseThrow();
		Task reloadedB = taskService.getTaskById(savedB.getId()).orElseThrow();

		// 存在しないIDの分もorderがインクリメントされた上で、
		// 続くB・Aのタスクにはスキップせず正しい順番でsortOrderが設定されること
		assertThat(reloadedB.getSortOrder()).isEqualTo(1);
		assertThat(reloadedA.getSortOrder()).isEqualTo(2);
	}

	@Test
	void 検索条件を指定するとAND結合で絞り込んだ結果が返る() {

		// ※指示書「フィルタリング・検索仕様」に基づき追加

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("サービス層検索確認タスク");
		task.setPriority(com.example.todo.model.Priority.HIGH);
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.IN_PROGRESS);

		Task other = new Task();
		other.setTitle("サービス層検索対象外タスク");
		taskService.saveTask(other);

		//------------実行--------------------------

		List<Task> result = taskService.searchTasks(TaskStatus.IN_PROGRESS, null,
				com.example.todo.model.Priority.HIGH, "サービス層検索", null, false);

		//------------比較--------------------------

		assertThat(result).extracting(Task::getTitle).containsExactly("サービス層検索確認タスク");
	}
}