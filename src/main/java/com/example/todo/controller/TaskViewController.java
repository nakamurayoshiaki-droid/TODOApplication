package com.example.todo.controller;

import com.example.todo.model.Category;
import com.example.todo.model.Priority;
import com.example.todo.model.RecurrenceRule;
import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.service.CategoryService;
import com.example.todo.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * タスクのビュー関連の操作を扱うコントローラ。
 * <p>
 * ブラウザ経由のページ表示（一覧、作成フォーム、編集フォーム）およびタスク保存処理を提供します。 ベースパスは {@code /tasks} です。
 * </p>
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskViewController {

	private final TaskService taskService;
	private final CategoryService categoryService;

	/**
	 * タスク一覧ページを表示します。
	 * <p>
	 * モデルに全タスクとステータス一覧を追加し、テンプレート {@code tasks/list} を返します。
	 * </p>
	 *
	 * @param model ビューに渡すモデル
	 * @return 表示するテンプレート名
	 */
	// 引数がない場合@RequestMapping のパスがそのまま適用
	@GetMapping
	public String listTasks(Model model) {
		// モデルにタスク、ステータス、優先度、カテゴリを追加
		model.addAttribute("tasks", taskService.getAllTasks());
		model.addAttribute("statuses", TaskStatus.values());
		model.addAttribute("priorities", Priority.values());
		List<Category> categories = categoryService.getAllCategories();
		model.addAttribute("categories", categories);
		
		// カテゴリをIDで検索するためのMapを作成してモデルに追加
		//  th:each の中で外側の変数を参照する場合、選択式（フィルタ構文）では参照できないという制約がある
		//  「外側の th:each の変数を、内側で普通に参照するだけ」なら問題なし → ステータスの == 比較がこれに該当
		//  「外側の th:each の変数を、内側の選択式（フィルタ構文）の条件式の中で参照する」と問題が起きる → カテゴリを探す処理がこれに該当
		//  そのため、カテゴリだけは選択式を避けて categoryMap.get(task.categoryId) という単純な Map 参照に置き換える必要があった
		model.addAttribute("categoryMap",
				categories.stream().collect(Collectors.toMap(Category::getId, Function.identity())));

		// thymeleafのテンプレート名を返す
		return "tasks/list";
	}

	/**
	 * 新規タスク作成フォームを表示します。
	 * <p>
	 * {@code parentTaskId} が指定された場合、そのタスクのサブタスクとして作成するフォームとして
	 * 表示する（一覧画面の「＋サブタスク」ボタンから遷移してきた場合）。「親タスク」選択欄に
	 * 該当タスクが事前選択された状態になる。
	 * </p>
	 *
	 * @param parentTaskId 親タスクとして事前選択するタスクID（任意。一覧画面の「＋サブタスク」ボタンから渡される）
	 * @param model        ビューに渡すモデル（空の Task とステータス一覧を含む）
	 * @return 表示するテンプレート名
	 */
	@GetMapping("/new")
	public String newTaskForm(@RequestParam(required = false) Long parentTaskId, Model model) {
		// 期限（dueDate）はTaskエンティティ上 nullable（@NotNull等の必須制約なし）で、
		// 「期限なし」を許容する任意項目のため、新規作成フォームの初期値は敢えて設定せず空欄のままにする。
		Task task = new Task();
		if (parentTaskId != null) {
			task.setParentTaskId(parentTaskId);
			// サブタスク作成時は、親タスクのカテゴリを初期値として引き継ぐ（必要に応じて画面で変更可）
			taskService.getTaskById(parentTaskId).ifPresent(parent -> task.setCategoryId(parent.getCategoryId()));
		}
		model.addAttribute("task", task);
		model.addAttribute("statuses", TaskStatus.values());
		model.addAttribute("priorities", Priority.values());
		model.addAttribute("categories", categoryService.getAllCategories());
		model.addAttribute("recurrenceRules", RecurrenceRule.values());
		// 新規作成時は、完了済み・中止済みの親タスクを候補から除外する。
		// ただし一覧画面の「+サブタスク」から渡された親IDは事前選択状態を維持する。
		model.addAttribute("parentTaskCandidates",
				taskService.getAvailableParentTaskCandidatesForCreate(null, task.getParentTaskId()));
		return "tasks/form";
	}

	/**
	 * 指定した ID のタスク編集フォームを表示します。
	 * <p>
	 * 対象タスクが存在しない場合（例: 一覧表示後、別タブ等で既に削除されていた場合）は、
	 * 例外を画面にそのまま表示させず、エラーメッセージ付きで一覧画面へ戻す。
	 * </p>
	 *
	 * @param id                 編集対象のタスクID
	 * @param model              ビューに渡すモデル（対象 Task とステータス一覧を含む）
	 * @param redirectAttributes 対象が見つからない場合に、一覧画面へエラーメッセージを渡すためのフラッシュ属性
	 * @return 表示するテンプレート名、対象が存在しない場合は一覧へのリダイレクト
	 */
	@GetMapping("/{id}/edit")
	public String editTaskForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
		return taskService.getTaskById(id).map(task -> {
			model.addAttribute("task", task);
			model.addAttribute("statuses", TaskStatus.values());
			model.addAttribute("priorities", Priority.values());
			model.addAttribute("categories", categoryService.getAllCategories());
			model.addAttribute("recurrenceRules", RecurrenceRule.values());
			// 編集時も完了済み・中止済みの親タスクは候補から除外する。
			// ただし現在すでに紐づいている親タスクは表示を維持する。
			model.addAttribute("parentTaskCandidates",
					taskService.getAvailableParentTaskCandidatesForEdit(id, task.getParentTaskId()));
			return "tasks/form";
		}).orElseGet(() -> {
			// 編集リンクを開いた時点で対象タスクが既に削除されていた場合は、
			// Whitelabel Error Page（500）を表示せず、一覧画面へエラーメッセージ付きでリダイレクトする
			redirectAttributes.addFlashAttribute("errorMessage", "対象のタスクは既に削除されているため、編集できません。");
			return "redirect:/tasks";
		});
	}

	/**
	 * フォームから送信されたタスクを保存します。
	 * <p>
	 * バリデーションエラーがある場合はフォームを再表示し、成功時は一覧へリダイレクトします。
	 * </p>
	 *
	 * @param task               バインドされる Task オブジェクト（@Valid により検証される）
	 * @param result             バリデーション結果
	 * @param model              ビューに渡すモデル（エラー時にステータス一覧を再設定するために使用）
	 * @param redirectAttributes 一覧画面へのリダイレクト後にエラーメッセージを一度だけ表示するためのフラッシュ属性
	 * @return 成功時はリダイレクト先、エラー時はフォームのテンプレート名
	 */
	@PostMapping
	public String saveTask(@Valid @ModelAttribute("task") Task task, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		// バリデーションエラーがある場合はフォームを再表示
		if (result.hasErrors()) {
			model.addAttribute("statuses", TaskStatus.values());
			model.addAttribute("priorities", Priority.values());
			model.addAttribute("categories", categoryService.getAllCategories());
			model.addAttribute("recurrenceRules", RecurrenceRule.values());
			model.addAttribute("parentTaskCandidates",
					task.getId() == null
							? taskService.getAvailableParentTaskCandidatesForCreate(null, task.getParentTaskId())
							: taskService.getAvailableParentTaskCandidatesForEdit(task.getId(), task.getParentTaskId()));
			return "tasks/form";
		}

		// 新規作成時はクライアント側が何らかの手段でステータスを選択しても強制的に未着手(TO/DO)にする
		if (task.getId() == null) {
			task.setStatus(TaskStatus.TODO);
		}

		try {
			// タスクを保存
			taskService.saveTask(task);
		} catch (IllegalArgumentException e) {
			// 更新対象のタスクが既に削除されていた場合は、従来どおりエラーメッセージ付きで一覧画面へ戻す
			// （TaskService#saveTaskがfindById(...).orElseThrow(...)で投げる「Task not found: 」で始まる例外）
			if (e.getMessage() != null && e.getMessage().startsWith("Task not found")) {
				redirectAttributes.addFlashAttribute("errorMessage", "対象のタスクは既に削除されているため、保存できませんでした。");
				return "redirect:/tasks";
			}
			// 親タスク関連のバリデーションエラー（1階層制限違反・親タスク不在など）は、
			// 一覧へ飛ばさずフォームを再表示し、入力内容を保持したままエラー内容を伝える
			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("statuses", TaskStatus.values());
			model.addAttribute("priorities", Priority.values());
			model.addAttribute("categories", categoryService.getAllCategories());
			model.addAttribute("recurrenceRules", RecurrenceRule.values());
			model.addAttribute("parentTaskCandidates",
					task.getId() == null
							? taskService.getAvailableParentTaskCandidatesForCreate(null, task.getParentTaskId())
							: taskService.getAvailableParentTaskCandidatesForEdit(task.getId(), task.getParentTaskId()));
			return "tasks/form";
		}

		// 保存後はタスク一覧ページにリダイレクト
		return "redirect:/tasks";
	}
}