package com.example.todo.service;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * タスクに関するビジネスロジックを提供するサービスクラス。
 * <p>
 * このクラスはタスクの取得、保存、状態更新、削除などの操作を行います。
 * </p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    /**
     * 全タスクをソート順・作成日時の昇順で取得します（読み取り専用トランザクション）。
     *
     * @return タスクのリスト
     */
    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        // ソート条件を作成（sortOrderの昇順、次にcreatedAtの昇順）
    	Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
        
        // タスクをソート順・作成日時の昇順で取得して返す
        return taskRepository.findAll(sort);
    }

    /**
     * 指定 ID のタスクを取得します（読み取り専用）。
     *
     * @param id タスクID
     * @return Optional に包まれた Task
     */
    @Transactional(readOnly = true)
    public Optional<Task> getTaskById(Long id) {
    	
    	// タスクをIDで取得し、Optionalで返す
        return taskRepository.findById(id);
    }

    /**
     * タスクを保存します（新規作成／更新）。
     * <p>
     * 更新対象のタスクIDが指定されている場合、対象タスクが存在するか確認し、存在しない場合は例外をスローします。
     * これは、編集中に対象タスクが別タブ等で既に削除されていた場合に、
     * そのタスクが保存操作によって復活してしまう挙動を防ぐための仕様です。
     * </p>
     *
     * @param task 保存対象のタスク
     * @return 保存後のタスク
     * @throws IllegalArgumentException 更新対象のタスクIDが指定されているが、既に削除されている場合
     */
    public Task saveTask(Task task) {

        // idが指定されている（＝更新のつもり）場合、対象がまだ存在するか確認する
        if (task.getId() != null && !taskRepository.existsById(task.getId())) {
            throw new IllegalArgumentException("Task not found: " + task.getId());
        }

    	// タスクを保存（新規作成または更新）し、保存後のタスクを返す
        return taskRepository.save(task);
    }

    /**
     * タスクの状態を更新します。状態が DONE の場合は完了日時を設定し、それ以外では完了日時をクリアします。
     *
     * @param id     更新対象のタスクID
     * @param status 設定するステータス
     * @return 更新後のタスク
     * @throws IllegalArgumentException 指定IDのタスクが存在しない場合
     */
    public Task updateStatus(Long id, TaskStatus status) {
    	
    	// タスクをIDで取得し、存在しない場合は例外をスロー
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        task.setStatus(status);
        
        // ステータスが DONE の場合は完了日時を設定し、それ以外の場合は完了日時をクリア
        if (status == TaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }
        
        // タスクを保存して更新後の状態を返す
        return taskRepository.save(task);
    }

    /**
     * タスクを削除します。
     * <p>
     * 指定IDのタスクが存在しない場合は何もせず正常終了します。
     * 存在する、しないが判別できてしまうと脆弱性になるので、そのままとする。
     * </p>
     *
     * @param id 削除対象のタスクID
     */
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }
}