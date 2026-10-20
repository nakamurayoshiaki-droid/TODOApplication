package com.example.todo.model;

import java.util.Set;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
 * <p>
 * {@code @TestMethodOrder} と {@code @Order} により、
 * デフォルトのハッシュベースの実行順ではなく、
 * ソースコード上の記述順でテストが実行されるようにしている。
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskModelValidationTest {

	@Test
	@Order(1)
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
	@Order(2)
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
	@Order(3)
	void タイトルがnullの場合もバリデーションエラーになる() {

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
	@Order(4)
	void タイトルが空白文字のみの場合もバリデーションエラーになる() {

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
	@Order(5)
	void タイトルが100文字ちょうどであればバリデーションエラーにならない() {

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
	@Order(6)
	void タイトルが101文字以上だとバリデーションエラーになる() {

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
	@Order(7)
	void 詳細が2000文字ちょうどであればバリデーションエラーにならない() {

		// （description項目の@Size(max=2000)制約の境界値ちょうどのケースを網羅するため）

		//------------準備--------------------------

		Task task = new Task();
		task.setTitle("正常なタイトル");
		task.setDescription("あ".repeat(2000)); // 最大文字数ちょうど

		//------------実行--------------------------

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			Set<ConstraintViolation<Task>> violations = validator.validate(task);

			//------------比較--------------------------

			// 境界値ちょうどなのでdescriptionに関する違反が発生しないこと
			assertThat(violations)
					.noneMatch(v -> v.getPropertyPath().toString().equals("description"));
		}
	}

	@Test
	@Order(8)
	void 詳細が2000文字を超えるとバリデーションエラーになる() {

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
