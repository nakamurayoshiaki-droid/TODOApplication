package com.example.todo.repository;

import com.example.todo.model.Priority;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * {@link Task} に対する検索条件（{@link Specification}）を組み立てるためのユーティリティクラス。
 * <p>
 * 指示書「フィルタリング・検索仕様」に基づき、ステータス／期限／優先度／キーワード／カテゴリの
 * 各条件をAND結合できるよう、条件ごとに独立した {@link Specification} を提供する。
 * 各メソッドは、対応するパラメータが未指定（null・空）の場合は「絞り込みなし」を意味する
 * {@code null} を返す（{@link Specification} の仕組み上、null条件はAND結合時に無視される）。
 * </p>
 */
public final class TaskSpecifications {

    // 期限フィルタ「今日まで」を表す値
    public static final String DUE_TODAY_OR_EARLIER = "TODAY_OR_EARLIER";
    // 期限フィルタ「期限切れのみ」を表す値
    public static final String DUE_OVERDUE = "OVERDUE";
    // カテゴリフィルタ「カテゴリなし」を表す値
    public static final String CATEGORY_NONE = "NONE";

    private TaskSpecifications() {
    }

    /**
     * ステータスによる絞り込み条件を返す。
     *
     * @param status 絞り込み対象のステータス（null の場合は絞り込みなし＝「すべて」）
     * @return 検索条件
     */
    public static Specification<Task> hasStatus(TaskStatus status) {
        // 三項演算子を使わず、あえてif-elseで記述している
    	//　理解出来たら改善
    	// root: エンティティのルート（Taskエンティティ）
    	// query: クエリの定義（CriteriaQuery）
    	// cb: 条件を構築するためのCriteriaBuilder
    	// 要するに、root.get("status")でTaskエンティティのstatusフィールドを取得し、cb.equalでその値が指定されたstatusと等しいかどうかを判定する条件を作成している
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            } else {
                return cb.equal(root.get("status"), status);
            }
        };
    }

    /**
     * 優先度による絞り込み条件を返す。
     *
     * @param priority 絞り込み対象の優先度（null の場合は絞り込みなし＝「すべて」）
     * @return 検索条件
     */
    public static Specification<Task> hasPriority(Priority priority) {
        // 三項演算子を使わず、あえてif-elseで記述している
    	//　理解出来たら改善
    	// root: エンティティのルート（Taskエンティティ）
    	// query: クエリの定義（CriteriaQuery）
    	// cb: 条件を構築するためのCriteriaBuilder
    	// 要するに、root.get("priority")でTaskエンティティのpriorityフィールドを取得し、cb.equalでその値が指定されたpriorityと等しいかどうかを判定する条件を作成している
        return (root, query, cb) -> {
            if (priority == null) {
                return null;
            } else {
                return cb.equal(root.get("priority"), priority);
            }
        };
    }

    /**
     * カテゴリによる絞り込み条件を返す。
     * <p>
     * {@code categoryId} が指定されている場合はそのカテゴリのタスクのみ、
     * {@code noCategory} が true の場合は「カテゴリなし」（categoryId が null）のタスクのみに絞り込む。
     * どちらも指定されない場合は絞り込みなし。
     * </p>
     *
     * @param categoryId 絞り込み対象のカテゴリID（null可）
     * @param noCategory 「カテゴリなし」のタスクのみに絞り込む場合 true
     * @return 検索条件
     */
    public static Specification<Task> hasCategory(Long categoryId, boolean noCategory) {
    	// 三項演算子を使わず、あえてif-elseで記述している
    	// root: エンティティのルート（Taskエンティティ）
    	// query: クエリの定義（CriteriaQuery）
    	// cb: 条件を構築するためのCriteriaBuilder
        return (root, query, cb) -> {
            if (noCategory) {
                return cb.isNull(root.get("categoryId"));
            }
            if (categoryId == null) {
                return null;
            }
            return cb.equal(root.get("categoryId"), categoryId);
        };
    }

    /**
     * キーワードによる絞り込み条件を返す（タイトル・詳細を対象に部分一致・大文字小文字を区別しない検索）。
     *
     * @param keyword 検索キーワード（null・空文字の場合は絞り込みなし）
     * @return 検索条件
     */
    public static Specification<Task> keywordContains(String keyword) {
    	// 三項演算子を使わず、あえてif-elseで記述している
    	// root: エンティティのルート（Taskエンティティ）
    	// query: クエリの定義（CriteriaQuery）
    	// cb: 条件を構築するためのCriteriaBuilder
    	return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("description").as(String.class), "")), pattern)
            );
        };
    }

    /**
     * 期限による絞り込み条件を返す。
     * <p>
     * {@link #DUE_TODAY_OR_EARLIER}: 期限が今日以前（今日を含む）のタスク。<br>
     * {@link #DUE_OVERDUE}: 期限切れ（期限が今日より過去、かつステータスがDONE・CANCELLED以外）のタスク。<br>
     * それ以外（null・空・"ALL"）の場合は絞り込みなし。
     * </p>
     *
     * @param due   絞り込み種別
     * @param today 判定基準日（通常は当日）
     * @return 検索条件
     */
    public static Specification<Task> dueFilter(String due, LocalDate today) {
    	// 三項演算子を使わず、あえてif-elseで記述している
    	// root: エンティティのルート（Taskエンティティ）
    	// query: クエリの定義（CriteriaQuery）
    	// cb: 条件を構築するためのCriteriaBuilder
    	return (root, query, cb) -> {
            if (due == null || due.isBlank()) {
                return null;
            }
            if (DUE_TODAY_OR_EARLIER.equalsIgnoreCase(due)) {
                return cb.and(cb.isNotNull(root.get("dueDate")), cb.lessThanOrEqualTo(root.get("dueDate"), today));
            }
            if (DUE_OVERDUE.equalsIgnoreCase(due)) {
                return cb.and(
                        cb.isNotNull(root.get("dueDate")),
                        cb.lessThan(root.get("dueDate"), today),
                        cb.notEqual(root.get("status"), TaskStatus.DONE),
                        cb.notEqual(root.get("status"), TaskStatus.CANCELLED));
            }
            return null;
        };
    }
}
