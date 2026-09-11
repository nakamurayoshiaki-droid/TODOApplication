package com.example.todo.model;

//JPAのエンティティとしてマークするためのアノテーション
import jakarta.persistence.*; 

//タイトルが空でないことを保証するバリデーションのアノテーション
import jakarta.validation.constraints.NotBlank; 
//タイトルと詳細の文字数制限を設定するためのバリデーションのアノテーション
import jakarta.validation.constraints.Size; 
//作成日時・更新日時・完了日時を保持するためのクラス　
import java.time.LocalDateTime; 

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

    // タスクのソート順
    @Column(name = "sort_order", nullable = false)
    // デフォルト値を 0 に設定
    private Integer sortOrder = 0;

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
     * 作成日時と更新日時を現在時刻に設定し、{@code sortOrder} と {@code status} が
     * null の場合はデフォルト値（0 / TO/DO）を補完する。
     * </p>
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // nullチェックを行い、ソート順とステータスのデフォルト値を設定
        if (this.sortOrder == null) this.sortOrder = 0;
        if (this.status == null) this.status = TaskStatus.TODO;
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

    //-------------------getter/setter-------------------
    
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
}