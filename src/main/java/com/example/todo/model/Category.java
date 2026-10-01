package com.example.todo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * タスクの分類（カテゴリ）を表すエンティティクラス。
 * <p>
 * 指示書「カテゴリ機能仕様」「データモデル定義（category テーブル）」に基づき、
 * {@code name}（必須・重複不可・20文字以内）と {@code color}（一覧表示時のラベル色）を保持する。
 * タスクは0または1個のカテゴリに属する（多対多にはしない）。
 * </p>
 */
@Entity
@Table(name = "category")
public class Category {

    // カテゴリのID（主キー）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // カテゴリ名（例：「仕事」「プライベート」「勉強」）。必須・重複不可・20文字以内。
    @NotBlank(message = "カテゴリ名は必須です")
    @Size(max = 20, message = "カテゴリ名は20文字以内で入力してください")
    @Column(nullable = false, length = 20, unique = true)
    private String name;

    // 一覧表示時のラベル色（例：#FF0000）。任意項目。
    @Column(length = 20)
    private String color;

    public Category() {
    }

    public Category(String name, String color) {
        this.name = name;
        this.color = color;
    }

    /** ID を返します。 */
    public Long getId() {
        return id;
    }

    /** ID を設定します。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** カテゴリ名を返します。 */
    public String getName() {
        return name;
    }

    /** カテゴリ名を設定します。 */
    public void setName(String name) {
        this.name = name;
    }

    /** ラベル色を返します。未設定の場合は null。 */
    public String getColor() {
        return color;
    }

    /** ラベル色を設定します。 */
    public void setColor(String color) {
        this.color = color;
    }
}
