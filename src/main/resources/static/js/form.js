// ============================================================
// タスク入力フォーム制御スクリプト (task-form.js)
// ============================================================

$(function () {

    /** @type {JQuery<HTMLSelectElement>} 親タスク選択セレクトボックス */
    const $parentTaskSelect = $('#parentTaskId');

    /** @type {JQuery<HTMLSelectElement>} 繰り返し設定セレクトボックス */
    const $recurrenceSelect = $('#recurrenceRule');

    /** @type {JQuery<HTMLInputElement>} 毎月最終日チェックボックス */
    const $lastDayCheckbox = $('#isLastDayOfMonth');

    /** @type {JQuery<HTMLElement>} 毎月最終日オプションエリア */
    const $lastDayOptionGroup = $('#lastDayOptionGroup');

    /** @type {JQuery<HTMLInputElement>} 期日入力欄 */
    const $dueDateInput = $('#dueDate');

    /** @type {JQuery<HTMLElement>} 繰り返し非活性ヒントメッセージ */
    const $recurrenceDisabledHint = $('#recurrenceDisabledHint');

    /** @type {JQuery<HTMLInputElement>} タスクID（新規/編集の判定用） */
    const $taskIdInput = $('input[name="id"]');

    /** 新規作成フラグ（IDが空または未設定の場合に新規と判定） */
    const isNewTask = !$taskIdInput.val();

    // ------------------------------------------------------------
    // ヘルパー関数定義
    // ------------------------------------------------------------

    /**
     * 毎月最終日オプションの表示／非表示および活性／非活性状態を切り替える。
     */
    function toggleLastDayOption() {
        if ($lastDayOptionGroup.length === 0 || $lastDayCheckbox.length === 0) {
            return;
        }
        const hasParent = $parentTaskSelect.val() !== '';
        const shouldShow = !hasParent && $recurrenceSelect.val() === 'MONTHLY';

        $lastDayOptionGroup.toggle(shouldShow);
        $lastDayCheckbox.prop('disabled', !shouldShow);

        if (!shouldShow) {
            $lastDayCheckbox.prop('checked', false);
        }
    }

    /**
     * 親タスク選択状態に応じて、繰り返し設定欄の活性／非活性を切り替える。
     * 親タスク選択時（サブタスク作成時）は繰り返し設定を不可（NONE固定）にする。
     */
    function toggleRecurrenceDisabled() {
        const hasParent = $parentTaskSelect.val() !== '';

        $recurrenceSelect.prop('disabled', hasParent);
        $recurrenceSelect.toggleClass('form-control-disabled-emphasis', hasParent);

        if ($recurrenceDisabledHint.length > 0) {
            $recurrenceDisabledHint.toggle(hasParent);
        }

        if (hasParent) {
            $recurrenceSelect.val('NONE');
        }

        toggleLastDayOption();
    }

    /**
     * Date オブジェクトを "yyyy-MM-dd" 形式の ISO 日付文字列へ変換する。
     * @param {Date} date 変換対象の Date オブジェクト
     * @returns {string} フォーマット済み日付文字列
     */
    function toIsoDateString(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return y + '-' + m + '-' + d;
    }

    /**
     * 選択された繰り返しルールに基づき、推奨されるデフォルト期日を算出する。
     * @param {string} rule 繰り返しルール（DAILY, WEEKLY, MONTHLYなど）
     * @param {boolean} isLastDay 毎月最終日フラグ
     * @returns {Date|null} 算出されたデフォルト期日
     */
    function computeDefaultDueDate(rule, isLastDay) {
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        if (rule === 'DAILY') {
            const d = new Date(today);
            d.setDate(d.getDate() + 1);
            return d;
        }
        if (rule === 'WEEKLY') {
            const d = new Date(today);
            d.setDate(d.getDate() + 7);
            return d;
        }
        if (rule === 'MONTHLY') {
            if (isLastDay) {
                // 毎月ON＝当月末（今月の最終日）
                return new Date(today.getFullYear(), today.getMonth() + 1, 0);
            }
            // 毎月OFF＝来月同日
            const d = new Date(today);
            d.setMonth(d.getMonth() + 1);
            return d;
        }
        return null;
    }

    /**
     * 繰り返しルールに応じたデフォルト期日を期日入力欄へ適用する。
     */
    function applyDueDateDefault() {
        if ($dueDateInput.length === 0 || $recurrenceSelect.prop('disabled')) {
            return;
        }
        const rule = $recurrenceSelect.val();
        if (rule === 'NONE') {
            return;
        }
        const isLastDay = $lastDayCheckbox.prop('checked');
        const defaultDate = computeDefaultDueDate(rule, isLastDay);
        if (defaultDate) {
            $dueDateInput.val(toIsoDateString(defaultDate));
        }
    }

    // ------------------------------------------------------------
    // イベントリスナーの登録と初期化実行
    // ------------------------------------------------------------

    // 親タスク変更時に繰り返し設定の可否を連動させる
    $parentTaskSelect.on('change', toggleRecurrenceDisabled);

    // 繰り返しルール変更時に期日の自動計算を行う
    $recurrenceSelect.on('change', function () {
        toggleLastDayOption();
        applyDueDateDefault();
    });

    // 毎月最終日チェックボックス変更時に期日の自動計算を行う
    if ($lastDayCheckbox.length > 0) {
        $lastDayCheckbox.on('change', applyDueDateDefault);
    }

    // 期日入力欄のUX改善制御（手入力不可 ＆ クリック時にカレンダーを開く ＆ 過去日選択不可）
    if ($dueDateInput.length > 0) {
        // キーボードでの直接手入力を無効化（カレンダーピッカーからの選択のみに限定する）
        $dueDateInput.on('keydown keypress', function (e) {
            e.preventDefault();
        });

        // 入力エリア全体のクリックでカレンダーピッカーを開く
        $dueDateInput.on('click', function () {
            if (typeof this.showPicker === 'function') {
                try {
                    this.showPicker();
                } catch (e) {
                    // ブラウザ非対応時のフォールバック処理
                }
            }
        });

        // 新規作成・編集を問わず、常に過去日（今日より前の日付）の選択を禁止する
        $dueDateInput.attr('min', toIsoDateString(new Date()));
    }

    // 初期表示時に親タスクの選択状態と繰り返し設定の表示状態を同期
    toggleRecurrenceDisabled();
});