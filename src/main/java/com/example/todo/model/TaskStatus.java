package com.example.todo.model;

/**
 * タスクの状態を表す列挙型。
 * <p>
 * 表示用の日本語名を内部に持ち、ビュー表示などで利用します。
 * </p>
 */
public enum TaskStatus {
    TODO("未着手"),
    IN_PROGRESS("進行中"),
    ON_HOLD("保留"),
    DONE("完了"),
    CANCELLED("中止");

	// 表示用の日本語名
    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 表示用の日本語名を返します。
     *
     * @return 表示名
     */
    public String getDisplayName() {
        return displayName;
    }
}