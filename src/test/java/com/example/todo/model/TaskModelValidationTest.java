package com.example.todo.model;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task エンティティのBean Validation制約を検証するテストクラス。
 * <p>
 * Springコンテキストを起動せず、Validatorを直接使うことで高速に実行できる。
 * </p>
 */
class TaskModelValidationTest {

	@Test
	void タイトルが空のままだとバリデーションエラーになる() {

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle(""); // タイトルを空にする

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// タイトルが空の場合、バリデーション違反が発生すること
			assertThat(violations).isNotEmpty();

			// 違反内容が「title」フィールドに関するものであること
			assertThat(violations)
					.anyMatch(v -> v.getPropertyPath().toString().equals("title"));
		}
	}

	@Test
	void タイトルが設定されていればバリデーションエラーにならない() {

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("買い物に行く");

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// title制約に関する違反が発生しないこと
			assertThat(violations)
					.noneMatch(v -> v.getPropertyPath().toString().equals("title"));
		}
	}

	@Test
	void タイトルがnullの場合もバリデーションエラーになる() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （@NotBlankのnull入力に対する境界値ケースを網羅するため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle(null); // タイトルを未設定(null)にする

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// タイトルがnullの場合も@NotBlankにより違反が発生すること
			assertThat(violations)
					.anyMatch(v -> v.getPropertyPath().toString().equals("title"));
		}
	}

	@Test
	void タイトルが空白文字のみの場合もバリデーションエラーになる() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （@NotBlankの空白文字トリム挙動を網羅するため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("   "); // 空白のみのタイトル（trimすると空文字扱い）

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// @NotBlankは空白のみの文字列も許可しないため違反が発生すること
			assertThat(violations)
					.anyMatch(v -> v.getPropertyPath().toString().equals("title"));
		}
	}

	@Test
	void タイトルが100文字ちょうどであればバリデーションエラーにならない() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （@Size(max=100)の境界値ちょうどのケースを網羅するため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("あ".repeat(100)); // 最大文字数ちょうど

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// 境界値ちょうどなのでtitleに関する違反が発生しないこと
			assertThat(violations)
					.noneMatch(v -> v.getPropertyPath().toString().equals("title"));
		}
	}

	@Test
	void タイトルが101文字以上だとバリデーションエラーになる() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （@Size(max=100)の境界値超過のケースを網羅するため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("あ".repeat(101)); // 最大文字数を1文字超過

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// @Size(max=100)により違反が発生すること
			assertThat(violations)
					.anyMatch(v -> v.getPropertyPath().toString().equals("title"));
		}
	}

	@Test
	void 詳細が2000文字を超えるとバリデーションエラーになる() {

		// ※指示書に記載のない観点：カバレッジ向上のため追加
		// （description項目の@Size(max=2000)制約が未網羅だったため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("正常なタイトル");
		task.setDescription("あ".repeat(2001)); // 最大文字数を1文字超過

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// @Size(max=2000)によりdescriptionに関する違反が発生すること
			assertThat(violations)
					.anyMatch(v -> v.getPropertyPath().toString().equals("description"));
		}
	}
}
