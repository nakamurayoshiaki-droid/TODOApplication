package com.example.todo.model;

/**
 * タスクの繰り返し設定を表す列挙型。
 * <p>
 * 表示用の日本語名を内部に持ち、ビュー表示などで利用します。
 * 繰り返し設定が未設定の場合は {@link #NONE}（繰り返しなし）が既定値として扱われます。
 * </p>
 * <p>
 * 	{@code NONE} 以外が設定されたタスクを
 * {@link TaskStatus#DONE} にした際、次回期日を持つ新しいタスクが自動生成される
 * （次回期日の計算: {@code DAILY}=完了日の翌日 / {@code WEEKLY}=完了日の7日後 /
 * {@code MONTHLY}=完了日の1か月後）。
 * </p>
 */
public enum RecurrenceRule {
    NONE("繰り返しなし"),
    DAILY("毎日"),
    WEEKLY("毎週"),
    MONTHLY("毎月");

    // 表示用の日本語名
    private final String displayName;

    RecurrenceRule(String displayName) {
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
