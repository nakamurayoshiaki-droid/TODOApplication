package com.example.todo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.service.TaskService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * TaskViewController の画面表示・フォーム送信を検証するテストクラス。
 * <p>
 * 実際にHTTPリクエストを模擬し（MockMvc）、バリデーションエラー時の挙動や
 * ステータスに応じた一覧画面の表示スタイル切替を確認する。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskViewControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TaskService taskService;

	@Test
	void タイトルが空のまま保存しようとするとエラーになりフォームが再表示される() throws Exception {

		//------------準備--------------------------

		long beforeCount = taskService.getAllTasks().size();

		//------------実行 & 比較--------------------------

		// タイトルを空でPOSTすると、一覧へリダイレクトされず同じフォーム画面が返る
		mockMvc.perform(post("/tasks")
				.param("title", "")
				.param("sortOrder", "0"))
				.andExpect(status().isOk())
				.andExpect(view().name("tasks/form"));

		// タスクが保存されていない（件数が増えていない）こと
		assertThat(taskService.getAllTasks()).hasSize((int) beforeCount);
	}

	@Test
	void ステータスがON_HOLDのタスクは一覧でis_on_holdクラスが付与される() throws Exception {

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("保留中のタスク");
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.ON_HOLD);

		//------------実行 & 比較--------------------------

		// 一覧画面のレンダリング結果に is-on-hold クラスが含まれること
		mockMvc.perform(get("/tasks"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("is-on-hold")));
	}

	@Test
	void ステータスがCANCELLEDのタスクは一覧でis_cancelledクラスが付与される() throws Exception {

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("中止したタスク");
		Task saved = taskService.saveTask(task);
		taskService.updateStatus(saved.getId(), TaskStatus.CANCELLED);

		//------------実行 & 比較--------------------------

		// 一覧画面のレンダリング結果に is-cancelled クラスが含まれること
		mockMvc.perform(get("/tasks"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("is-cancelled")));
	}

	@Test
	void 新規作成フォーム画面が空のタスクとステータス一覧付きで表示される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （newTaskFormメソッドが未網羅だったため）

		//------------実行 & 比較--------------------------

		mockMvc.perform(get("/tasks/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("tasks/form"))
				.andExpect(model().attributeExists("task"))
				.andExpect(model().attributeExists("statuses"))
				// 新規作成なのでidが未設定であること
				.andExpect(model().attribute("task", org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.nullValue())));
	}

	@Test
	void 編集フォーム画面に対象タスクの情報が表示される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （editTaskFormメソッドの正常系が未網羅だったため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("編集対象の確認タスク");
		Task saved = taskService.saveTask(task);

		//------------実行 & 比較--------------------------

		mockMvc.perform(get("/tasks/{id}/edit", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(view().name("tasks/form"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("編集対象の確認タスク")));
	}

	@Test
	void 存在しないIDで編集フォームを開こうとするとエラーメッセージ付きで一覧へリダイレクトされる() throws Exception {

		//------------準備--------------------------

		long notExistId = 999999999L;

		//------------実行 & 比較--------------------------

		mockMvc.perform(get("/tasks/{id}/edit", notExistId))
				.andExpect(status().is3xxRedirection())
				.andExpect(view().name("redirect:/tasks"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.flash().attributeExists("errorMessage"));
	}

	@Test
	void タイトルが空白文字のみだとエラーになりフォームが再表示される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （@NotBlankの空白文字トリム挙動を網羅するため）

		//------------準備--------------------------

		long beforeCount = taskService.getAllTasks().size();

		//------------実行 & 比較--------------------------

		mockMvc.perform(post("/tasks")
				.param("title", "   ")
				.param("sortOrder", "0"))
				.andExpect(status().isOk())
				.andExpect(view().name("tasks/form"));

		// タスクが保存されていないこと
		assertThat(taskService.getAllTasks()).hasSize((int) beforeCount);
	}

	@Test
	void タイトルが101文字以上だとエラーになりフォームが再表示される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （@Size(max=100)の境界値超過ケースを網羅するため）

		//------------準備--------------------------

		long beforeCount = taskService.getAllTasks().size();
		String tooLongTitle = "あ".repeat(101);

		//------------実行 & 比較--------------------------

		mockMvc.perform(post("/tasks")
				.param("title", tooLongTitle)
				.param("sortOrder", "0"))
				.andExpect(status().isOk())
				.andExpect(view().name("tasks/form"));

		// タスクが保存されていないこと
		assertThat(taskService.getAllTasks()).hasSize((int) beforeCount);
	}

	@Test
	void 有効なタイトルで新規作成すると一覧画面に即座に反映される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （「追加が一覧に即座に反映される」という観点を検証する）

		//------------実行--------------------------

		// フォームからタイトルを指定して新規作成する
		mockMvc.perform(post("/tasks")
				.param("title", "新規作成即時反映確認タスク")
				.param("sortOrder", "0"))
				.andExpect(status().is3xxRedirection())
				.andExpect(view().name("redirect:/tasks"));

		//------------比較--------------------------

		// 一覧画面に新規作成したタスクのタイトルが表示されること
		mockMvc.perform(get("/tasks"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("新規作成即時反映確認タスク")));
	}

	@Test
	void 新規作成時にステータスを指定しても強制的にTODOになる() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （saveTaskの「新規作成時はTODOに強制」する分岐を網羅するため）

		//------------実行--------------------------

		// クライアント側からstatus=DONEを送っても、新規作成では無視されTODOになる想定
		mockMvc.perform(post("/tasks")
				.param("title", "ステータス強制確認タスク")
				.param("status", "DONE")
				.param("sortOrder", "0"))
				.andExpect(status().is3xxRedirection());

		//------------比較--------------------------

		Task saved = taskService.getAllTasks().stream()
				.filter(t -> "ステータス強制確認タスク".equals(t.getTitle()))
				.findFirst()
				.orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void 既存タスクを編集すると一覧画面の表示内容が即座に更新される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （「編集が一覧に即座に反映される」という観点を検証する）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("編集前タイトル");
		Task saved = taskService.saveTask(task);

		//------------実行--------------------------

		mockMvc.perform(post("/tasks")
				.param("id", String.valueOf(saved.getId()))
				.param("title", "編集後タイトル")
				.param("status", "IN_PROGRESS")
				.param("sortOrder", "0"))
				.andExpect(status().is3xxRedirection());

		//------------比較--------------------------

		// 一覧画面に編集後のタイトルが反映され、編集前のタイトルはもう表示されないこと
		mockMvc.perform(get("/tasks"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("編集後タイトル")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("編集前タイトル"))))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("is-in-progress")));
	}
}
