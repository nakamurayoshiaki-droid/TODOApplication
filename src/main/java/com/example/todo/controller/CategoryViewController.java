package com.example.todo.controller;

import com.example.todo.model.Category;
import com.example.todo.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * カテゴリのビュー関連の操作を扱うコントローラ。
 * <p>
 * 指示書「カテゴリ機能仕様」の「カテゴリの新規作成・削除ができる管理画面」に対応する、
 * 簡易的なCRUD画面（一覧表示・新規作成・削除）を提供する。ベースパスは {@code /categories}。
 * </p>
 */
@Controller
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryViewController {

    private final CategoryService categoryService;

    /**
     * カテゴリ管理画面（一覧＋新規作成フォーム）を表示する。
     *
     * @param model ビューに渡すモデル
     * @return 表示するテンプレート名
     */
    @GetMapping
    public String listCategories(Model model) {
    	// カテゴリ一覧を取得してモデルに追加
        model.addAttribute("categories", categoryService.getAllCategories());
        // 新規作成フォーム用の空の Category オブジェクトをモデルに追加（存在しない場合のみ）
        if (!model.containsAttribute("category")) {
            model.addAttribute("category", new Category());
        }
        // カテゴリ一覧画面のテンプレート名を返す
        return "categories/list";
    }

    /**
     * カテゴリを新規作成する。
     * <p>
     * バリデーションエラー、または名前が重複している場合はエラーメッセージ付きで
     * 一覧画面を再表示する。
     * </p>
     *
     * @param category           バインドされる Category オブジェクト
     * @param result             バリデーション結果
     * @param model              ビューに渡すモデル
     * @param redirectAttributes 一覧画面再表示時のフラッシュ属性
     * @return リダイレクト先
     */
    @PostMapping
    public String saveCategory(@Valid @ModelAttribute("category") Category category, BindingResult result,
            Model model, RedirectAttributes redirectAttributes) {
    	// バリデーションエラーがある場合は、カテゴリ一覧を再表示する
        if (result.hasErrors()) {
        	// カテゴリ一覧を取得してモデルに追加
            model.addAttribute("categories", categoryService.getAllCategories());
            return "categories/list";
        }

        // カテゴリを保存する際に、名前の重複などで IllegalArgumentException が発生する可能性があるため、try-catch で処理する
        try {
            categoryService.saveCategory(category);
        } catch (IllegalArgumentException e) {
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("errorMessage", e.getMessage());
            return "categories/list";
        }

        // 保存成功時は、フラッシュ属性に成功メッセージを追加して一覧画面へリダイレクトする
        redirectAttributes.addFlashAttribute("successMessage", "カテゴリを追加しました。");
        return "redirect:/categories";
    }

    /**
     * カテゴリを削除する。
     * <p>
     * 削除時、そのカテゴリに属していたタスクは「カテゴリなし」に変更される
     * （{@link CategoryService#deleteCategory(Long)} を参照）。
     * 対象カテゴリが既に削除されていた場合（例: 別タブ等で先に削除された場合）も、
     * {@code TaskRestController#deleteTask} と同様に、削除成功時と同じ挙動（成功メッセージ付きで一覧へ）とする。
     * </p>
     *
     * @param id                 削除対象のカテゴリID
     * @param redirectAttributes 一覧画面へのフラッシュ属性
     * @return 一覧画面へのリダイレクト
     */
    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.deleteCategory(id);
        
        // 削除成功時は、フラッシュ属性に成功メッセージを追加して一覧画面へリダイレクトする
        redirectAttributes.addFlashAttribute("successMessage", "カテゴリを削除しました。");
        return "redirect:/categories";
    }
}
