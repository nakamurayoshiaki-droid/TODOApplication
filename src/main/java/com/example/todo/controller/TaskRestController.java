package com.example.todo.controller;

import com.example.todo.model.Priority;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.repository.TaskSpecifications;
import com.example.todo.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * タスクに関するREST APIを提供するコントローラクラス。
 * <p>
 * このコントローラは主にタスクの検索・状態更新・削除を扱います。
 * ベースパスは {@code /api/tasks} です。
 * </p>
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskRestController {

    private final TaskService taskService;

    /**
     * 指示書「フィルタリング・検索仕様」に基づき、複数条件（AND結合）でタスクを検索するエンドポイント。
     * <p>
     * 全てのパラメータは任意で、未指定または空文字の場合はその条件で絞り込まない（「すべて」扱い）。
     * 一覧画面（{@code list.html}）からのAjax呼び出し専用で、画面全体を再読み込みせずに
     * 検索結果を再描画するために使用する。
     * </p>
     *
     * @param status   ステータス（{@link TaskStatus} の name。例: "TO_DO"）
     * @param due      期限フィルタ（"TODAY_OR_EARLIER"＝今日まで／"OVERDUE"＝期限切れのみ／未指定＝すべて）
     * @param priority 優先度（{@link Priority} の name。例: "HIGH"）
     * @param keyword  タイトル・詳細に対する部分一致キーワード
     * @param category カテゴリ（カテゴリIDの数値文字列／"NONE"＝カテゴリなし／未指定＝すべて）
     * @return 条件に合致するタスクの配列（JSON）、または不正なパラメータの場合は400
     */
    @GetMapping
    public ResponseEntity<?> searchTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String due,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {

        // パラメータのバリデーションと変換
        TaskStatus statusEnum;
        try {
            if (status == null || status.isBlank()) {
                statusEnum = null;
            } else {
                statusEnum = TaskStatus.valueOf(status);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("不正なステータス値です: " + status);
        }

        Priority priorityEnum;
        try {
            if (priority == null || priority.isBlank()) {
                priorityEnum = null;
            } else {
                priorityEnum = Priority.valueOf(priority);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("不正な優先度値です: " + priority);
        }

        Long categoryId = null;
        boolean noCategory = false;
        if (category != null && !category.isBlank()) {
            if (TaskSpecifications.CATEGORY_NONE.equalsIgnoreCase(category)) {
                noCategory = true;
            } else {
                try {
                    categoryId = Long.valueOf(category);
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body("不正なカテゴリ値です: " + category);
                }
            }
        }

        List<Task> tasks = taskService.searchTasks(statusEnum, due, priorityEnum, keyword, categoryId, noCategory);
        return ResponseEntity.ok(tasks);
    }

    /**
     * タスクの状態を更新するエンドポイント。
     * <p>
     * リクエストはパスパラメータでタスクIDを受け取り、リクエストボディのJSONに含まれる
     * {@code status} フィールドを使って状態を更新します。例: {@code {"status":"COMPLETED"}}
     * </p>
     *
     * @param id      更新対象のタスクID
     * @param payload リクエストボディ（キー: "status"）
     * @return 更新後の Task を含む HTTP 200 レスポンス、または不正なリクエストの場合は
     *         理由を含む HTTP 400 レスポンス（{@code status} が未指定、または未定義のステータス値の場合）
     * @throws IllegalArgumentException {@code payload} に不正な状態文字列が含まれる場合
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String statusValue = payload.get("status");
        // statusキーが存在しない（null）場合は、TaskStatus.valueOf(null)がNullPointerExceptionを
        // 投げてしまい下のcatchで捕捉できないため、事前にチェックして400を返す
        if (statusValue == null) {
            return ResponseEntity.badRequest().body("statusは必須です");
        }
        TaskStatus status;
        try {
            status = TaskStatus.valueOf(statusValue);
        } catch (IllegalArgumentException e) {
            // 未定義のステータス値（enumに存在しない文字列）が送信された場合は、
            // 原因が分かるメッセージ付きで400 Bad Requestを返す
            return ResponseEntity.badRequest().body("不正なステータス値です: " + statusValue);
        }
        Task updated = taskService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    /**
     * タスクを削除するエンドポイント。
     *
     * @param id 削除対象のタスクID
     * @return 空の HTTP 200 レスポンス（削除成功時）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 指示書「並び替え仕様」に基づき、ドラッグ＆ドロップ後の並び順をまとめて受け取り、永続化するエンドポイント。
     * <p>
     * リクエストボディには、ドロップ後の並び順どおりのタスクIDの配列を
     * {@code {"taskIds": [3, 1, 2, ...]}} の形式で渡す。
     * </p>
     *
     * @param payload リクエストボディ（キー: "taskIds"）
     * @return 更新成功時は空の HTTP 200 レスポンス、{@code taskIds} が未指定の場合は 400
     */
    @PutMapping("/reorder")
    public ResponseEntity<?> reorderTasks(@RequestBody Map<String, List<Long>> payload) {
        List<Long> taskIds = payload.get("taskIds");
        if (taskIds == null) {
            return ResponseEntity.badRequest().body("taskIdsは必須です");
        }
        taskService.reorderTasks(taskIds);
        return ResponseEntity.ok().build();
    }
}