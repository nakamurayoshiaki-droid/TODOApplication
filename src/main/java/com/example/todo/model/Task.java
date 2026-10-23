package com.example.todo.model;

//JPAのエンティティとしてマークするためのアノテーション
import jakarta.persistence.*;

//タイトルが空でないことを保証するバリデーションのアノテーション
import jakarta.validation.constraints.NotBlank;
//タイトルと詳細の文字数制限を設定するためのバリデーションのアノテーション
import jakarta.validation.constraints.Size;
//作成日時・更新日時・完了日時を保持するためのクラス　
import java.time.LocalDateTime;
//期限（日付のみ、時刻を持たない）を保持するためのクラス
import java.time.LocalDate;
//HTML5のinput type="date"はyyyy-MM-dd形式（ISO 8601）を要求するため、
//ロケール依存のデフォルトフォーマット（例: 環境によってはM/d/yyのような形式）にならないよう明示的に指定する
import org.springframework.format.annotation.DateTimeFormat;

@Entity
@Table(name = "task")
/**
 * タスクを表すエンティティクラス。
 * <p>
 * タイトル・詳細・ステータス・ソート順・作成/更新/完了日時などの属性を保持します。
 * </p>
 */
public class Task {

	// タスクのID（主キー）
	@Id
	// 自動生成戦略を IDENTITY に設定（データベースの自動インクリメントを使用）
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	// ID カラムは null 許容不可
	private Long id;

	// タスク名（タイトル）
	@NotBlank(message = "タスク名は必須です")
	// タイトルの最大長を 100 文字に制限
	@Size(max = 100, message = "タスク名は100文字以内で入力してください")
	// タイトルカラムは null 許容不可、長さ制限を設定
	@Column(nullable = false, length = 100)
	// タイトルのフィールド
	private String title;

	// タスクの詳細（説明）
	@Size(max = 2000, message = "詳細は2000文字以内で入力してください")
	// 詳細カラムの長さ制限を設定
	@Column(length = 2000)
	// 詳細のフィールド
	private String description;

	// タスクの状態（列挙型）
	@Enumerated(EnumType.STRING)
	// 状態カラムは null 許容不可、長さ制限を設定
	@Column(nullable = false, length = 20)
	// デフォルト値を TO/DO に設定
	private TaskStatus status = TaskStatus.TODO;

	// タスクの優先度（列挙型）
	@Enumerated(EnumType.STRING)
	// 優先度カラムは null 許容不可、長さ制限を設定
	@Column(nullable = false, length = 20)
	// デフォルト値を MEDIUM に設定
	private Priority priority = Priority.MEDIUM;

	// タスクのソート順
	@Column(name = "sort_order", nullable = false)
	// デフォルト値を 0 に設定
	private Integer sortOrder = 0;

	// タスクが属するカテゴリのID（category.id への外部キー）。
	// 「カテゴリなし」を許容するため nullable（未設定＝null が正しい値）。
	@Column(name = "category_id")
	private Long categoryId;

	// このタスクが「どの親タスクのサブタスクか」を表すID
	// 「親タスクなし（通常のトップレベルタスク）」を許容するため nullable（未設定＝null が正しい値）。
	// サブタスクは1階層までのため、このフィールドを持つタスク（＝サブタスク）に対して
	// さらに parentTaskId を設定することはバリデーションで禁止する。
	@Column(name = "parent_task_id")
	private Long parentTaskId;

	// このタスクが「繰り返しタスク」かどうか、繰り返すならどの頻度かを表す
	// デフォルト値を NONE（繰り返しなし）に設定する
	@Enumerated(EnumType.STRING)
	@Column(name = "recurrence_rule", nullable = false, length = 20)
	private RecurrenceRule recurrenceRule = RecurrenceRule.NONE;

	// このタスクが「月末締めの繰り返しタスク」かどうかを表す
	// デフォルト値を false に設定する
	@Column(name = "is_last_day_of_month")
	private Boolean isLastDayOfMonth = false;

	// サブタスクの進捗表示用フィールド（DBには保存しない）。
	// 一覧画面でトップレベルタスクを表示する際、TaskService側でサブタスクを集計して
	// この2つのフィールドに値を詰めてから返す。
	// サブタスクが0件、またはすべてCANCELLEDの場合は subtaskTotalCount が 0 になる
	// （分母0件の除算を避けるため、進捗表示が必要かどうかの判定は呼び出し側＝テンプレート/JS側で
	// 「subtaskTotalCount > 0」を見て行う想定）。
	@Transient
	private Integer subtaskDoneCount;

