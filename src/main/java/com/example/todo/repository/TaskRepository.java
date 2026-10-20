package com.example.todo.repository;

import com.example.todo.model.Task;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * タスクの永続化操作を提供するリポジトリインターフェース。
 * <p>
 * Spring Data JPA を利用して、タスクの CRUD 操作を簡単に行うことができます。
 * ソート済み一覧の取得には、{@link JpaRepository#findAll(org.springframework.data.domain.Sort)}
 * を呼び出し側（{@code TaskService}）で利用します。
 * {@link JpaSpecificationExecutor} を継承することで、
 * 複数条件（ステータス／期限／優先度／キーワード／カテゴリ）のAND結合検索を動的に組み立てられるようにする。
 * </p>
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * 指定したカテゴリIDに紐づく全タスクの {@code categoryId} を {@code NULL} に更新する。
     * <p>
     * 「カテゴリ削除時、そのカテゴリに属していたタスクは
     * 『カテゴリなし』（category_id = NULL）に変更する」という仕様に対応するためのメソッド。
     * </p>
     *
     * @param categoryId 削除対象のカテゴリID
     */
    @Modifying
    @Query("UPDATE Task t SET t.categoryId = NULL WHERE t.categoryId = :categoryId")
    void clearCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 指定した親タスクIDに紐づくサブタスクを、指定したソート順で取得する。
     * <p>
     * 存在しない親IDを指定した場合は空リストを返す（親の存在確認は {@code TaskService} 側で行う）。
     * </p>
     *
     * @param parentTaskId 親タスクのID
     * @param sort         ソート条件
     * @return サブタスクのリスト
     */
    List<Task> findByParentTaskId(Long parentTaskId, Sort sort);

    /**
     * 指定した複数の親タスクIDのいずれかに紐づくサブタスクを一括取得する。
     * <p>
     * 一覧画面でトップレベルタスクの進捗（DONE件数／全件数）をまとめて計算する際、
     * タスク1件ごとに問い合わせる（N+1）のを避けるために使用する。
     * </p>
     *
     * @param parentTaskIds 親タスクIDのリスト
     * @return サブタスクのリスト（ソート順は不定。呼び出し側で集計のみに使う）
     */
    List<Task> findByParentTaskIdIn(List<Long> parentTaskIds);

    /**
     * 指定した親タスクIDに紐づく全サブタスクを一括削除する。
     * <p>
     * 「親タスクを削除した場合、紐づくサブタスクも一括削除する」仕様に対応する。
     * 親タスク自体の削除（{@code deleteById}）とは別に、事前にこのメソッドを呼び出す想定。
     * 対象が0件の場合も例外にならず何もしない。
     * </p>
     *
     * @param parentTaskId 親タスクのID
     */
    @Modifying
    @Query("DELETE FROM Task t WHERE t.parentTaskId = :parentTaskId")
    void deleteByParentTaskId(@Param("parentTaskId") Long parentTaskId);
}

