package com.example.todo.controller;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * タスクに関するREST APIを提供するコントローラクラス。
 * <p>
 * このコントローラは主にタスクの状態更新と削除を扱います。
 * ベースパスは {@code /api/tasks} です。
 * </p>
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskRestController {

    private final TaskService taskService;

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
}