	@Transient
	private Integer subtaskTotalCount;

	// タスクの期限（日付のみ、時刻は持たない。未設定＝期限なしを許容するのでnullable）
	@Column(name = "due_date")

	// 重要
	// HTML5のinput type="date"はyyyy-MM-dd（ISO 8601）形式の値しか正しく認識しないため、明示的にISO形式を指定する
	// ブラウザに不正な値として扱われて欄が空に見えてしまう不具合の原因になっていた）
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	// 期限のフィールド
	private LocalDate dueDate;

	// 作成日時（自動設定）
	@Column(name = "created_at", nullable = false, updatable = false)
	// 作成日時のフィールド
	private LocalDateTime createdAt;

	// 更新日時（自動設定）
	@Column(name = "updated_at", nullable = false)
	// 更新日時のフィールド
	private LocalDateTime updatedAt;

	// 完了日時（タスクが完了した時に設定される）
	@Column(name = "completed_at")
	// 完了日時のフィールド
	private LocalDateTime completedAt;

	/**
	 * エンティティ作成時（INSERT前）に呼ばれるライフサイクルコールバック。
	 * <p>
	 * 作成日時と更新日時を現在時刻に設定し、{@code sortOrder} と {@code status} が null の場合はデフォルト値（0 /
	 * TO/DO）を補完する。
	 * </p>
	 */
	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();

