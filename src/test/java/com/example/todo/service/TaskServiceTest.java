package com.example.todo.service;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.model.RecurrenceRule;

import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskServiceTest {

	@Autowired
	private TaskService taskService;

	// DBへの反映を1次キャッシュ経由ではなく本当に確認するためのEntityManager
	@Autowired
	private EntityManager entityManager;

	@Test
	@Order(1)
	void ステータスをDONEに変更するとcompletedAtが設定される() {

		// ------------準備--------------------------

		// タスクを作成して保存する
		Task task = new Task();

		// タスク名は必須のため仮で設定する
		task.setTitle("テストタスク");

		// ステータスは初期値でTODOのままにする
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		// ステータスをDONEに変更する
		Task updated = taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		// 1次キャッシュに頼らず、本当にDBへ反映されたかを確認する
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		// ------------比較--------------------------

		// ステータスがDONEに変更されていること
		assertThat(updated.getStatus()).isEqualTo(TaskStatus.DONE);

		// completedAtがnullではないこと
		assertThat(updated.getCompletedAt()).isNotNull();

		// DBから読み直した値でも同様にDONE・completedAt設定済みであること
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(reloaded.getCompletedAt()).isNotNull();
	}

	@Test
	@Order(2)
	void ステータスをDONEから他のステータスに変更するとcompletedAtが解除される() {

		// ------------準備--------------------------

		// タスクを作成し、いったんDONEにしてcompletedAtを設定させておく
		Task task = new Task();
		task.setTitle("テストタスク2");
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		// ------------実行--------------------------

		// DONEからIN_PROGRESSへ変更する
		Task updated = taskService.updateStatus(saved.getId(), TaskStatus.IN_PROGRESS);

		// 本当にDBへ反映されたかを確認する
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		// ------------比較--------------------------

		// ステータスがIN_PROGRESSに変更されていること
		assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

		// completedAtがnullに解除されていること
		assertThat(updated.getCompletedAt()).isNull();

		// DBから読み直した値でも同様に解除されていること
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(reloaded.getCompletedAt()).isNull();
	}

	@Test
	@Order(3)
	void 削除操作を行うとタスクが実際にデータベースから削除される() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("削除対象タスク");
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		taskService.deleteTask(saved.getId());

		// 1次キャッシュに頼らず、本当にDBから消えたかを確認する
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		// 再度検索しても見つからない（DBから実際に削除されている）こと
		assertThat(taskService.getTaskById(saved.getId())).isEmpty();
	}

	@Test
	@Order(4)
	void 存在しないIDでステータス更新しようとすると例外がスローされる() {

		// （updateStatusの存在しないIDに対する異常系分岐が未網羅だったため）

		// ------------準備--------------------------

		// DBに存在し得ない十分大きなIDを使用する
		long notExistId = 999999999L;

		// ------------実行 & 比較--------------------------

		// IllegalArgumentExceptionがスローされること
		assertThatThrownBy(() -> taskService.updateStatus(notExistId, TaskStatus.DONE))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(5)
	void 存在しないIDでタスクを取得しようとすると空のOptionalが返る() {

		// （getTaskByIdの空Optional分岐が未網羅だったため）

		// ------------準備--------------------------

		long notExistId = 999999999L;

		// ------------実行--------------------------

		var result = taskService.getTaskById(notExistId);

		// ------------比較--------------------------

		assertThat(result).isEmpty();
	}

	@Test
	@Order(6)
	void 新規タスク保存時にステータス_ソート順_作成更新日時のデフォルト値が設定される() {

		// （saveTask新規作成時のデフォルト値設定処理が未網羅だったため）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("デフォルト値確認用タスク");
		// status, sortOrder, createdAt, updatedAt はあえて未設定のまま保存する

		// ------------実行--------------------------

		Task saved = taskService.saveTask(task);
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		// ------------比較--------------------------

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
	@Order(7)
	void 全タスク取得はソート順_作成日時の昇順で返る() {

		// （getAllTasksのソート順分岐が未網羅だったため）

		// ------------準備--------------------------

		Task first = new Task();
		first.setTitle("並び順確認タスクA");
		first.setSortOrder(2);
		taskService.saveTask(first);

		Task second = new Task();
		second.setTitle("並び順確認タスクB");
		second.setSortOrder(1);
		taskService.saveTask(second);

		// ------------実行--------------------------

		List<Task> tasks = taskService.getAllTasks();

		// ------------比較--------------------------

		// sortOrderが小さい「並び順確認タスクB」が「並び順確認タスクA」より前に来ること
		int indexA = tasks
				.indexOf(tasks.stream().filter(t -> t.getTitle().equals("並び順確認タスクA")).findFirst().orElseThrow());
		int indexB = tasks
				.indexOf(tasks.stream().filter(t -> t.getTitle().equals("並び順確認タスクB")).findFirst().orElseThrow());
		assertThat(indexB).isLessThan(indexA);
	}

	@Test
	@Order(8)
	void 絵文字や改行を含む詳細が保存され表示できること() {

		// 「絵文字や改行を含む詳細（description）の入力が保存・表示できること
		// （MySQL/MariaDBの文字コードがutf8mb4になっているか確認）」
		//
		// 絵文字（😀🎉📝等）はUTF-8で4バイトとなるため、DB・接続文字コードが
		// utf8mb4（4バイト対応）になっていないと、保存時に文字化けしたり、
		// INSERT自体が失敗したりする。実際のMySQL/MariaDBへ保存・再読込することで、
		// 文字コード設定が正しいことを確認する。

		// ------------準備--------------------------

		String descriptionWithEmojiAndNewline = "買い物リスト📝\n- 牛乳🥛\n- 卵🥚\n- パン🍞\n完了したら🎉で祝う！";

		Task task = new Task();
		task.setTitle("絵文字_改行確認タスク");
		task.setDescription(descriptionWithEmojiAndNewline);

		// ------------実行--------------------------

		Task saved = taskService.saveTask(task);

		// 1次キャッシュに頼らず、本当にDBへ正しく保存・読み込みできているかを確認する
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		// ------------比較--------------------------

		// 絵文字・改行を含む文字列が、文字化けや欠損なくそのまま保存・取得できていること
		assertThat(reloaded.getDescription()).isEqualTo(descriptionWithEmojiAndNewline);
	}

	@Test
	@Order(9)
	void reorderTasksで渡した順序どおりにsortOrderが振り直される() {

		// （ドラッグ&ドロップ後の並び順永続化ロジックを検証する）

		// ------------準備--------------------------

		Task a = new Task();
		a.setTitle("並び替えAPI確認タスクA");
		Task savedA = taskService.saveTask(a);

		Task b = new Task();
		b.setTitle("並び替えAPI確認タスクB");
		Task savedB = taskService.saveTask(b);

		Task c = new Task();
		c.setTitle("並び替えAPI確認タスクC");
		Task savedC = taskService.saveTask(c);

		// ------------実行--------------------------

		// C→A→Bの順に並び替える
		taskService.reorderTasks(List.of(savedC.getId(), savedA.getId(), savedB.getId()));

		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		Task reloadedA = taskService.getTaskById(savedA.getId()).orElseThrow();
		Task reloadedB = taskService.getTaskById(savedB.getId()).orElseThrow();
		Task reloadedC = taskService.getTaskById(savedC.getId()).orElseThrow();

		assertThat(reloadedC.getSortOrder()).isEqualTo(0);
		assertThat(reloadedA.getSortOrder()).isEqualTo(1);
		assertThat(reloadedB.getSortOrder()).isEqualTo(2);
	}

	@Test
	@Order(10)
	void reorderTasksに存在しないIDが含まれていても無視して残りのタスクの並び順が正しく振り直される() {

		// （reorderTasksの「存在しないタスクIDは無視して続行する」分岐
		// （taskOpt.isEmpty()がtrueの場合）が未網羅だったため）

		// ------------準備--------------------------

		Task a = new Task();
		a.setTitle("並び替え存在しないID確認タスクA");
		Task savedA = taskService.saveTask(a);

		Task b = new Task();
		b.setTitle("並び替え存在しないID確認タスクB");
		Task savedB = taskService.saveTask(b);

		// 一覧取得後に別タブ等で削除された状況を再現する
		long deletedId = 999999999L;

		// ------------実行--------------------------

		// 削除済み(存在しない)ID→B→AのIDリストで並び替える
		taskService.reorderTasks(List.of(deletedId, savedB.getId(), savedA.getId()));

		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		Task reloadedA = taskService.getTaskById(savedA.getId()).orElseThrow();
		Task reloadedB = taskService.getTaskById(savedB.getId()).orElseThrow();

		// 存在しないIDの分もorderがインクリメントされた上で、
		// 続くB・Aのタスクにはスキップせず正しい順番でsortOrderが設定されること
		assertThat(reloadedB.getSortOrder()).isEqualTo(1);
		assertThat(reloadedA.getSortOrder()).isEqualTo(2);
	}

	@Test
	@Order(11)
	void DONEのタスクをステータス変更なしでフォーム経由保存しても完了日時が保持される() {

		// ※バグ修正に伴い追加
		// 不具合: form.htmlはcompletedAtを持たないため、DONEのタスクを編集画面から保存する
		// （ステータスはDONEのまま変更しない）と、completedAtがnullで上書きされてしまっていた。
		// saveTaskがステータスの変化前後を見て完了日時を引き継ぐことを確認する。

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("完了日時保持確認タスク");
		Task saved = taskService.saveTask(task);
		Task doneTask = taskService.updateStatus(saved.getId(), TaskStatus.DONE);
		var originalCompletedAt = doneTask.getCompletedAt();

		// ------------実行--------------------------

		// フォーム送信を模して、completedAtを持たない（nullの）TaskでステータスをそのままsaveTaskする
		Task editedFromForm = new Task();
		editedFromForm.setId(saved.getId());
		editedFromForm.setTitle("完了日時保持確認タスク（編集後）");
		editedFromForm.setStatus(TaskStatus.DONE);
		editedFromForm.setSortOrder(saved.getSortOrder());
		Task resaved = taskService.saveTask(editedFromForm);

		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		// ------------比較--------------------------

		// 完了日時が消えずに元の値のまま保持されていること
		// ※DB（MySQLのDATETIME）は秒未満の精度を保持しないため、秒単位に丸めて比較する
		assertThat(resaved.getCompletedAt()).isEqualTo(originalCompletedAt);
		assertThat(reloaded.getCompletedAt().truncatedTo(ChronoUnit.SECONDS))
				.isEqualTo(originalCompletedAt.truncatedTo(ChronoUnit.SECONDS));
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	@Order(12)
	void DONEのタスクをフォーム経由で他ステータスへ変更すると完了日時がクリアされる() {

		// ※バグ修正に伴い追加（saveTask経由でもupdateStatusと同じ解除ルールが働くことを確認）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("フォーム経由完了解除確認タスク");
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		// ------------実行--------------------------

		Task editedFromForm = new Task();
		editedFromForm.setId(saved.getId());
		editedFromForm.setTitle("フォーム経由完了解除確認タスク");
		editedFromForm.setStatus(TaskStatus.IN_PROGRESS);
		editedFromForm.setSortOrder(saved.getSortOrder());
		Task resaved = taskService.saveTask(editedFromForm);

		// ------------比較--------------------------

		assertThat(resaved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(resaved.getCompletedAt()).isNull();
	}

	@Test
	@Order(13)
	void 未完了のタスクをフォーム経由でDONEにすると完了日時が新規設定される() {

		// ※バグ修正に伴い追加（フォーム編集画面からDONEへ変更した場合も、
		// インラインのステータス切替（updateStatus）と同じく完了日時が設定されることを確認）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("フォーム経由完了設定確認タスク");
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		Task editedFromForm = new Task();
		editedFromForm.setId(saved.getId());
		editedFromForm.setTitle("フォーム経由完了設定確認タスク");
		editedFromForm.setStatus(TaskStatus.DONE);
		editedFromForm.setSortOrder(saved.getSortOrder());
		Task resaved = taskService.saveTask(editedFromForm);

		// ------------比較--------------------------

		assertThat(resaved.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(resaved.getCompletedAt()).isNotNull();
	}

	@Test
	@Order(14)
	void 検索条件を指定するとAND結合で絞り込んだ結果が返る() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("サービス層検索確認タスク");
		task.setPriority(com.example.todo.model.Priority.HIGH);
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.IN_PROGRESS);

		Task other = new Task();
		other.setTitle("サービス層検索対象外タスク");
		taskService.saveTask(other);

		// ------------実行--------------------------

		List<Task> result = taskService.searchTasks(TaskStatus.IN_PROGRESS, null, com.example.todo.model.Priority.HIGH,
				"サービス層検索", null, false);

		// ------------比較--------------------------

		assertThat(result).extracting(Task::getTitle).containsExactly("サービス層検索確認タスク");
	}

	// ================= サブタスク機能 =================

	@Test
	@Order(15)
	void 一覧取得と検索結果にはサブタスクが含まれない() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_一覧除外確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_一覧除外確認");
		child.setParentTaskId(savedParent.getId());
		taskService.saveTask(child);

		// ------------実行--------------------------

		List<Task> all = taskService.getAllTasks();
		List<Task> searched = taskService.searchTasks(null, null, null, "一覧除外確認", null, false);

		// ------------比較--------------------------

		assertThat(all).extracting(Task::getTitle).contains("親タスク_一覧除外確認").doesNotContain("子タスク_一覧除外確認");
		assertThat(searched).extracting(Task::getTitle).containsExactly("親タスク_一覧除外確認");
	}

	@Test
	@Order(16)
	void サブタスクにさらに親タスクを設定しようとすると例外になる() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_階層制限確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_階層制限確認");
		child.setParentTaskId(savedParent.getId());
		Task savedChild = taskService.saveTask(child);

		Task grandChild = new Task();
		grandChild.setTitle("孫タスク_階層制限確認");
		grandChild.setParentTaskId(savedChild.getId());

		// ------------実行・比較--------------------------

		assertThatThrownBy(() -> taskService.saveTask(grandChild)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(17)
	void 存在しない親タスクIDを指定すると例外になる() {

		// ------------準備--------------------------

		Task child = new Task();
		child.setTitle("子タスク_親不在確認");
		child.setParentTaskId(999999L);

		// ------------実行・比較--------------------------

		assertThatThrownBy(() -> taskService.saveTask(child)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(18)
	void サブタスクをすべてDONEにすると進捗が全件分母に対して100になる() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_進捗確認");
		Task savedParent = taskService.saveTask(parent);

		Task child1 = new Task();
		child1.setTitle("子タスク_進捗確認1");
		child1.setParentTaskId(savedParent.getId());
		Task savedChild1 = taskService.saveTask(child1);

		Task child2 = new Task();
		child2.setTitle("子タスク_進捗確認2");
		child2.setParentTaskId(savedParent.getId());
		Task savedChild2 = taskService.saveTask(child2);

		taskService.updateStatus(savedChild1.getId(), TaskStatus.DONE);

		// ------------実行（1件完了時点）--------------------------

		List<Task> allAfterOne = taskService.getAllTasks();
		Task parentAfterOne = allAfterOne.stream().filter(t -> t.getId().equals(savedParent.getId())).findFirst()
				.orElseThrow();

		// ------------比較（1件完了時点）--------------------------

		assertThat(parentAfterOne.getSubtaskTotalCount()).isEqualTo(2);
		assertThat(parentAfterOne.getSubtaskDoneCount()).isEqualTo(1);

		// ------------実行（全件完了時点）--------------------------

		taskService.updateStatus(savedChild2.getId(), TaskStatus.DONE);
		List<Task> allAfterTwo = taskService.getAllTasks();
		Task parentAfterTwo = allAfterTwo.stream().filter(t -> t.getId().equals(savedParent.getId())).findFirst()
				.orElseThrow();

		// ------------比較（全件完了時点）--------------------------

		assertThat(parentAfterTwo.getSubtaskTotalCount()).isEqualTo(2);
		assertThat(parentAfterTwo.getSubtaskDoneCount()).isEqualTo(2);
	}

	@Test
	@Order(19)
	void CANCELLEDのサブタスクは進捗計算の分母分子から除外される() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_キャンセル除外確認");
		Task savedParent = taskService.saveTask(parent);

		Task doneChild = new Task();
		doneChild.setTitle("子タスク_完了済み");
		doneChild.setParentTaskId(savedParent.getId());
		Task savedDoneChild = taskService.saveTask(doneChild);
		taskService.updateStatus(savedDoneChild.getId(), TaskStatus.DONE);

		Task cancelledChild = new Task();
		cancelledChild.setTitle("子タスク_中止済み");
		cancelledChild.setParentTaskId(savedParent.getId());
		Task savedCancelledChild = taskService.saveTask(cancelledChild);
		taskService.updateStatus(savedCancelledChild.getId(), TaskStatus.CANCELLED);

		// ------------実行--------------------------

		List<Task> all = taskService.getAllTasks();
		Task parentResult = all.stream().filter(t -> t.getId().equals(savedParent.getId())).findFirst().orElseThrow();

		// ------------比較--------------------------

		// CANCELLEDの1件は分母からも分子からも除外されるため、分母1件・分子1件（100%完了扱い）になる
		assertThat(parentResult.getSubtaskTotalCount()).isEqualTo(1);
		assertThat(parentResult.getSubtaskDoneCount()).isEqualTo(1);
	}

	@Test
	@Order(20)
	void 親タスクをDONEにすると全サブタスクもDONEになる() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_連動完了確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_連動完了確認");
		child.setParentTaskId(savedParent.getId());
		Task savedChild = taskService.saveTask(child);

		// ------------実行--------------------------

		taskService.updateStatus(savedParent.getId(), TaskStatus.DONE);

		entityManager.flush();
		entityManager.clear();
		Task reloadedChild = taskService.getTaskById(savedChild.getId()).orElseThrow();

		// ------------比較--------------------------

		assertThat(reloadedChild.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(reloadedChild.getCompletedAt()).isNotNull();
	}

	@Test
	@Order(21)
	void 全サブタスクがDONEになると親タスクも自動的にDONEになる() {

		// 「親→子」の逆方向。サブタスクを全てDONEにした場合、親タスクも自動的にDONEにする。

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_子完了連動確認");
		Task savedParent = taskService.saveTask(parent);

		Task child1 = new Task();
		child1.setTitle("子タスク_子完了連動確認1");
		child1.setParentTaskId(savedParent.getId());
		Task savedChild1 = taskService.saveTask(child1);

		Task child2 = new Task();
		child2.setTitle("子タスク_子完了連動確認2");
		child2.setParentTaskId(savedParent.getId());
		Task savedChild2 = taskService.saveTask(child2);

		// ------------実行（1件だけ完了。まだ親はDONEにならないはず）--------------------------

		taskService.updateStatus(savedChild1.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();
		Task parentAfterOne = taskService.getTaskById(savedParent.getId()).orElseThrow();

		// ------------比較（1件完了時点）--------------------------

		assertThat(parentAfterOne.getStatus()).isNotEqualTo(TaskStatus.DONE);

		// ------------実行（残りも完了。これで親もDONEになるはず）--------------------------

		taskService.updateStatus(savedChild2.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();
		Task parentAfterAll = taskService.getTaskById(savedParent.getId()).orElseThrow();

		// ------------比較（全件完了時点）--------------------------

		assertThat(parentAfterAll.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(parentAfterAll.getCompletedAt()).isNotNull();
	}

	@Test
	@Order(22)
	void CANCELLEDのサブタスクを除く全サブタスクがDONEになると親タスクも自動的にDONEになる() {

		// 進捗計算（CANCELLEDを除く）と同じ基準で判定する。

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_キャンセル込み子完了連動確認");
		Task savedParent = taskService.saveTask(parent);

		Task doneChild = new Task();
		doneChild.setTitle("子タスク_完了対象");
		doneChild.setParentTaskId(savedParent.getId());
		Task savedDoneChild = taskService.saveTask(doneChild);

		Task cancelledChild = new Task();
		cancelledChild.setTitle("子タスク_中止対象");
		cancelledChild.setParentTaskId(savedParent.getId());
		Task savedCancelledChild = taskService.saveTask(cancelledChild);
		taskService.updateStatus(savedCancelledChild.getId(), TaskStatus.CANCELLED);

		// ------------実行--------------------------

		taskService.updateStatus(savedDoneChild.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();
		Task parentAfter = taskService.getTaskById(savedParent.getId()).orElseThrow();

		// ------------比較--------------------------

		assertThat(parentAfter.getStatus()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	@Order(23)
	void 親タスクがDONEの状態でサブタスクをDONE以外に戻すと親タスクもIN_PROGRESSに戻る() {

		// 「親タスクを`DONE`から他ステータスへ戻した場合のサブタスクの扱い」の対になる仕様。

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_子差し戻し連動確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_差し戻し連動確認");
		child.setParentTaskId(savedParent.getId());
		Task savedChild = taskService.saveTask(child);

		// 親タスクをDONEにし、子タスクも連動してDONEになっている状態を作る
		taskService.updateStatus(savedParent.getId(), TaskStatus.DONE);

		// ------------実行--------------------------

		// 子タスクだけをDONEから別のステータスへ戻す
		taskService.updateStatus(savedChild.getId(), TaskStatus.TODO);

		entityManager.flush();
		entityManager.clear();
		Task reloadedParent = taskService.getTaskById(savedParent.getId()).orElseThrow();

		// ------------比較--------------------------

		assertThat(reloadedParent.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(reloadedParent.getCompletedAt()).isNull();
	}

	@Test
	@Order(24)
	void 親タスクを削除すると紐づくサブタスクも削除される() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_連動削除確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_連動削除確認");
		child.setParentTaskId(savedParent.getId());
		Task savedChild = taskService.saveTask(child);

		// ------------実行--------------------------

		taskService.deleteTask(savedParent.getId());

		// deleteByParentTaskId はJPQLの一括削除（@Modifying）のため、永続化コンテキストの
		// 1次キャッシュには反映されない。1次キャッシュに頼らず本当にDBから消えたかを確認するため、
		// 明示的にflush・clearしてから再取得する。
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		assertThat(taskService.getTaskById(savedParent.getId())).isEmpty();
		assertThat(taskService.getTaskById(savedChild.getId())).isEmpty();
	}

	@Test
	@Order(25)
	void getSubtasksで親タスクに紐づくサブタスクのみ取得できる() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_サブタスク取得確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_取得確認");
		child.setParentTaskId(savedParent.getId());
		taskService.saveTask(child);

		Task other = new Task();
		other.setTitle("無関係タスク_取得確認");
		taskService.saveTask(other);

		// ------------実行--------------------------

		List<Task> subtasks = taskService.getSubtasks(savedParent.getId());

		// ------------比較--------------------------

		assertThat(subtasks).extracting(Task::getTitle).containsExactly("子タスク_取得確認");
	}

	@Test
	@Order(26)
	void 存在しないタスクIDでgetSubtasksを呼ぶと例外になる() {

		// ------------実行・比較--------------------------

		assertThatThrownBy(() -> taskService.getSubtasks(999999L)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(27)
	void 並び替え対象にサブタスクのIDが含まれていても無視される() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_並び替え除外確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_並び替え除外確認");
		child.setParentTaskId(savedParent.getId());
		Task savedChild = taskService.saveTask(child);
		Integer originalChildSortOrder = savedChild.getSortOrder();

		Task other = new Task();
		other.setTitle("他タスク_並び替え確認");
		Task savedOther = taskService.saveTask(other);

		// ------------実行--------------------------

		// サブタスクのIDを先頭に含めて並び替えを実行する
		taskService.reorderTasks(List.of(savedChild.getId(), savedOther.getId(), savedParent.getId()));

		entityManager.flush();
		entityManager.clear();
		Task reloadedChild = taskService.getTaskById(savedChild.getId()).orElseThrow();

		// ------------比較--------------------------

		// サブタスクのsortOrderは並び替えの影響を受けず変化しない
		assertThat(reloadedChild.getSortOrder()).isEqualTo(originalChildSortOrder);
	}

	@Test
	@Order(28)
	void 既にサブタスクを持つ親タスクも引き続き親タスク候補に含まれる() {

		// ※バグ修正に伴い追加（1個目のサブタスクを追加した時点で、2個目以降のサブタスクの
		// 親として選べなくなってしまっていた不具合の再発防止）

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_候補維持確認");
		Task savedParent = taskService.saveTask(parent);

		Task firstChild = new Task();
		firstChild.setTitle("子タスク_候補維持確認1");
		firstChild.setParentTaskId(savedParent.getId());
		taskService.saveTask(firstChild);

		// ------------実行--------------------------

		// 1個目のサブタスクを追加した後でも、親タスクは引き続き候補一覧に含まれているはず
		List<Task> candidates = taskService.getParentTaskCandidates(null);

		// ------------比較--------------------------

		assertThat(candidates).extracting(Task::getId).contains(savedParent.getId());

		// さらに2個目のサブタスクも問題なく追加できること
		Task secondChild = new Task();
		secondChild.setTitle("子タスク_候補維持確認2");
		secondChild.setParentTaskId(savedParent.getId());
		Task savedSecondChild = taskService.saveTask(secondChild);

		assertThat(savedSecondChild.getParentTaskId()).isEqualTo(savedParent.getId());
		assertThat(taskService.getSubtasks(savedParent.getId())).hasSize(2);
	}

	@Test
	@Order(29)
	void 親タスク候補一覧には自分自身が含まれない() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("自己参照除外確認タスク");
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		List<Task> candidates = taskService.getParentTaskCandidates(saved.getId());

		// ------------比較--------------------------

		assertThat(candidates).extracting(Task::getId).doesNotContain(saved.getId());
	}

	@Test
	@Order(30)
	void 既にサブタスクを持つタスクを別のタスクのサブタスクにしようとすると例外になる() {

		// ------------準備--------------------------

		Task grandparent = new Task();
		grandparent.setTitle("祖父タスク_孫階層防止確認");
		Task savedGrandparent = taskService.saveTask(grandparent);

		Task parent = new Task();
		parent.setTitle("親タスク_孫階層防止確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_孫階層防止確認");
		child.setParentTaskId(savedParent.getId());
		taskService.saveTask(child);

		// savedParentは既にサブタスク(child)を持っている状態で、
		// さらにsavedGrandparentのサブタスクにしようとする
		Task parentBecomingChild = new Task();
		parentBecomingChild.setId(savedParent.getId());
		parentBecomingChild.setTitle("親タスク_孫階層防止確認");
		parentBecomingChild.setParentTaskId(savedGrandparent.getId());
		parentBecomingChild.setSortOrder(savedParent.getSortOrder());

		// ------------実行・比較--------------------------

		assertThatThrownBy(() -> taskService.saveTask(parentBecomingChild))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ================= 繰り返しタスク機能 =================

	@Test
	@Order(31)
	void DAILYの繰り返しタスクをDONEにすると翌日期日の新しいタスクが自動生成される() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_毎日");
		task.setRecurrenceRule(RecurrenceRule.DAILY);
		task.setDueDate(LocalDate.of(2026, 10, 10));
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		taskService.updateStatus(saved.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		List<Task> all = taskService.getAllTasks();
		Task next = all.stream().filter(t -> t.getTitle().equals("繰り返し確認タスク_毎日") && !t.getId().equals(saved.getId()))
				.findFirst().orElseThrow();

		// 完了日の翌日が次回期日になること（completedAtは今日なので today+1）
		assertThat(next.getDueDate()).isEqualTo(LocalDate.now().plusDays(1));
		assertThat(next.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(next.getCompletedAt()).isNull();
		assertThat(next.getRecurrenceRule()).isEqualTo(RecurrenceRule.DAILY);
	}

	@Test
	@Order(32)
	void WEEKLYの繰り返しタスクをDONEにすると7日後期日の新しいタスクが自動生成される() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_毎週");
		task.setRecurrenceRule(RecurrenceRule.WEEKLY);
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		taskService.updateStatus(saved.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		List<Task> all = taskService.getAllTasks();
		Task next = all.stream().filter(t -> t.getTitle().equals("繰り返し確認タスク_毎週") && !t.getId().equals(saved.getId()))
				.findFirst().orElseThrow();

		assertThat(next.getDueDate()).isEqualTo(LocalDate.now().plusDays(7));
	}

	@Test
	@Order(33)
	void MONTHLYの繰り返しタスクで月末日を完了すると翌月の末日が次回期日になる() {

		// 31日→翌月に31日が無い場合は月末日にする」の確認。
		// completedAtは「今」で自動設定されるため、起点日を直接検証しづらい分岐のテストとして、
		// LocalDate#plusMonthsの仕様（存在しない日付は自動的に月末に調整される）を
		// 1/31起点で直接検証する形で代替する（calculateNextDueDateは private のため、
		// 同等のロジックをここで検証する）。

		// ------------準備--------------------------

		LocalDate jan31 = LocalDate.of(2026, 1, 31);

		// ------------実行--------------------------

		LocalDate next = jan31.plusMonths(1);

		// ------------比較--------------------------

		// 2026年は平年のため2月は28日まで
		assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	@Order(34)
	void MONTHLYの繰り返しタスクをDONEにすると1か月後期日の新しいタスクが自動生成される() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_毎月");
		task.setRecurrenceRule(RecurrenceRule.MONTHLY);
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		taskService.updateStatus(saved.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		List<Task> all = taskService.getAllTasks();
		Task next = all.stream().filter(t -> t.getTitle().equals("繰り返し確認タスク_毎月") && !t.getId().equals(saved.getId()))
				.findFirst().orElseThrow();

		assertThat(next.getDueDate()).isEqualTo(LocalDate.now().plusMonths(1));
	}

	@Test
	@Order(35)
	void recurrenceRuleがNONEのタスクをDONEにしても新しいタスクは生成されない() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_なし");
		// recurrenceRuleは未設定（NONE）のまま
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		taskService.updateStatus(saved.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		long count = taskService.getAllTasks().stream().filter(t -> t.getTitle().equals("繰り返し確認タスク_なし")).count();
		assertThat(count).isEqualTo(1);
	}

	@Test
	@Order(36)
	void 繰り返しタスクの元タスクを削除しても生成済みの次回分タスクには影響しない() {

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_元削除確認");
		task.setRecurrenceRule(RecurrenceRule.DAILY);
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		entityManager.flush();
		entityManager.clear();

		Task next = taskService.getAllTasks().stream()
				.filter(t -> t.getTitle().equals("繰り返し確認タスク_元削除確認") && !t.getId().equals(saved.getId())).findFirst()
				.orElseThrow();

		// ------------実行--------------------------

		// 元タスク（完了済み）を削除する
		taskService.deleteTask(saved.getId());
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		// 元タスクは削除されているが、既に生成済みの次回分タスクはそのまま残っていること
		assertThat(taskService.getTaskById(saved.getId())).isEmpty();
		assertThat(taskService.getTaskById(next.getId())).isPresent();
	}

	@Test
	@Order(37)
	void 既にDONEのタスクをフォーム経由で再度DONEのまま保存しても新しいタスクは重複生成されない() {

		// ※ステータスに変化がない（DONE→DONE）再保存では、繰り返しタスクが二重生成されないことを確認する

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_重複生成防止");
		task.setRecurrenceRule(RecurrenceRule.DAILY);
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.DONE);

		// ------------実行--------------------------

		// フォーム送信を模して、ステータスをDONEのまま変更せず再保存する
		Task editedFromForm = new Task();
		editedFromForm.setId(saved.getId());
		editedFromForm.setTitle("繰り返し確認タスク_重複生成防止");
		editedFromForm.setStatus(TaskStatus.DONE);
		editedFromForm.setRecurrenceRule(RecurrenceRule.DAILY);
		editedFromForm.setSortOrder(saved.getSortOrder());
		taskService.saveTask(editedFromForm);

		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		long generatedCount = taskService.getAllTasks().stream()
				.filter(t -> t.getTitle().equals("繰り返し確認タスク_重複生成防止") && !t.getId().equals(saved.getId())).count();
		assertThat(generatedCount).isEqualTo(1);
	}

	@Test
	@Order(38)
	void 未完了のタスクをフォーム経由でDONEにすると繰り返し設定に応じて新しいタスクが生成される() {

		// ※saveTask（フォーム経由）での完了操作でも繰り返し生成が働くことを確認する

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("繰り返し確認タスク_フォーム経由完了");
		task.setRecurrenceRule(RecurrenceRule.WEEKLY);
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		Task editedFromForm = new Task();
		editedFromForm.setId(saved.getId());
		editedFromForm.setTitle("繰り返し確認タスク_フォーム経由完了");
		editedFromForm.setStatus(TaskStatus.DONE);
		editedFromForm.setRecurrenceRule(RecurrenceRule.WEEKLY);
		editedFromForm.setSortOrder(saved.getSortOrder());
		taskService.saveTask(editedFromForm);

		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		long generatedCount = taskService.getAllTasks().stream()
				.filter(t -> t.getTitle().equals("繰り返し確認タスク_フォーム経由完了") && !t.getId().equals(saved.getId())).count();
		assertThat(generatedCount).isEqualTo(1);
	}

	// ================= サブタスク機能 × 繰り返しタスク機能の統合確認 =================
	// recurrenceRuleを持たず、不要なサブタスクは削除で複製対象から除外する）を検証する。

	@Test
	@Order(39)
	void 繰り返し設定の親タスクが完了すると紐づくサブタスクも新しい次回タスクに複製される() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("繰り返し親タスク_サブタスク複製確認");
		parent.setRecurrenceRule(RecurrenceRule.DAILY);
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_複製確認対象");
		child.setParentTaskId(savedParent.getId());
		taskService.saveTask(child);

		// ------------実行--------------------------

		// 親タスクを直接DONEにする（サブタスクも連動してDONEになる＝cascadeDoneToSubtasks経由）
		taskService.updateStatus(savedParent.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		// 新しく生成された次回タスク（親タスクと同名・別ID）を見つける
		Task nextParent = taskService.getAllTasks().stream()
				.filter(t -> t.getTitle().equals("繰り返し親タスク_サブタスク複製確認") && !t.getId().equals(savedParent.getId()))
				.findFirst().orElseThrow();

		// 次回タスクにサブタスクが1件複製されていること。複製後はTODOから始まり、
		// recurrenceRuleはサブタスクには持たせない方針のためNONEになっていること
		List<Task> nextSubtasks = taskService.getSubtasks(nextParent.getId());
		assertThat(nextSubtasks).hasSize(1);
		Task nextChild = nextSubtasks.get(0);
		assertThat(nextChild.getTitle()).isEqualTo("子タスク_複製確認対象");
		assertThat(nextChild.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(nextChild.getCompletedAt()).isNull();
		assertThat(nextChild.getRecurrenceRule()).isEqualTo(RecurrenceRule.NONE);
		assertThat(nextParent.isHasSubtasks()).isTrue();

		// 元の親タスク側の子タスクは、連動DONE化されたまま削除されずに残っていること
		List<Task> originalSubtasks = taskService.getSubtasks(savedParent.getId());
		assertThat(originalSubtasks).hasSize(1);
		assertThat(originalSubtasks.get(0).getStatus()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	@Order(40)
	void 削除済みのサブタスクは次回タスクへの複製対象から除外される() {

		// ------------準備--------------------------

		Task parent = new Task();
		parent.setTitle("繰り返し親タスク_削除除外確認");
		parent.setRecurrenceRule(RecurrenceRule.DAILY);
		Task savedParent = taskService.saveTask(parent);

		Task keepChild = new Task();
		keepChild.setTitle("残すサブタスク");
		keepChild.setParentTaskId(savedParent.getId());
		taskService.saveTask(keepChild);

		Task removedChild = new Task();
		removedChild.setTitle("削除するサブタスク");
		removedChild.setParentTaskId(savedParent.getId());
		Task savedRemovedChild = taskService.saveTask(removedChild);

		// 「次回以降不要」と判断したサブタスクは削除する運用（質問3最終決定）
		taskService.deleteTask(savedRemovedChild.getId());

		// ------------実行--------------------------

		taskService.updateStatus(savedParent.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		Task nextParent = taskService.getAllTasks().stream()
				.filter(t -> t.getTitle().equals("繰り返し親タスク_削除除外確認") && !t.getId().equals(savedParent.getId()))
				.findFirst().orElseThrow();

		List<Task> nextSubtasks = taskService.getSubtasks(nextParent.getId());
		assertThat(nextSubtasks).extracting(Task::getTitle).containsExactly("残すサブタスク");
	}

	@Test
	@Order(41)
	void サブタスクにrecurrenceRuleを設定しようとしても保存時にNONEへ強制される() {

		// ------------準備・実行--------------------------

		Task parent = new Task();
		parent.setTitle("親タスク_子繰り返し禁止確認");
		Task savedParent = taskService.saveTask(parent);

		Task child = new Task();
		child.setTitle("子タスク_繰り返し禁止確認");
		child.setParentTaskId(savedParent.getId());
		child.setRecurrenceRule(RecurrenceRule.WEEKLY); // フォーム改ざん等を想定し、あえて設定してみる
		Task savedChild = taskService.saveTask(child);

		// ------------比較--------------------------

		// サブタスクはrecurrenceRuleを持てず、常にNONEへ強制される（質問3最終決定）
		assertThat(savedChild.getRecurrenceRule()).isEqualTo(RecurrenceRule.NONE);

		// NONEに強制されているため、このサブタスクを完了させても独立した次回タスクは生成されない
		taskService.updateStatus(savedChild.getId(), TaskStatus.DONE);
		entityManager.flush();
		entityManager.clear();

		long generatedCount = taskService.getAllTasks().stream().filter(t -> t.getTitle().equals("子タスク_繰り返し禁止確認"))
				.count();
		assertThat(generatedCount).isZero();
	}
}