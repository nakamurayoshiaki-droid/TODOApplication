package com.example.todo.repository;

import com.example.todo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * タスクの永続化操作を提供するリポジトリインターフェース。
 * <p>
 * Spring Data JPA を利用して、タスクの CRUD 操作を簡単に行うことができます。
 * ソート済み一覧の取得には、{@link JpaRepository#findAll(org.springframework.data.domain.Sort)}
 * を呼び出し側（{@code TaskService}）で利用します。
 * </p>
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
}