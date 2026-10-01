package com.example.todo.repository;

import com.example.todo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * タスクの永続化操作を提供するリポジトリインターフェース。
 * <p>
 * Spring Data JPA を利用して、タスクの CRUD 操作を簡単に行うことができます。
 * ソート済み一覧の取得には、{@link JpaRepository#findAll(org.springframework.data.domain.Sort)}
 * を呼び出し側（{@code TaskService}）で利用します。
 * {@link JpaSpecificationExecutor} を継承することで、指示書「フィルタリング・検索仕様」の
 * 複数条件（ステータス／期限／優先度／キーワード／カテゴリ）のAND結合検索を動的に組み立てられるようにする。
 * </p>
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * 指定したカテゴリIDに紐づく全タスクの {@code categoryId} を {@code NULL} に更新する。
     * <p>
     * 指示書「カテゴリ機能仕様」の「カテゴリ削除時、そのカテゴリに属していたタスクは
     * 『カテゴリなし』（category_id = NULL）に変更する」という仕様に対応するためのメソッド。
     * </p>
     *
     * @param categoryId 削除対象のカテゴリID
     */
    @Modifying
    @Query("UPDATE Task t SET t.categoryId = NULL WHERE t.categoryId = :categoryId")
    void clearCategoryId(@Param("categoryId") Long categoryId);
}