		// nullチェックを行い、ソート順とステータスのデフォルト値を設定
		if (this.sortOrder == null)
			this.sortOrder = 0;
		if (this.status == null)
			this.status = TaskStatus.TODO;
		// 優先度が未設定の場合はMEDIUMを補完する
		if (this.priority == null)
			this.priority = Priority.MEDIUM;
		// 繰り返し設定が未設定の場合はNONE（繰り返しなし）を補完する
		if (this.recurrenceRule == null)
			this.recurrenceRule = RecurrenceRule.NONE;
		// 月末フラグが未設定の場合はfalseを補完する（追加）
		if (this.isLastDayOfMonth == null)
			this.isLastDayOfMonth = false;
	}

	/**
	 * エンティティ更新時（UPDATE前）に呼ばれるライフサイクルコールバック。
	 * <p>
	 * 更新日時を現在時刻に設定する。
	 * </p>
	 */
	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	/**
	 * デフォルトコンストラクタ。
	 */
	public Task() {

	}

	// -------------------getter/setter-------------------

	/** ID を返します。 */
	public Long getId() {
		return id;
	}

	/** ID を設定します。 */
	public void setId(Long id) {
		this.id = id;
	}

	/** タイトルを返します。 */
	public String getTitle() {
		return title;
	}

	/** タイトルを設定します。 */
	public void setTitle(String title) {
		this.title = title;
	}

	/** 詳細（説明）を返します。 */
	public String getDescription() {
		return description;
	}

	/** 詳細（説明）を設定します。 */
	public void setDescription(String description) {
		this.description = description;
	}

	/** ステータスを返します。 */
	public TaskStatus getStatus() {
		return status;
	}

	/** ステータスを設定します。 */
	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	/** ソート順を返します。 */
	public Integer getSortOrder() {
		return sortOrder;
	}

	/** ソート順を設定します。 */
	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	/** 優先度を返します。 */
	public Priority getPriority() {
		return priority;
	}

	/** 優先度を設定します。 */
	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	/** 期限（日付のみ）を返します。未設定の場合は null。 */
	public LocalDate getDueDate() {
		return dueDate;
	}

	/** 期限（日付のみ）を設定します。 */
	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	/** 所属カテゴリのIDを返します。未設定（カテゴリなし）の場合は null。 */
	public Long getCategoryId() {
		return categoryId;
	}

	/** 所属カテゴリのIDを設定します。カテゴリなしにする場合は null を設定します。 */
	public void setCategoryId(Long categoryId) {
		this.categoryId = categoryId;
	}

	/** 親タスクのIDを返します。親タスクなし（トップレベルタスク）の場合は null。 */
	public Long getParentTaskId() {
		return parentTaskId;
	}

	/** 親タスクのIDを設定します。親タスクなしにする場合は null を設定します。 */
	public void setParentTaskId(Long parentTaskId) {
		this.parentTaskId = parentTaskId;
	}

	/** 繰り返し設定を返します。未設定時は {@link RecurrenceRule#NONE} が補完されます。 */
	public RecurrenceRule getRecurrenceRule() {
		return recurrenceRule;
	}

	/** 繰り返し設定を設定します。 */
	public void setRecurrenceRule(RecurrenceRule recurrenceRule) {
		this.recurrenceRule = recurrenceRule;
	}

	/** サブタスクのうち完了（DONE）している件数を返します（進捗表示用。未集計の場合は null）。 */
	public Integer getSubtaskDoneCount() {
		return subtaskDoneCount;
	}

	/** サブタスクのうち完了（DONE）している件数を設定します（{@code TaskService} が集計結果を詰めるために使用）。 */
	public void setSubtaskDoneCount(Integer subtaskDoneCount) {
		this.subtaskDoneCount = subtaskDoneCount;
	}

	/** サブタスクの全件数（CANCELLEDを除く。進捗表示用。未集計の場合は null）を返します。 */
	public Integer getSubtaskTotalCount() {
		return subtaskTotalCount;
	}

	/** サブタスクの全件数（CANCELLEDを除く）を設定します（{@code TaskService} が集計結果を詰めるために使用）。 */
	public void setSubtaskTotalCount(Integer subtaskTotalCount) {
		this.subtaskTotalCount = subtaskTotalCount;
	}

	/**
	 * このタスクにサブタスクが存在するかどうかを返します（一覧画面で展開アイコンを表示するかの判定に使用）。
	 *
	 * @return {@code subtaskTotalCount} が集計済みで1件以上ある場合 true
	 */
	public boolean isHasSubtasks() {
		return subtaskTotalCount != null && subtaskTotalCount > 0;
	}

	/** 作成日時を返します。 */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/** 作成日時を設定します（通常は自動設定）。 */
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	/** 更新日時を返します。 */
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	/** 更新日時を設定します（通常は自動設定）。 */
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	/** 完了日時を返します（完了時に設定される）。 */
	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	/** 完了日時を設定します。 */
	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}

	// 繰り返しタスクの月末締め判定を返します。
	public Boolean getIsLastDayOfMonth() {
		return isLastDayOfMonth;
	}

	// 繰り返しタスクの月末締め判定を設定します。
	public void setIsLastDayOfMonth(Boolean isLastDayOfMonth) {
		this.isLastDayOfMonth = isLastDayOfMonth;
	}

	// -------------------期限切れ判定・当日期限判定-------------------

	/**
	 * 指定した基準日時点で、このタスクが「期限切れ」かどうかを判定します。
	 * <p>
	 * 期限（{@code dueDate}）が基準日より過去で、かつステータスが {@link TaskStatus#DONE} /
	 * {@link TaskStatus#CANCELLED} 以外の場合に期限切れとみなす。 期限が未設定の場合は常に false。
	 * </p>
	 *
	 * @param today 判定基準日（通常は当日）
	 * @return 期限切れの場合 true
	 */
	public boolean isOverdue(LocalDate today) {
		if (dueDate == null) {
			return false;
		}
		return dueDate.isBefore(today) && status != TaskStatus.DONE && status != TaskStatus.CANCELLED;
	}

	/**
	 * 現在日時を基準に {@link #isOverdue(LocalDate)} を判定します（Thymeleafテンプレート等から利用）。
	 *
	 * @return 期限切れの場合 true
	 */
	public boolean isOverdue() {
		return isOverdue(LocalDate.now());
	}

	/**
	 * 指定した基準日が、このタスクの期限日と一致するかどうかを判定します。
	 * <p>
	 * ステータスが {@link TaskStatus#DONE} / {@link TaskStatus#CANCELLED} の場合は
	 * 既に完了・中止しているため、当日期限の強調表示は不要と判断し false を返す。 期限が未設定の場合は常に false。
	 * </p>
	 *
	 * @param today 判定基準日（通常は当日）
	 * @return 基準日が期限日と一致する場合 true
	 */
	public boolean isDueToday(LocalDate today) {
		if (dueDate == null) {
			return false;
		}
		return dueDate.isEqual(today) && status != TaskStatus.DONE && status != TaskStatus.CANCELLED;
	}

	/**
	 * 現在日時を基準に {@link #isDueToday(LocalDate)} を判定します（Thymeleafテンプレート等から利用）。
	 *
	 * @return 当日が期限日の場合 true
	 */
	public boolean isDueToday() {
		return isDueToday(LocalDate.now());
	}
}