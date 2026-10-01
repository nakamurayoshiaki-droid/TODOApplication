package com.example.todo.service;

import com.example.todo.model.Category;
import com.example.todo.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CategoryService の単体テストクラス。
 * <p>
 * 指示書「カテゴリ機能仕様」に基づき、カテゴリ名の重複チェック・
 * 削除時のタスク側「カテゴリなし」への更新を検証する。
 * </p>
 */
@SpringBootTest
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TaskService taskService;

    // DBへの反映を1次キャッシュ経由ではなく本当に確認するためのEntityManager
    @Autowired
    private EntityManager entityManager;

    @Test
    void カテゴリを新規作成できること() {

        //------------準備--------------------------

        Category category = new Category();
        category.setName("仕事");
        category.setColor("#FF0000");

        //------------実行--------------------------

        Category saved = categoryService.saveCategory(category);

        //------------比較--------------------------

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("仕事");
        assertThat(saved.getColor()).isEqualTo("#FF0000");
    }

    @Test
    void 同名のカテゴリを作成しようとすると例外がスローされる() {

        //------------準備--------------------------

        Category first = new Category();
        first.setName("重複確認カテゴリ");
        categoryService.saveCategory(first);

        Category duplicated = new Category();
        duplicated.setName("重複確認カテゴリ");

        //------------実行 & 比較--------------------------

        assertThatThrownBy(() -> categoryService.saveCategory(duplicated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void カテゴリを削除するとそのカテゴリに属していたタスクがカテゴリなしになる() {

        //------------準備--------------------------

        Category category = new Category();
        category.setName("削除対象カテゴリ");
        Category savedCategory = categoryService.saveCategory(category);

        Task task = new Task();
        task.setTitle("カテゴリ紐付きタスク");
        task.setCategoryId(savedCategory.getId());
        Task savedTask = taskService.saveTask(task);

        //------------実行--------------------------

        categoryService.deleteCategory(savedCategory.getId());

        // 1次キャッシュに頼らず、本当にDBへ反映されたかを確認する
        entityManager.flush();
        entityManager.clear();
        Task reloaded = taskService.getTaskById(savedTask.getId()).orElseThrow();

        //------------比較--------------------------

        // カテゴリ自体が削除されていること
        assertThat(categoryService.getCategoryById(savedCategory.getId())).isEmpty();

        // タスクのcategoryIdがnull（＝カテゴリなし）になっていること
        assertThat(reloaded.getCategoryId()).isNull();
    }

    @Test
    void 既存カテゴリを自分以外と重複しない名前に更新できること() {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （saveCategoryの更新時分岐 existsByNameAndIdNot が未網羅だったため）

        //------------準備--------------------------

        Category category = new Category();
        category.setName("更新前カテゴリ名");
        Category saved = categoryService.saveCategory(category);

        entityManager.flush();
        entityManager.clear();

        //------------実行--------------------------

        Category updateRequest = new Category();
        updateRequest.setId(saved.getId());
        updateRequest.setName("更新後カテゴリ名");
        Category updated = categoryService.saveCategory(updateRequest);

        //------------比較--------------------------

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getName()).isEqualTo("更新後カテゴリ名");
    }

    @Test
    void 既存カテゴリを別カテゴリと同名に更新しようとすると例外がスローされる() {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （saveCategoryの更新時分岐 existsByNameAndIdNot のtrue側が未網羅だったため）

        //------------準備--------------------------

        Category other = new Category();
        other.setName("既存の別カテゴリ");
        categoryService.saveCategory(other);

        Category target = new Category();
        target.setName("更新対象カテゴリ");
        Category savedTarget = categoryService.saveCategory(target);

        entityManager.flush();
        entityManager.clear();

        //------------実行 & 比較--------------------------

        Category updateRequest = new Category();
        updateRequest.setId(savedTarget.getId());
        updateRequest.setName("既存の別カテゴリ");

        assertThatThrownBy(() -> categoryService.saveCategory(updateRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 既存カテゴリを自分自身と同じ名前のまま更新してもエラーにならないこと() {

        // ※指示書に記載のない観点：カバレッジ向上のため追加
        // （existsByNameAndIdNotは更新対象自身のIDを除外するため、名前を変更しない更新は
        // 重複とみなされないことを確認する）

        //------------準備--------------------------

        Category category = new Category();
        category.setName("変更なし確認カテゴリ");
        category.setColor("#111111");
        Category saved = categoryService.saveCategory(category);

        entityManager.flush();
        entityManager.clear();

        //------------実行--------------------------

        // 名前はそのまま、色だけ変更したデタッチ状態の別インスタンスを渡す
        Category updateRequest = new Category();
        updateRequest.setId(saved.getId());
        updateRequest.setName("変更なし確認カテゴリ");
        updateRequest.setColor("#222222");
        Category updated = categoryService.saveCategory(updateRequest);

        //------------比較--------------------------

        assertThat(updated.getName()).isEqualTo("変更なし確認カテゴリ");
        assertThat(updated.getColor()).isEqualTo("#222222");
    }

    @Test
    void 存在しないIDでカテゴリを取得しようとすると空のOptionalが返る() {

        //------------準備--------------------------

        long notExistId = 999999999L;

        //------------実行--------------------------

        var result = categoryService.getCategoryById(notExistId);

        //------------比較--------------------------

        assertThat(result).isEmpty();
    }
}
