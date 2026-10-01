package com.example.todo.controller;

import com.example.todo.model.Category;
import com.example.todo.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CategoryViewController の画面遷移・保存処理を検証するテストクラス。
 * <p>
 * 指示書「カテゴリ機能仕様」に基づき、カテゴリの新規作成・削除が
 * 実際にDBへ正しく反映されるかをMockMvc経由で確認する。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void GETでカテゴリ一覧画面が表示できること() throws Exception {

        // ------------実行 & 比較--------------------------

        mockMvc.perform(get("/categories")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("カテゴリ管理")));
    }

    @Test
    void POSTでカテゴリを新規作成すると一覧に反映される() throws Exception {

        // ------------実行--------------------------

        mockMvc.perform(post("/categories").param("name", "登録確認カテゴリ").param("color", "#00FF00"))
                .andExpect(status().is3xxRedirection());

        // ------------比較--------------------------

        entityManager.flush();
        entityManager.clear();
        assertThat(categoryService.getAllCategories())
                .anyMatch(c -> c.getName().equals("登録確認カテゴリ"));
    }

    @Test
    void POSTで名前が空のカテゴリを作成しようとするとエラーになり保存されない() throws Exception {

        // ------------実行--------------------------

        mockMvc.perform(post("/categories").param("name", "").param("color", "#00FF00"))
                .andExpect(status().isOk());

        // ------------比較--------------------------

        entityManager.flush();
        entityManager.clear();
        assertThat(categoryService.getAllCategories()).noneMatch(c -> c.getName() == null || c.getName().isEmpty());
    }

    @Test
    void POSTでカテゴリを削除するとそのカテゴリがDBから削除される() throws Exception {

        // ※テスト名・観点の重複整理：
        // 「削除時にタスクがカテゴリなしになる」という業務ロジック自体は
        // CategoryServiceTest#カテゴリを削除するとそのカテゴリに属していたタスクがカテゴリなしになる()
        // で既に検証済みのため、本テストではコントローラ経由（POST /categories/{id}/delete）で
        // カテゴリそのものが削除されることのみを確認する（旧テスト名は実装内容と乖離していたため修正）。

        // ------------準備--------------------------

        Category category = new Category();
        category.setName("削除確認カテゴリ");
        Category saved = categoryService.saveCategory(category);

        // ------------実行--------------------------

        mockMvc.perform(post("/categories/{id}/delete", saved.getId()))
                .andExpect(status().is3xxRedirection());

        // ------------比較--------------------------

        entityManager.flush();
        entityManager.clear();
        assertThat(categoryService.getCategoryById(saved.getId())).isEmpty();
    }

    @Test
    void POSTで既存カテゴリと同名のカテゴリを作成しようとするとエラーメッセージ付きで一覧が再表示される() throws Exception {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （saveCategoryの名前重複によるIllegalArgumentExceptionハンドリング分岐を網羅する）

        // ------------準備--------------------------

        Category category = new Category();
        category.setName("重複確認カテゴリ");
        categoryService.saveCategory(category);
        long beforeCount = categoryService.getAllCategories().size();

        // ------------実行--------------------------

        // 同じ名前で再度作成を試みる（リダイレクトされず一覧画面がそのまま返る）
        mockMvc.perform(post("/categories").param("name", "重複確認カテゴリ").param("color", "#00FF00"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("同名のカテゴリが既に存在します")));

        // ------------比較--------------------------

        // 重複したカテゴリが追加保存されていない（件数が増えていない）こと
        entityManager.flush();
        entityManager.clear();
        assertThat(categoryService.getAllCategories()).hasSize((int) beforeCount);
    }
}
