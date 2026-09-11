package com.example.todo.controller;

import com.example.todo.model.Task;
import com.example.todo.model.TaskStatus;
import com.example.todo.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * タスクのビュー関連の操作を扱うコントローラ。
 * <p>
 * ブラウザ経由のページ表示（一覧、作成フォーム、編集フォーム）およびタスク保存処理を提供します。
 * ベースパスは {@code /tasks} です。
 * </p>
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskViewController {

    private final TaskService taskService;

    /**
     * タスク一覧ページを表示します。
     * <p>
     * モデルに全タスクとステータス一覧を追加し、テンプレート {@code tasks/list} を返します。
     * </p>
     *
     * @param model ビューに渡すモデル
     * @return 表示するテンプレート名
     */
    //引数がない場合@RequestMapping のパスがそのまま適用
    @GetMapping
    public String listTasks(Model model) {
    	// モデルに全タスクとステータス一覧を追加
    	// フロント側にtasks、statusesという名前でtaskService.getAllTasks()の戻り値とTaskStatus.values()の戻り値を渡す
        model.addAttribute("tasks", taskService.getAllTasks());
        model.addAttribute("statuses", TaskStatus.values());
        
        //　thymeleafのテンプレート名を返す
        return "tasks/list";
    }

    /**
     * 新規タスク作成フォームを表示します。
     *
     * @param model ビューに渡すモデル（空の Task とステータス一覧を含む）
     * @return 表示するテンプレート名
     */
    @GetMapping("/new")
    public String newTaskForm(Model model) {
        model.addAttribute("task", new Task());
        model.addAttribute("statuses", TaskStatus.values());
        return "tasks/form";
    }

    /**
     * 指定した ID のタスク編集フォームを表示します。
     * <p>
     * 対象タスクが存在しない場合（例: 一覧表示後、別タブ等で既に削除されていた場合）は、
     * 例外を画面にそのまま表示させず、エラーメッセージ付きで一覧画面へ戻す。
     * </p>
     *
     * @param id                  編集対象のタスクID
     * @param model               ビューに渡すモデル（対象 Task とステータス一覧を含む）
     * @param redirectAttributes  対象が見つからない場合に、一覧画面へエラーメッセージを渡すためのフラッシュ属性
     * @return 表示するテンプレート名、対象が存在しない場合は一覧へのリダイレクト
     */
    @GetMapping("/{id}/edit")
    public String editTaskForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return taskService.getTaskById(id)
                .map(task -> {
                    model.addAttribute("task", task);
                    model.addAttribute("statuses", TaskStatus.values());
                    return "tasks/form";
                })
                .orElseGet(() -> {
                    // TODO: レビュー時に確認
                    // 「編集リンクを開いた時点で対象タスクが既に削除されていた場合」の挙動は指示書で未規定。
                    // 元々は例外をそのまま投げてWhitelabel Error Page(500)を表示させていたが、
                    // ここでは一覧画面へエラーメッセージ付きでリダイレクトする方針に独自変更している。要確認。
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "対象のタスクは既に削除されているため、編集できません。");
                    return "redirect:/tasks";
                });
    }

    /**
     * フォームから送信されたタスクを保存します。
     * <p>
     * バリデーションエラーがある場合はフォームを再表示し、成功時は一覧へリダイレクトします。
     * </p>
     *
     * @param task   バインドされる Task オブジェクト（@Valid により検証される）
     * @param result バリデーション結果
     * @param model  ビューに渡すモデル（エラー時にステータス一覧を再設定するために使用）
     * @param redirectAttributes 一覧画面へのリダイレクト後にエラーメッセージを一度だけ表示するためのフラッシュ属性
     * @return 成功時はリダイレクト先、エラー時はフォームのテンプレート名
     */
    @PostMapping
    public String saveTask(@Valid @ModelAttribute("task") Task task, BindingResult result, Model model,
            RedirectAttributes redirectAttributes) {
        
    	// バリデーションエラーがある場合はフォームを再表示
    	if (result.hasErrors()) {
            model.addAttribute("statuses", TaskStatus.values());
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
            // TODO: レビュー時に確認
            // 「保存しようとした対象タスクが、既に削除されていた場合」の挙動は指示書で未規定。
            // ここではエラーメッセージ付きで一覧画面へ戻す方針を独自に採用している。要確認。
            redirectAttributes.addFlashAttribute("errorMessage", "対象のタスクは既に削除されているため、保存できませんでした。");
            return "redirect:/tasks";
        }
        
        // 保存後はタスク一覧ページにリダイレクト
        return "redirect:/tasks";
    }
}