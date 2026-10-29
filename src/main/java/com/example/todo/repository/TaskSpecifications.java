package com.example.todo.repository;

import com.example.todo.model.Priority;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * {@link Task} に対する検索条件（{@link Specification}）を組み立てるためのユーティリティクラス。
 * <p>
 * ステータス／期限／優先度／キーワード／カテゴリの 各条件をAND結合できるよう、条件ごとに独立した {@link Specification}
 * を提供する。 各メソッドは、対応するパラメータが未指定（null・空）の場合は「絞り込みなし」を意味する {@code null}
 * を返す（{@link Specification} の仕組み上、null条件はAND結合時に無視される）。
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
		// root.get("status")でTaskエンティティのstatusフィールドを取得し、cb.equalで判定
		return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
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
		return (root, query, cb) -> (due == null || due.isBlank()) ? null :
				DUE_TODAY_OR_EARLIER.equalsIgnoreCase(due) ? cb.and(cb.isNotNull(root.get("dueDate")), cb.lessThanOrEqualTo(root.get("dueDate"), today)) :
				DUE_OVERDUE.equalsIgnoreCase(due) ? cb.and(cb.isNotNull(root.get("dueDate")), cb.lessThan(root.get("dueDate"), today), cb.notEqual(root.get("status"), TaskStatus.DONE), cb.notEqual(root.get("status"), TaskStatus.CANCELLED)) :
				null;
	}

	/**
	 * 優先度による絞り込み条件を返す。
	 *
	 * @param priority 絞り込み対象の優先度（null の場合は絞り込みなし＝「すべて」）
	 * @return 検索条件
	 */
	public static Specification<Task> hasPriority(Priority priority) {
		// root.get("priority")でTaskエンティティのpriorityフィールドを取得し、cb.equalで判定
		return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
	}

	/**
	 * カテゴリによる絞り込み条件を返す。
	 * <p>
	 * {@code categoryId} が指定されている場合はそのカテゴリのタスクのみ、 {@code noCategory} が true
	 * の場合は「カテゴリなし」（categoryId が null）のタスクのみに絞り込む。 どちらも指定されない場合は絞り込みなし。
	 * </p>
	 *
	 * @param categoryId 絞り込み対象のカテゴリID（null可）
	 * @param noCategory 「カテゴリなし」のタスクのみに絞り込む場合 true
	 * @return 検索条件
	 */
	public static Specification<Task> hasCategory(Long categoryId, boolean noCategory) {
		return (root, query, cb) -> noCategory ? cb.isNull(root.get("categoryId")) : 
									(categoryId == null ? null : cb.equal(root.get("categoryId"), categoryId));
	}

	/**
	 * キーワードによる絞り込み条件を返す（タイトル・詳細を対象に部分一致・大文字小文字を区別しない検索）。
	 *
	 * @param keyword 検索キーワード（null・空文字の場合は絞り込みなし）
	 * @return 検索条件
	 */
	public static Specification<Task> keywordContains(String keyword) {
		return (root, query, cb) -> (keyword == null || keyword.isBlank()) ? null :
				cb.or(
						cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"),
						cb.like(cb.lower(cb.coalesce(root.get("description").as(String.class), "")), "%" + keyword.toLowerCase() + "%")
				);
	}

	/**
	 * キーワードによる絞り込み条件を返す。
	 * <p>
	 * トップレベルタスク自身のタイトル・詳細に加えて、そのタスクに紐づく「サブタスク」の
	 * タイトル・詳細がキーワードにマッチする場合も、親タスク（＝自身）をヒットさせる。
	 * これにより、キーワードがサブタスクのみに一致した場合でも、親タスクが一覧から
	 * 消えてしまわないようにする（サブタスク自体はトップレベル一覧の行としては表示しない仕様の
	 * ままで、あくまで「親タスクを探す手がかり」として扱う）。
	 * </p>
	 * <p>
	 * サブタスクとのマッチ判定は、JPA Criteriaの相関サブクエリ（{@code EXISTS}句）で
	 * 「{@code parentTaskId = (自身のid)} かつ キーワードに一致する」タスクが1件でも
	 * 存在するかどうかを判定する形で実現する。
	 * </p>
	 *
	 * @param keyword 検索キーワード（null・空文字の場合は絞り込みなし）
	 * @return 検索条件
	 */
	public static Specification<Task> keywordContainsIncludingSubtasks(String keyword) {
		return (root, query, cb) -> {
			if (keyword == null || keyword.isBlank()) {
				return null;
			}
			String pattern = "%" + keyword.toLowerCase() + "%";

			// 自分自身（トップレベルタスク）のタイトル・詳細がキーワードにマッチする条件
			Predicate selfMatches = cb.or(cb.like(cb.lower(root.get("title")), pattern),
					cb.like(cb.lower(cb.coalesce(root.get("description").as(String.class), "")), pattern));

			// 相関サブクエリ: 「parentTaskIdが自分（root）のid」かつ「キーワードにマッチする」
			// サブタスクが1件でも存在するか（EXISTS）を判定する
			Subquery<Long> subquery = query.subquery(Long.class);
			Root<Task> subtaskRoot = subquery.from(Task.class);
			Predicate subtaskMatches = cb.or(cb.like(cb.lower(subtaskRoot.get("title")), pattern),
					cb.like(cb.lower(cb.coalesce(subtaskRoot.get("description").as(String.class), "")), pattern));
			subquery.select(subtaskRoot.get("id"))
					.where(cb.equal(subtaskRoot.get("parentTaskId"), root.get("id")), subtaskMatches);

			return cb.or(selfMatches, cb.exists(subquery));
		};
	}


	/**
	 * トップレベルタスク（サブタスクではないタスク）に絞り込む条件を返す。
	 * <p>
	 * 一覧表示・検索・並び替えの対象は{@code parentTaskId} が {@code null}
	 * のタスク（トップレベルタスク）のみに限定する必要がある （サブタスクは親タスク行の展開アイコンから個別に取得するため、トップレベル一覧には含めない）。
	 * </p>
	 *
	 * @return 検索条件（常に {@code parentTaskId IS NULL} を表す。絞り込みなしを意味するnullは返さない）
	 */
	public static Specification<Task> isTopLevel() {
		return (root, query, cb) -> cb.isNull(root.get("parentTaskId"));
	}
}