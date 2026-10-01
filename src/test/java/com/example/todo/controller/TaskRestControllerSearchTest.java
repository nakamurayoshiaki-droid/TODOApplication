package com.example.todo.controller;

import com.example.todo.model.Category;
import com.example.todo.model.Priority;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.service.CategoryService;
import com.example.todo.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskRestController の検索API（GET /api/tasks）・並び替えAPI（PUT /api/tasks/reorder）を検証するテストクラス。
 * <p>
 * 指示書「フィルタリング・検索仕様」「並び替え仕様」に基づき、複数条件のAND結合検索と
 * 並び順の永続化が正しく機能するかをMockMvc経由で確認する。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskRestControllerSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ステータスで絞り込むと該当するタスクのみ返る() throws Exception {

        //------------準備--------------------------

        Task todo = new Task();
        todo.setTitle("検索確認_未着手タスク");
        taskService.saveTask(todo);

        Task inProgress = new Task();
        inProgress.setTitle("検索確認_進行中タスク");
        Task savedInProgress = taskService.saveTask(inProgress);
        taskService.updateStatus(savedInProgress.getId(), TaskStatus.IN_PROGRESS);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("検索確認_進行中タスク")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("検索確認_未着手タスク"))));
    }

    @Test
    void キーワード検索でタイトル_詳細の部分一致が大文字小文字を区別せず絞り込まれる() throws Exception {

        //------------準備--------------------------

        Task task = new Task();
        task.setTitle("Keyword確認タスク");
        task.setDescription("検索対象のメモ");
        taskService.saveTask(task);

        Task other = new Task();
        other.setTitle("無関係タスク");
        taskService.saveTask(other);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("keyword", "keyword"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("Keyword確認タスク")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("無関係タスク"))));
    }

    @Test
    void 期限切れのみで絞り込むと期限切れタスクのみ返る() throws Exception {

        //------------準備--------------------------

        Task overdue = new Task();
        overdue.setTitle("期限切れ確認タスク");
        overdue.setDueDate(LocalDate.now().minusDays(1));
        taskService.saveTask(overdue);

        Task future = new Task();
        future.setTitle("期限先確認タスク");
        future.setDueDate(LocalDate.now().plusDays(5));
        taskService.saveTask(future);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("due", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("期限切れ確認タスク")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("期限先確認タスク"))));
    }

    @Test
    void カテゴリで絞り込むと該当タスクのみ返る() throws Exception {

        //------------準備--------------------------

        Category category = new Category();
        category.setName("検索確認カテゴリ");
        Category savedCategory = categoryService.saveCategory(category);

        Task categorized = new Task();
        categorized.setTitle("カテゴリ絞込確認タスク");
        categorized.setCategoryId(savedCategory.getId());
        taskService.saveTask(categorized);

        Task uncategorized = new Task();
        uncategorized.setTitle("カテゴリなし確認タスク");
        taskService.saveTask(uncategorized);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("category", String.valueOf(savedCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("カテゴリ絞込確認タスク")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("カテゴリなし確認タスク"))));
    }

    @Test
    void 優先度と組み合わせた複数条件がAND結合で正しく絞り込まれる() throws Exception {

        //------------準備--------------------------

        Task matched = new Task();
        matched.setTitle("複合条件確認タスクA");
        matched.setPriority(Priority.HIGH);
        Task savedMatched = taskService.saveTask(matched);
        taskService.updateStatus(savedMatched.getId(), TaskStatus.IN_PROGRESS);

        Task wrongPriority = new Task();
        wrongPriority.setTitle("複合条件確認タスクB");
        wrongPriority.setPriority(Priority.LOW);
        Task savedWrongPriority = taskService.saveTask(wrongPriority);
        taskService.updateStatus(savedWrongPriority.getId(), TaskStatus.IN_PROGRESS);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("status", "IN_PROGRESS").param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("複合条件確認タスクA")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("複合条件確認タスクB"))));
    }

    @Test
    void カテゴリなしで絞り込むとカテゴリ未設定タスクのみ返る() throws Exception {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （TaskSpecifications#hasCategoryのnoCategory=true分岐が未網羅だったため）

        //------------準備--------------------------

        Category category = new Category();
        category.setName("カテゴリなし確認用カテゴリ");
        Category savedCategory = categoryService.saveCategory(category);

        Task categorized = new Task();
        categorized.setTitle("カテゴリあり確認タスク");
        categorized.setCategoryId(savedCategory.getId());
        taskService.saveTask(categorized);

        Task uncategorized = new Task();
        uncategorized.setTitle("カテゴリなし絞込確認タスク");
        taskService.saveTask(uncategorized);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("category", "NONE"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("カテゴリなし絞込確認タスク")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("カテゴリあり確認タスク"))));
    }

    @Test
    void 期限今日以前で絞り込むと今日と過去のタスクのみ返る() throws Exception {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （TaskSpecifications#dueFilterのDUE_TODAY_OR_EARLIER分岐が未網羅だったため）

        //------------準備--------------------------

        Task today = new Task();
        today.setTitle("期限今日確認タスク");
        today.setDueDate(LocalDate.now());
        taskService.saveTask(today);

        Task future = new Task();
        future.setTitle("期限今日以前_対象外タスク");
        future.setDueDate(LocalDate.now().plusDays(5));
        taskService.saveTask(future);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("due", "TODAY_OR_EARLIER"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("期限今日確認タスク")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("期限今日以前_対象外タスク"))));
    }

    @Test
    void 期限フィルタに未知の値を指定すると絞り込まれずに全件返る() throws Exception {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （TaskSpecifications#dueFilterの「TODAY_OR_EARLIER/OVERDUEどちらにも
        // 一致しない非空文字列（例:"ALL"）の場合にnullを返す」分岐が未網羅だったため）

        //------------準備--------------------------

        Task task = new Task();
        task.setTitle("期限フィルタ未知値確認タスク");
        task.setDueDate(LocalDate.now().plusDays(10));
        taskService.saveTask(task);

        //------------実行 & 比較--------------------------

        mockMvc.perform(get("/api/tasks").param("due", "ALL"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("期限フィルタ未知値確認タスク")));
    }

    @Test
    void 不正なステータス値で検索すると400が返る() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 不正な優先度値で検索すると400が返る() throws Exception {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （searchTasksの優先度パラメータに対する不正値ハンドリング分岐を網羅する）

        mockMvc.perform(get("/api/tasks").param("priority", "UNKNOWN_PRIORITY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 数値でもNONEでもない不正なカテゴリ値で検索すると400が返る() throws Exception {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （searchTasksのカテゴリパラメータに対するNumberFormatExceptionハンドリング分岐を網羅する）

        mockMvc.perform(get("/api/tasks").param("category", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PUTで並び替え後の順序がsortOrderとしてDBへ永続化される() throws Exception {

        //------------準備--------------------------

        Task first = new Task();
        first.setTitle("並び替え確認タスクA");
        Task savedFirst = taskService.saveTask(first);

        Task second = new Task();
        second.setTitle("並び替え確認タスクB");
        Task savedSecond = taskService.saveTask(second);

        // 現在はA→Bの順（sortOrderは両方とも初期値0だが、作成日時昇順でA→B）。
        // 並び替え後はB→Aの順に変更する。
        List<Long> newOrder = List.of(savedSecond.getId(), savedFirst.getId());

        //------------実行--------------------------

        mockMvc.perform(put("/api/tasks/reorder")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("taskIds", newOrder))))
                .andExpect(status().isOk());

        // 1次キャッシュに頼らず、本当にDBへ反映されたかを確認する
        entityManager.flush();
        entityManager.clear();

        //------------比較--------------------------

        Task reloadedFirst = taskService.getTaskById(savedFirst.getId()).orElseThrow();
        Task reloadedSecond = taskService.getTaskById(savedSecond.getId()).orElseThrow();

        // Bのほうが先（sortOrderが小さい）になっていること
        assertThat(reloadedSecond.getSortOrder()).isLessThan(reloadedFirst.getSortOrder());
    }

    @Test
    void PUTでtaskIdsが未指定の場合400が返る() throws Exception {
        mockMvc.perform(put("/api/tasks/reorder")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
