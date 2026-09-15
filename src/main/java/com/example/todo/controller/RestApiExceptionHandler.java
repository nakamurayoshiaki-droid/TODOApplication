package com.example.todo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link TaskRestController} 用の例外ハンドラ。
 * <p>
 * 存在しないタスクIDに対する操作（ステータス更新・削除）で発生する
 * {@link IllegalArgumentException} を捕捉し、素の500エラーではなく
 * REST APIとして適切な 404 Not Found を返す仕様とする。
 * </p>
 */
@RestControllerAdvice(assignableTypes = TaskRestController.class)
public class RestApiExceptionHandler {

    /**
     * 指定IDのタスクが存在しない場合に発生する {@link IllegalArgumentException} を
     * 404 Not Found のレスポンスに変換する。
     *
     * @param e 発生した例外
     * @return エラーメッセージを含む 404 レスポンス
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
