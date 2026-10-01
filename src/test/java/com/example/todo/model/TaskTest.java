package com.example.todo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task エンティティの単純なgetter/setterの挙動を検証するテストクラス。
 * <p>
 * {@code createdAt} / {@code updatedAt} はライフサイクルコールバック（{@code onCreate}/{@code onUpdate}）
 * 内でフィールドへ直接代入されるため、アプリケーションの通常フローでは
 * {@code setCreatedAt} / {@code setUpdatedAt} は呼び出されない。
 * ここでは、それらのsetterが単体で正しく動作することを確認する。
 * </p>
 * <p>
 * ※このクラス自体、指示書に記載のない観点：カバレッジ向上のため追加。
 * （setCreatedAt/setUpdatedAt等、アプリ内から未使用のgetter/setterを網羅する目的）
 * </p>
 */
class TaskTest {

	@Test
	void 各getter_setterに設定した値がそのまま取得できる() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加

		//------------準備--------------------------

		Task task = new Task();
		LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 9, 0);
		LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 10, 30);
		LocalDateTime completedAt = LocalDateTime.of(2026, 1, 3, 15, 45);
		LocalDate dueDate = LocalDate.of(2026, 1, 10);

		//------------実行--------------------------

		task.setId(1L);
		task.setTitle("テストタスク");
		task.setDescription("詳細メモ");
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.HIGH);
		task.setSortOrder(5);
		task.setDueDate(dueDate);
		task.setCreatedAt(createdAt);
		task.setUpdatedAt(updatedAt);
		task.setCompletedAt(completedAt);

		//------------比較--------------------------

		assertThat(task.getId()).isEqualTo(1L);
		assertThat(task.getTitle()).isEqualTo("テストタスク");
		assertThat(task.getDescription()).isEqualTo("詳細メモ");
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
		assertThat(task.getSortOrder()).isEqualTo(5);
		assertThat(task.getDueDate()).isEqualTo(dueDate);
		assertThat(task.getCreatedAt()).isEqualTo(createdAt);
		assertThat(task.getUpdatedAt()).isEqualTo(updatedAt);
		assertThat(task.getCompletedAt()).isEqualTo(completedAt);
	}

	@Test
	void デフォルトコンストラクタ生成直後はステータスがTODO_ソート順が0である() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加

		//------------準備 & 実行--------------------------

		Task task = new Task();

		//------------比較--------------------------

		// フィールド初期値（@PrePersist前のインスタンス生成直後の状態）を確認する
		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
		assertThat(task.getSortOrder()).isEqualTo(0);
		assertThat(task.getId()).isNull();
		assertThat(task.getCreatedAt()).isNull();
		assertThat(task.getUpdatedAt()).isNull();
		assertThat(task.getCompletedAt()).isNull();
		assertThat(task.getDueDate()).isNull();
	}

	@Test
	void onCreateでsortOrderとstatusとpriorityがnullの場合はデフォルト値が補完される() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （onCreate内のnullチェック分岐 if(sortOrder==null) / if(status==null) / if(priority==null) は、
		// フィールド初期化子により通常フローでは常にfalseとなり真の分岐が未検証だったため、
		// setSortOrder(null)/setStatus(null)/setPriority(null)により意図的にnullの状態を作って検証する）

		//------------準備--------------------------

		Task task = new Task();
		task.setSortOrder(null);
		task.setStatus(null);
		task.setPriority(null);

		//------------実行--------------------------

		// onCreateは@PrePersistのprotectedメソッドだが、同一パッケージのため直接呼び出せる
		task.onCreate();

		//------------比較--------------------------

		// null補完によりデフォルト値（0 / TO/DO / MEDIUM）が設定されていること
		assertThat(task.getSortOrder()).isEqualTo(0);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);

		// createdAt/updatedAtも同時に設定されること
		assertThat(task.getCreatedAt()).isNotNull();
		assertThat(task.getUpdatedAt()).isNotNull();
	}

	@Test
	void onCreateでsortOrderとstatusとpriorityが非nullの場合はそのままの値が維持される() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （if(sortOrder==null)/if(status==null)/if(priority==null)の偽側の分岐も明示的に検証する）

		//------------準備--------------------------

		Task task = new Task();
		task.setSortOrder(3);
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.LOW);

		//------------実行--------------------------

		task.onCreate();

		//------------比較--------------------------

		// 既存の値が上書きされずそのまま維持されること
		assertThat(task.getSortOrder()).isEqualTo(3);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(task.getPriority()).isEqualTo(Priority.LOW);
	}

	// ---------------------------------------------------------------
	// 期限切れ／当日期限判定（isOverdue / isDueToday）のテスト
	// ---------------------------------------------------------------

	@Test
	void 期限が基準日より過去でステータスがDONE_CANCELLED以外なら期限切れと判定される() {

		//------------準備--------------------------

		LocalDate today = LocalDate.of(2026, 9, 15);
		Task task = new Task();
		task.setStatus(TaskStatus.TODO);
		task.setDueDate(today.minusDays(1));

		//------------実行 & 比較--------------------------

		assertThat(task.isOverdue(today)).isTrue();
		assertThat(task.isDueToday(today)).isFalse();
	}

	@Test
	void 期限が基準日と同じ場合は当日期限と判定され期限切れにはならない() {

		//------------準備--------------------------

		LocalDate today = LocalDate.of(2026, 9, 15);
		Task task = new Task();
		task.setStatus(TaskStatus.TODO);
		task.setDueDate(today);

		//------------実行 & 比較--------------------------

		assertThat(task.isDueToday(today)).isTrue();
		assertThat(task.isOverdue(today)).isFalse();
	}

	@Test
	void 期限が基準日より未来なら期限切れでも当日期限でもない() {

		//------------準備--------------------------

		LocalDate today = LocalDate.of(2026, 9, 15);
		Task task = new Task();
		task.setStatus(TaskStatus.TODO);
		task.setDueDate(today.plusDays(1));

		//------------実行 & 比較--------------------------

		assertThat(task.isOverdue(today)).isFalse();
		assertThat(task.isDueToday(today)).isFalse();
	}

	@Test
	void 期限が過去でもステータスがDONEなら期限切れと判定されない() {

		//------------準備--------------------------

		LocalDate today = LocalDate.of(2026, 9, 15);
		Task task = new Task();
		task.setStatus(TaskStatus.DONE);
		task.setDueDate(today.minusDays(1));

		//------------実行 & 比較--------------------------

		assertThat(task.isOverdue(today)).isFalse();
	}

	@Test
	void 期限が過去でもステータスがCANCELLEDなら期限切れと判定されない() {

		//------------準備--------------------------

		LocalDate today = LocalDate.of(2026, 9, 15);
		Task task = new Task();
		task.setStatus(TaskStatus.CANCELLED);
		task.setDueDate(today.minusDays(1));

		//------------実行 & 比較--------------------------

		assertThat(task.isOverdue(today)).isFalse();
	}

	@Test
	void 期限が同日でもステータスがDONE_CANCELLEDなら当日期限と判定されない() {

		//------------準備--------------------------

		LocalDate today = LocalDate.of(2026, 9, 15);
		Task doneTask = new Task();
		doneTask.setStatus(TaskStatus.DONE);
		doneTask.setDueDate(today);

		Task cancelledTask = new Task();
		cancelledTask.setStatus(TaskStatus.CANCELLED);
		cancelledTask.setDueDate(today);

		//------------実行 & 比較--------------------------

		assertThat(doneTask.isDueToday(today)).isFalse();
		assertThat(cancelledTask.isDueToday(today)).isFalse();
	}

	@Test
	void 期限が未設定の場合は期限切れにも当日期限にもならない() {

		//------------準備--------------------------

		Task task = new Task();
		task.setStatus(TaskStatus.TODO);

		//------------実行 & 比較--------------------------

		assertThat(task.isOverdue(LocalDate.now())).isFalse();
		assertThat(task.isDueToday(LocalDate.now())).isFalse();
	}

	@Test
	void 引数なしのisOverdueとisDueTodayは現在日時を基準に判定する() {

		// ※ isOverdue()/isDueToday()（引数なし版）は、Thymeleafテンプレートから
		//   ${task.overdue} 等で呼び出すためのラッパー。LocalDate.now()を正しく使っていることを確認する。

		//------------準備--------------------------

		Task overdueTask = new Task();
		overdueTask.setStatus(TaskStatus.TODO);
		overdueTask.setDueDate(LocalDate.now().minusDays(1));

		Task dueTodayTask = new Task();
		dueTodayTask.setStatus(TaskStatus.TODO);
		dueTodayTask.setDueDate(LocalDate.now());

		//------------実行 & 比較--------------------------

		assertThat(overdueTask.isOverdue()).isTrue();
		assertThat(dueTodayTask.isDueToday()).isTrue();
	}
}
