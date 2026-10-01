package com.example.todo.repository;

import com.example.todo.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * カテゴリの永続化操作を提供するリポジトリインターフェース。
 * <p>
 * Spring Data JPA を利用して、カテゴリの CRUD 操作を行う。
 * </p>
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 指定した名前のカテゴリが既に存在するかどうかを返す（重複チェック用）。
     *
     * @param name カテゴリ名
     * @return 存在する場合 true
     */
    boolean existsByName(String name);

    /**
     * 指定した名前のカテゴリが、指定したID以外に存在するかどうかを返す（更新時の重複チェック用）。
     *
     * @param name        カテゴリ名
     * @param excludedId  重複チェックの対象から除外するID（更新対象自身）
     * @return 存在する場合 true
     */
    boolean existsByNameAndIdNot(String name, Long excludedId);
}
