package com.example.todo.service;

import com.example.todo.model.Category;
import com.example.todo.repository.CategoryRepository;
import com.example.todo.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * カテゴリに関するビジネスロジックを提供するサービスクラス。
 * <p>
 * 指示書「カテゴリ機能仕様」に基づき、カテゴリの取得・作成・削除を行う。
 * カテゴリ名の重複チェックはDBの一意制約に任せず、事前にサービス層で行うことで、
 * 素の500エラーではなく分かりやすいバリデーションエラーとして呼び出し側へ伝える。
 * </p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;

    /**
     * 全カテゴリを名前の昇順で取得する（読み取り専用）。
     *
     * @return カテゴリのリスト
     */
    // 指示書に明記のない並び順だったため仕様書作成者に確認済み
    // カテゴリにもsort_orderを持たせて昇順表示するのが正式な設計だが、そこまでせず名前の昇順（50音順）でもOK」とのことで、現状の実装（名前昇順）のまま確定。
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll(org.springframework.data.domain.Sort.by("name").ascending());
    }

    /**
     * 指定IDのカテゴリを取得する（読み取り専用）。
     *
     * @param id カテゴリID
     * @return Optional に包まれた Category
     */
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    /**
     * カテゴリを新規作成する。
     * <p>
     * 名前が既存のカテゴリと重複する場合は {@link IllegalArgumentException} をスローする
     * （指示書「name: 必須（重複不可、上限20文字）」に対応）。
     * </p>
     *
     * @param category 保存対象のカテゴリ
     * @return 保存後のカテゴリ
     * @throws IllegalArgumentException カテゴリ名が既に使用されている場合
     */
    public Category saveCategory(Category category) {        
        boolean duplicated;
        if (category.getId() == null) {
            // 新規作成 → 単純にその名前が既に使われていないか確認
            duplicated = categoryRepository.existsByName(category.getName());
        } else {
            // 更新 → 自分以外に同じ名前のカテゴリがないか確認（自分自身は除外）
            duplicated = categoryRepository.existsByNameAndIdNot(category.getName(), category.getId());
        }

        if (duplicated) {
            throw new IllegalArgumentException("同名のカテゴリが既に存在します: " + category.getName());
        }

        return categoryRepository.save(category);
    }

    /**
     * カテゴリを削除する。
     * <p>
     * 削除前に、そのカテゴリに属していた全タスクの {@code categoryId} を {@code NULL}
     * （＝「カテゴリなし」）に更新してから削除する。
     * </p>
     * <p>
     * 指定IDのカテゴリが既に存在しない場合（例: 別タブ等で既に削除済み）も何もせず正常終了する。
     * {@code TaskService#deleteTask} と同じ「削除対象が無い場合も削除成功時と同様の挙動とする」方針に合わせたもの。
     * {@code CategoryRepository#deleteById}（{@code SimpleJpaRepository#deleteById}）は
     * {@code findById(id).ifPresent(this::delete)} という実装のため、対象が存在しない場合も
     * 例外を投げず何もしないので、追加のガード処理なしでこの方針を満たす。
     * </p>
     *
     * @param id 削除対象のカテゴリID
     */
    public void deleteCategory(Long id) {
        taskRepository.clearCategoryId(id);
        categoryRepository.deleteById(id);
    }
}
