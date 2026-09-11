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

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskRestController の REST API を検証するテストクラス。
 * <p>
 * 指示書「フェーズ1」に基づき、ステータス更新API・削除APIが 実際にDBへ正しく反映されるかをMockMvc経由で確認する。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskRestControllerTest {

	// MockMvcを@Autowiredで注入することで、Spring Bootのテスト環境でHTTPリクエストをシミュレートできる
	@Autowired
	private MockMvc mockMvc;

	// TaskServiceを@Autowiredで注入することで、タスクの保存や取得などの操作を行える
	@Autowired
	private TaskService taskService;

	// EntityManagerを@Autowiredで注入することで、DBの状態を直接確認できる
	@Autowired
	private EntityManager entityManager;

	@Test
	void PATCHでステータスをDONEに変更するとDBのstatusとcompletedAtが更新される() throws Exception {

		// ------------準備--------------------------

		Task task = new Task();
		// タイトルを設定
		task.setTitle("API経由のテストタスク");
		// タスクを保存し、保存後のタスクを取得
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		/*
		 * MockMvcを使ってPATCHリクエストを送信し、タスクのステータスをDONEに変更する。
		 * リクエストのContent-Typeはapplication/jsonで、リクエストボディには{"status":"DONE"}を含める。
		 * レスポンスのステータスコードが200 OKであることを期待する。
		 */
		mockMvc.perform(patch("/api/tasks/{id}/status", saved.getId()).contentType("application/json")
				.content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

		// 1次キャッシュに頼らず、本当にDBへ反映されたかを確認する
		entityManager.flush();
		entityManager.clear();
		// DBから再取得して比較する
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();

		// ------------比較--------------------------

		// ステータスがDONEに更新されていることを確認する
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.DONE);

		// 完了日時が設定されていることを確認する
		assertThat(reloaded.getCompletedAt()).isNotNull();
	}

	@Test
	void DELETEでタスクを削除すると実際にデータベースから削除される() throws Exception {

		// ------------準備--------------------------

		Task task = new Task();
		// タイトルを設定
		task.setTitle("API経由の削除対象タスク");
		// タスクを保存し、保存後のタスクを取得
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		// MockMvcを使ってDELETEリクエストを送信し、タスクを削除する。
		mockMvc.perform(delete("/api/tasks/{id}", saved.getId())).andExpect(status().isOk());

		// 1次キャッシュに頼らず、本当にDBから消えたかを確認する
		entityManager.flush();
		entityManager.clear();

		// ------------比較--------------------------

		assertThat(taskService.getTaskById(saved.getId())).isEmpty();
	}

	@Test
	void PATCHで不正なステータス値を送信すると400が返りDBの状態は変更されない() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （updateStatusの不正値ハンドリング分岐を網羅する）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("不正ステータス確認タスク");
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		mockMvc.perform(patch("/api/tasks/{id}/status", saved.getId()).contentType("application/json")
				.content("{\"status\":\"UNKNOWN_STATUS\"}")).andExpect(status().isBadRequest());

		// ------------比較--------------------------

		// ステータスが変更されず、初期値TODOのままであること
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void PATCHでstatusキーが存在しないリクエストボディを送信すると400が返りDBの状態は変更されない() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （payload.get("status")がnullの場合、TaskStatus.valueOf(null)はIllegalArgumentExceptionではなく
		// NullPointerExceptionを投げるため、catch節では捕捉できず意図せず500になる不具合を防止する分岐を検証する）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("statusキー欠落確認タスク");
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		// statusキーを含まない空のJSONオブジェクトを送信する
		mockMvc.perform(patch("/api/tasks/{id}/status", saved.getId())
				.contentType("application/json")
				.content("{}"))
				.andExpect(status().isBadRequest());

		// ------------比較--------------------------

		// ステータスが変更されず、初期値TODOのままであること
		entityManager.flush();
		entityManager.clear();
		Task reloaded = taskService.getTaskById(saved.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void PATCHで存在しないIDのステータスを更新しようとすると404が返る() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （updateStatusの存在しないIDに対する異常系分岐を網羅する）

		// ------------準備--------------------------

		long notExistId = 999999999L;

		// ------------実行 & 比較--------------------------

		// RestApiExceptionHandlerによりIllegalArgumentExceptionが404に変換されること
		mockMvc.perform(patch("/api/tasks/{id}/status", notExistId).contentType("application/json")
				.content("{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
	}

	@Test
	void DELETEで存在しないIDを削除しようとしても例外にならず200が返る() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （Spring Data JPAのdeleteByIdは対象が存在しなくても例外を投げ無い仕様。
		//　それが変更されていない事を念のため確認する。
		

		// ------------準備--------------------------

		long notExistId = 999999999L;

		// ------------実行 & 比較--------------------------

		mockMvc.perform(delete("/api/tasks/{id}", notExistId)).andExpect(status().isOk());
	}

	@Test
	void DELETE後に一覧画面から削除されたタスクの表示が即座に消える() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （「削除が一覧に即座に反映される」という観点を検証する）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("削除即時反映確認タスク");
		Task saved = taskService.saveTask(task);

		// 削除前は一覧に表示されていることを確認
		mockMvc.perform(get("/tasks")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("削除即時反映確認タスク")));

		// ------------実行--------------------------

		mockMvc.perform(delete("/api/tasks/{id}", saved.getId())).andExpect(status().isOk());

		// ------------比較--------------------------

		// 削除後は一覧に表示されなくなること
		mockMvc.perform(get("/tasks")).andExpect(status().isOk()).andExpect(
				content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("削除即時反映確認タスク"))));
	}

	@Test
	void PATCHでステータスを変更すると一覧画面の表示に即座に反映される() throws Exception {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （「ステータス変更が一覧に即座に反映される」という観点を検証する）

		// ------------準備--------------------------

		Task task = new Task();
		task.setTitle("ステータス即時反映確認タスク");
		Task saved = taskService.saveTask(task);

		// ------------実行--------------------------

		mockMvc.perform(patch("/api/tasks/{id}/status", saved.getId()).contentType("application/json")
				.content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

		// ------------比較--------------------------

		// 一覧画面にis-doneクラスが付与されること
		mockMvc.perform(get("/tasks")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("is-done")));
	}
}
