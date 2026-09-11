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
}