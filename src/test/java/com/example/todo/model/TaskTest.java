package com.example.todo.model;

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

		//------------実行--------------------------

		task.setId(1L);
		task.setTitle("テストタスク");
		task.setDescription("詳細メモ");
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setSortOrder(5);
		task.setCreatedAt(createdAt);
		task.setUpdatedAt(updatedAt);
		task.setCompletedAt(completedAt);

		//------------比較--------------------------

		assertThat(task.getId()).isEqualTo(1L);
		assertThat(task.getTitle()).isEqualTo("テストタスク");
		assertThat(task.getDescription()).isEqualTo("詳細メモ");
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(task.getSortOrder()).isEqualTo(5);
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
		assertThat(task.getSortOrder()).isEqualTo(0);
		assertThat(task.getId()).isNull();
		assertThat(task.getCreatedAt()).isNull();
		assertThat(task.getUpdatedAt()).isNull();
		assertThat(task.getCompletedAt()).isNull();
	}

	@Test
	void onCreateでsortOrderとstatusがnullの場合はデフォルト値が補完される() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （onCreate内のnullチェック分岐 if(sortOrder==null) / if(status==null) は、
		// フィールド初期化子により通常フローでは常にfalseとなり真の分岐が未検証だったため、
		// setSortOrder(null)/setStatus(null)により意図的にnullの状態を作って検証する）

		//------------準備--------------------------

		Task task = new Task();
		task.setSortOrder(null);
		task.setStatus(null);

		//------------実行--------------------------

		// onCreateは@PrePersistのprotectedメソッドだが、同一パッケージのため直接呼び出せる
		task.onCreate();

		//------------比較--------------------------

		// null補完によりデフォルト値（0 / TO/DO）が設定されていること
		assertThat(task.getSortOrder()).isEqualTo(0);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);

		// createdAt/updatedAtも同時に設定されること
		assertThat(task.getCreatedAt()).isNotNull();
		assertThat(task.getUpdatedAt()).isNotNull();
	}

	@Test
	void onCreateでsortOrderとstatusが非nullの場合はそのままの値が維持される() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （if(sortOrder==null)/if(status==null)の偽側の分岐も明示的に検証する）

		//------------準備--------------------------

		Task task = new Task();
		task.setSortOrder(3);
		task.setStatus(TaskStatus.IN_PROGRESS);

		//------------実行--------------------------

		task.onCreate();

		//------------比較--------------------------

		// 既存の値が上書きされずそのまま維持されること
		assertThat(task.getSortOrder()).isEqualTo(3);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
	}
}
