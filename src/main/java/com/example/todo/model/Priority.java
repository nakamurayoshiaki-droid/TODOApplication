package com.example.todo.model;

/**
 * タスクの優先度を表す列挙型。
 * <p>
 * 表示用の日本語名を内部に持ち、ビュー表示などで利用します。
 * 優先度が未設定の場合は {@link #MEDIUM} が既定値として扱われます。
 * </p>
 */
public enum Priority {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低");

    // 表示用の日本語名
    private final String displayName;

    Priority(String displayName) {
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
