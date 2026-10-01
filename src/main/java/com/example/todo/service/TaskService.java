package com.example.todo.service;

import com.example.todo.model.Priority;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
     * 指示書「フィルタリング・検索仕様」に基づき、複数条件（AND結合）でタスクを検索する。
     * <p>
     * 各条件は未指定（null・空文字・"ALL"相当）の場合は絞り込まれない。
     * 結果は一覧表示と同じ順序（ソート順・作成日時の昇順）で返す。
     * </p>
     *
     * @param status     絞り込み対象のステータス（null可）
     * @param due        期限フィルタ種別（{@link TaskSpecifications#DUE_TODAY_OR_EARLIER} 等、null・空可）
     * @param priority   絞り込み対象の優先度（null可）
     * @param keyword    タイトル・詳細に対する部分一致キーワード（null・空可）
     * @param categoryId 絞り込み対象のカテゴリID（null可）
     * @param noCategory 「カテゴリなし」のタスクのみに絞り込む場合 true
     * @return 条件に合致するタスクのリスト
     */
    @Transactional(readOnly = true)
    public List<Task> searchTasks(TaskStatus status, String due, Priority priority, String keyword,
            Long categoryId, boolean noCategory) {

    	//　Specificationは検索条件を表現するためのインターフェース
    	// SQLのWHERE句にあたる条件を、Javaのコードとして組み立てられるようにする仕組み
    	//　動的な検索条件（条件が来たり来なかったりする）」を、きれいに組み合わせられるようにする
    	// 条件に応じて作っていたメソッドをここだけに集約できる。
        Specification<Task> spec = Specification.where(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.hasPriority(priority))
                .and(TaskSpecifications.hasCategory(categoryId, noCategory))
                .and(TaskSpecifications.keywordContains(keyword))
                .and(TaskSpecifications.dueFilter(due, LocalDate.now()));

        Sort sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"));
        return taskRepository.findAll(spec, sort);
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
    	// findByIdはOptionalを返すので、存在しない場合の処理を簡潔に書ける
    	// orElseThrowを使って、タスクが存在しない場合にIllegalArgumentExceptionをスローする
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
     * <p>
     * {@link org.springframework.data.jpa.repository.JpaRepository#deleteById} は、
     * Spring Data JPAの実装（{@code SimpleJpaRepository#deleteById}）上
     * {@code findById(id).ifPresent(this::delete)} となっており、対象が存在しない場合は
     * 例外を投げず何もしないため、追加のガード処理なしでそのまま上記の仕様を満たす。
     * </p>
     *
     * @param id 削除対象のタスクID
     */
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    /**
     * 指示書「並び替え仕様」に基づき、ドラッグ＆ドロップ後のタスクの並び順を一括で更新・永続化する。
     * <p>
     * 渡されたタスクIDの並び順どおりに、先頭から {@code 0, 1, 2, ...} を {@code sortOrder} として設定する。
     * 存在しないタスクIDが含まれていた場合は無視する（一覧取得後、別タブ等で削除された場合を考慮）。
     * </p>
     *
     * @param orderedTaskIds ドロップ後の並び順どおりのタスクIDリスト
     */
    public void reorderTasks(List<Long> orderedTaskIds) {
        int order = 0;
        for (Long taskId : orderedTaskIds) {
            Optional<Task> taskOpt = taskRepository.findById(taskId);
            if (taskOpt.isEmpty()) {
                // 一覧取得後に削除されていた場合等は無視して続行する
                order++;
                continue;
            }
            Task task = taskOpt.get();
            task.setSortOrder(order);
            taskRepository.save(task);
            order++;
        }
    }
}