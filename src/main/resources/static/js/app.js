$(function () {

    // ------------------------------------------------------------
    // 初期化
    // ------------------------------------------------------------

    // 初期表示時に、フィルタ条件（初期状態は「すべて」）でタスク一覧を取得し直す。
    // サーバー側で描画済みのSSR内容はそのままフォールバック表示として利用し、
    // Ajaxが正常に動作すればすぐに同じ内容（＋フィルタ機能）に置き換わる。
    reloadTaskList();

    // ------------------------------------------------------------
    // フィルタ・検索
    // ------------------------------------------------------------

    // プルダウン変更時は即座に再取得する
    $('#filterStatus, #filterDue, #filterPriority, #filterCategory').on('change', function () {
        reloadTaskList();
    });

    // キーワード入力時は、連続入力での過剰なリクエストを防ぐため少し待ってから再取得する（デバウンス）
    let keywordTimer = null;
    $('#filterKeyword').on('input', function () {
        clearTimeout(keywordTimer);
        keywordTimer = setTimeout(reloadTaskList, 300);
    });

    // ------------------------------------------------------------
    // ステータス変更（イベント委譲：Ajaxで再描画された行にも同じ処理が効くようにする）
    // ------------------------------------------------------------

	//　ステータス変更時の処理。親タスク・サブタスクの連動完了確認ダイアログ表示や、Ajax通信によるステータス更新を行う。
    $('#taskTableBody').on('change', '.status-select', function () {
		
		// 変更対象のセレクトボックス要素を取得
		// これにより、$(this)を何度も書かずに済む
		// 例えば、$(this).data('task-id')のように、セレクトボックスのdata属性を取得する際に使用する
        const select = $(this);
		
		// 変更対象のタスクIDを取得
		// data属性に埋め込まれた task-id を取得する
		// 例えば、タスクIDが123の場合は taskId には 123 が入る
        const taskId = select.data('task-id');
		
		// 変更後のステータス値を取得
		// select.val()で選択された値を取得する
		// 例えば、DONEに変更された場合は newStatus には 'DONE' が入る
        const newStatus = select.val();
		
		// 変更前のステータス値を取得
		// data属性に埋め込まれた status を取得する
		// 例えば、変更前のステータスが IN_PROGRESS の場合は oldStatus には 'IN_PROGRESS' が入る
        const oldStatus = select.data('status');
		
		// 変更対象のタスクがサブタスクを持つ親タスクかどうかを判定
		// data属性に埋め込まれた has-subtasks を取得し、true/falseに変換する
		// 例えば、サブタスクを持つ場合は hasSubtasks には true が入る
        const hasSubtasks = select.data('has-subtasks') === true || select.data('has-subtasks') === 'true';
		
		// 変更対象の行（tr要素）を取得
		// select.closest('tr')で、セレクトボックスを包んでいる親のtr要素（行全体）を取得する
		// 例えば、タスクIDが123の親タスクの場合は row に <tr id="task-row-123">...</tr> のDOM要素が入る
		// （※サブタスクの場合は、そのサブタスク自身のtr要素が入る）
        const row = select.closest('tr');
		
		// 変更対象の行がサブタスク行かどうかを判定
		// row.hasClass('subtask-row')で、tr要素にsubtask-rowクラスが付与されているかを判定する
		// 例えば、サブタスク行の場合は isSubtaskRow には true が入る
        const isSubtaskRow = row.hasClass('subtask-row');

		
        // 親タスク（サブタスクを持つタスク）をDONEにする場合は、全サブタスクも連動してDONEになることを事前に確認する。
		// DONEかつサブタスクを持つ親タスクの場合、確認ダイアログを表示する。
        if (newStatus === 'DONE' && hasSubtasks) {
			
			// 確認ポップアップダイアログを表示し、ユーザーがキャンセルした場合はステータス変更を取り消す。
            if (!confirm('このタスクを完了にすると、紐づく全てのサブタスクも完了になります。よろしいですか？')) {
				// ステータス変更を取り消すため、セレクトボックスの値を元に戻す
                select.val(oldStatus);
                return;
            }
        }

        // サブタスク行をDONEにする場合、これで兄弟サブタスクが全てDONE（CANCELLEDを除く）になるなら、
        // 親タスクも自動的にDONEになることを事前に確認する。
        // 実際の連動処理はサーバー側＝TaskService#updateStatusが行うため、ここでは画面上に既に
        // 展開表示されている兄弟サブタスク行のデータから「これで全部揃うか」のみを事前判定する）。
		
		// 兄弟サブタスクのうち、すでにDONEまたはCANCELLEDのものを除いた残りのサブタスクが、今回の変更で全てDONEになる場合、確認ダイアログを表示する。
        if (newStatus === 'DONE' && isSubtaskRow) {
			
			// 親タスクIDを取得し、同じ親タスクIDを持つ兄弟サブタスク行を取得する
            const parentId = row.data('parent-id');
			
			// 兄弟サブタスク行のうち、今回変更対象の行を除いたものを取得する
			// $はjQueryのセレクタ関数で、$('.subtask-row[data-parent-id="' + parentId + '"]')で、親タスクIDがparentIdのサブタスク行を取得する
			// 取得した要素から.not(row)で、今回変更対象の行を除外する
            const siblingRows = $('.subtask-row[data-parent-id="' + parentId + '"]').not(row);
			
			// 兄弟サブタスク行のステータスを確認し、全てDONEまたはCANCELLEDであれば、親タスクも自動的にDONEになることを確認する
			// 兄弟サブタスク行のステータスを取得するために、siblingRows.toArray()で配列に変換し、every()で全ての要素が条件を満たすかを判定する
			// 兄弟サブタスク行のステータスは、$(siblingRow).find('.status-select').data('status')で取得する
			// すべての兄弟サブタスク行がDONEまたはCANCELLEDであれば、willCompleteAllにはtrueが入る
            const willCompleteAll = siblingRows.toArray().every(function (siblingRow) {
                const siblingStatus = $(siblingRow).find('.status-select').data('status');
                return siblingStatus === 'DONE' || siblingStatus === 'CANCELLED';
            });
			// 兄弟サブタスクが全てDONEまたはCANCELLEDであれば、これで親タスクも自動的にDONEになる。
            if (willCompleteAll) {
				// 確認ポップアップダイアログを表示し、ユーザーがキャンセルした場合はステータス変更を取り消す。
                if (!confirm('この操作で全てのサブタスクが完了になるため、親タスクも自動的に完了になります。よろしいですか？')) {
                    select.val(oldStatus);
                    return;
                }
            }
        }

        // 通信中は連続クリック（再変更）を防止する
        select.prop('disabled', true);

        $.ajax({
            url: '/api/tasks/' + taskId + '/status',
            type: 'PATCH',
            contentType: 'application/json',
            data: JSON.stringify({ status: newStatus }),
            success: function () {
                // 現在のフィルタ条件で一覧を再取得し、行のスタイル・絞り込み結果を最新化する
                reloadTaskList();
            },
            error: function (xhr) {
                // サーバー側の400エラーメッセージ（例:「不正なステータス値です: 〇〇」）をそのまま表示する
                const message = xhr.responseText || 'ステータスの更新に失敗しました。';
                alert(message);
                select.prop('disabled', false);
                // 失敗時もサーバーの現在の状態に合わせて表示を戻す
                reloadTaskList();
            }
        });
    });

    // ------------------------------------------------------------
    // 削除（イベント委譲）
    // ------------------------------------------------------------

    $('#taskTableBody').on('click', '.btn-delete', function () {
        const hasSubtasks = $(this).data('has-subtasks') === true || $(this).data('has-subtasks') === 'true';
        // 親タスク削除時は、紐づくサブタスクも一緒に削除されることを注記する
        const confirmMessage = hasSubtasks
            ? 'このタスクを削除しますか？（紐づくサブタスクも削除されます）'
            : 'このタスクを削除しますか？';
        if (!confirm(confirmMessage)) {
            return;
        }

        const taskId = $(this).data('task-id');

        $.ajax({
            url: '/api/tasks/' + taskId,
            type: 'DELETE',
            success: function () {
                // タスク削除APIは対象が既に存在しない場合も200を返す仕様のため、
                // 成功・失敗にかかわらず必ず何らかのメッセージを表示する
                alert('タスクを削除しました。');
                reloadTaskList();
            },
            error: function (xhr) {
                const message = xhr.responseText || 'タスクの削除に失敗しました。';
                alert(message);
            }
        });
    });

    // ------------------------------------------------------------
    // サブタスクの展開／折りたたみ
    // ------------------------------------------------------------

    $('#taskTableBody').on('click', '.btn-toggle-subtasks', function () {
        const button = $(this);
        const parentId = button.data('task-id');
        const isExpanded = button.attr('aria-expanded') === 'true';

        if (isExpanded) {
            collapseSubtasks(parentId);
        } else {
            expandSubtasks(parentId);
        }
    });
});

// ------------------------------------------------------------
// タスク一覧の取得・再描画
// ------------------------------------------------------------

// 現在展開中（サブタスクを表示中）の親タスクIDの集合。
// 一覧が再描画（reloadTaskList）されるたびにテーブルが作り直されるため、
// 再描画後もユーザーが開いていたサブタスクの展開状態を復元するために使用する。
const expandedParentIds = new Set();

// ドラッグ&ドロップ中に一時的にDOMから退避させている、ドラッグ対象の親タスクのサブタスク行。
// ドラッグしていない間や、ドラッグ対象の親タスクが展開されていない場合はnull。
let draggingSubtaskRows = null;

/**
 * 現在のフィルタ条件でタスク一覧をAjax取得し、テーブルを再描画する。
 * 画面全体は再読み込みしないため、フィルタの選択状態や展開中のサブタスクの表示状態は維持される。
 */
function reloadTaskList() {
    const params = {
        status: $('#filterStatus').val(),
        due: $('#filterDue').val(),
        priority: $('#filterPriority').val(),
        category: $('#filterCategory').val(),
        keyword: $('#filterKeyword').val()
    };

    $.ajax({
        url: '/api/tasks',
        type: 'GET',
        data: params,
        success: function (tasks) {
            renderTaskTable(tasks);
            updateFilterHighlight();
            updateSortableState();
            restoreExpandedSubtasks();
        },
        error: function (xhr) {
            alert(xhr.responseText || 'タスク一覧の取得に失敗しました。');
        }
    });
}

/**
 * 取得したタスク配列を元に、テーブルの中身を丸ごと作り直す。
 *
 * @param {Array} tasks サーバーから返されたタスクの配列
 */
function renderTaskTable(tasks) {
    const tbody = $('#taskTableBody');
    tbody.empty();

    if (!tasks || tasks.length === 0) {
        $('#emptyState').show();
        $('#taskTable').hide();
        return;
    }

    $('#emptyState').hide();
    $('#taskTable').show();

    tasks.forEach(function (task) {
        tbody.append(buildTaskRow(task));
    });
}

/**
 * 1件のタスクからテーブル行（tr要素）を組み立てる。
 * サーバー側のThymeleafテンプレート（list.html）と同じ見た目になるようにする。
 *
 * @param {Object} task タスクオブジェクト（TaskRestControllerのJSONレスポンス1件分）
 * @returns {jQuery} 組み立てた tr 要素
 */
function buildTaskRow(task) {
    const statusClassMap = {
        DONE: 'is-done',
        CANCELLED: 'is-cancelled',
        IN_PROGRESS: 'is-in-progress',
        ON_HOLD: 'is-on-hold'
    };

    const row = $('<tr>')
        .attr('id', 'task-row-' + task.id)
        .attr('data-task-id', task.id);
    if (statusClassMap[task.status]) {
        row.addClass(statusClassMap[task.status]);
    }

    // サブタスク展開アイコン。
    // サブタスクを持たないタスクでは空セルのままにする。
    const expandTd = $('<td class="col-expand">');
    if (task.hasSubtasks) {
        expandTd.append(
            $('<button type="button" class="btn-toggle-subtasks" title="サブタスクを表示">')
                .attr('data-task-id', task.id)
                .attr('aria-expanded', 'false')
                .text('▶')
        );
    }
    row.append(expandTd);

    // ステータスのセレクトボックス
    const statusTd = $('<td>');
    const select = $('<select class="status-select">')
        .attr('data-task-id', task.id)
        .attr('data-status', task.status)
        .attr('data-has-subtasks', !!task.hasSubtasks)
        .html(getStatusOptionsHtml());
    select.val(task.status);
    statusTd.append(select);
    row.append(statusTd);

    // タスク名・詳細
    const titleTd = $('<td>');
    titleTd.append(
        $('<span class="priority-badge">')
            .addClass('priority-' + String(task.priority).toLowerCase())
            .text(getPriorityLabel(task.priority))
    );
    if (task.categoryId) {
        const categoryInfo = getCategoryInfo(task.categoryId);
        if (categoryInfo) {
            titleTd.append(
                $('<span class="category-badge">')
                    .css('background-color', categoryInfo.color)
                    .text(categoryInfo.name)
            );
        }
    }
    if (task.overdue) {
        titleTd.append($('<span class="due-label">').text('期限切れ'));
    }
    if (task.dueToday) {
        titleTd.append($('<span class="due-label due-label-today">').text('本日期限'));
    }
    // サブタスクの進捗表示。
    // テキスト表示に加え、視覚的に把握しやすいよう進捗バーも併記する（list.htmlのSSR描画と同じ見た目にする）。
    // task.hasSubtasksがtrueの時点でsubtaskTotalCountは1以上であることが保証されているため、
    // ここでのゼロ除算は発生しない。
    if (task.hasSubtasks) {
        const pct = Math.round(task.subtaskDoneCount * 100 / task.subtaskTotalCount);
        const wrapper = $('<span class="subtask-progress-wrapper">');
        wrapper.append(
            $('<span class="subtask-progress">').text(task.subtaskDoneCount + '/' + task.subtaskTotalCount + '完了')
        );
        const bar = $('<span class="subtask-progress-bar" role="progressbar">')
            .attr('aria-valuenow', pct)
            .attr('aria-valuemin', 0)
            .attr('aria-valuemax', 100)
            .attr('title', pct + '%完了');
        const fill = $('<span class="subtask-progress-bar-fill">').css('width', pct + '%');
        if (pct === 100) {
            fill.addClass('is-complete');
        }
        bar.append(fill);
        wrapper.append(bar);
        titleTd.append(wrapper);
    }
    titleTd.append($('<span class="task-title">').text(task.title));
    if (task.description) {
        titleTd.append($('<p class="task-desc">').text(task.description));

    }
    row.append(titleTd);

    // 期限
    const dueTd = $('<td class="text-muted">');
    if (task.overdue) {
        dueTd.addClass('due-overdue');
    } else if (task.dueToday) {
        dueTd.addClass('due-today');
    }
    dueTd.text(task.dueDate ? formatDate(task.dueDate) : '-');
    row.append(dueTd);

    // 登録日時
    row.append($('<td class="text-muted col-created-at">').text(formatDateTime(task.createdAt)));

    // 完了日時（statusをDONEに変更した日時。未完了の場合は「-」を表示）
    row.append($('<td class="text-muted col-completed-at">').text(task.completedAt ? formatDateTime(task.completedAt) : '-'));

    // 操作ボタン
    const actionTd = $('<td class="action-buttons">');
    // サブタスク追加ボタン。クリックすると新規作成画面へ遷移し、
    // このタスクが「親タスク」選択欄に事前選択された状態になる。
    // サブタスク行（buildSubtaskRowで組み立てた行）では1階層制限のため非表示にする。
    actionTd.append(
        $('<a class="btn btn-secondary btn-sm btn-add-subtask">')
            .attr('href', '/tasks/new?parentTaskId=' + task.id)
            .text('＋サブタスク')
    );
    actionTd.append(
        $('<a class="btn btn-secondary btn-sm">')
            .attr('href', '/tasks/' + task.id + '/edit')
            .text('編集')
    );
    actionTd.append(
        $('<button class="btn btn-danger btn-sm btn-delete">')
            .attr('data-task-id', task.id)
            .attr('data-has-subtasks', !!task.hasSubtasks)
            .text('削除')
    );
    row.append(actionTd);

    return row;
}

/**
 * サブタスク1件分のテーブル行を組み立てる。
 * {@link buildTaskRow} で組み立てた通常の行をベースに、サブタスク行であることを示す
 * クラス・データ属性を付加し、タイトルをインデント表示する。
 *
 * @param {Object} subtask サブタスクのタスクオブジェクト
 * @param {number} parentId 親タスクのID
 * @returns {jQuery} 組み立てた tr 要素
 */
function buildSubtaskRow(subtask, parentId) {
    const row = buildTaskRow(subtask);
    row.addClass('subtask-row').attr('data-parent-id', parentId);
    // サブタスクは1階層までのため、サブタスク行には「＋サブタスク」ボタンを表示しない
    row.find('.btn-add-subtask').remove();
    row.find('.task-title').addClass('subtask-title');
    return row;
}

/**
 * 指定した親タスクに紐づくサブタスクをAjaxで取得し、親タスク行の直後に展開表示する。
 *
 * @param {number} parentId 親タスクのID
 */
function expandSubtasks(parentId) {
    const button = $('#task-row-' + parentId).find('.btn-toggle-subtasks');
    button.prop('disabled', true);

    $.ajax({
        url: '/api/tasks/' + parentId + '/subtasks',
        type: 'GET',
        success: function (subtasks) {
            expandedParentIds.add(parentId);
            renderSubtaskRows(parentId, subtasks);
            button.attr('aria-expanded', 'true').text('▼').prop('disabled', false);
        },
        error: function (xhr) {
            alert(xhr.responseText || 'サブタスクの取得に失敗しました。');
            button.prop('disabled', false);
        }
    });
}

/**
 * 指定した親タスクのサブタスク行を折りたたむ（DOMから除去する）。
 *
 * @param {number} parentId 親タスクのID
 */
function collapseSubtasks(parentId) {
    expandedParentIds.delete(parentId);
    $('.subtask-row[data-parent-id="' + parentId + '"]').remove();
    $('#task-row-' + parentId).find('.btn-toggle-subtasks').attr('aria-expanded', 'false').text('▶');
}

/**
 * 指定した親タスクのサブタスク行を（既存があれば作り直して）親タスク行の直後に挿入する。
 *
 * @param {number} parentId 親タスクのID
 * @param {Array} subtasks サブタスクの配列
 */
function renderSubtaskRows(parentId, subtasks) {
    $('.subtask-row[data-parent-id="' + parentId + '"]').remove();
    let insertAfter = $('#task-row-' + parentId);
    subtasks.forEach(function (subtask) {
        const row = buildSubtaskRow(subtask, parentId);
        insertAfter.after(row);
        insertAfter = row;
    });
}

/**
 * {@link reloadTaskList} による再描画後、ユーザーが展開していたサブタスクの表示状態を復元する。
 * 親タスク自体が一覧から消えていた場合（削除・絞り込み対象外になった等）は展開状態を破棄する。
 */
function restoreExpandedSubtasks() {
    expandedParentIds.forEach(function (parentId) {
        const parentRow = $('#task-row-' + parentId);
        if (parentRow.length === 0) {
            // 親タスクが一覧に存在しない（削除された、または絞り込みで対象外になった）場合は展開状態を破棄する
            expandedParentIds.delete(parentId);
            return;
        }
        $.ajax({
            url: '/api/tasks/' + parentId + '/subtasks',
            type: 'GET',
            success: function (subtasks) {
                renderSubtaskRows(parentId, subtasks);
                parentRow.find('.btn-toggle-subtasks').attr('aria-expanded', 'true').text('▼');
            },
            error: function () {
                // 親タスクが削除されていた等で取得に失敗した場合は、展開状態を諦めて静かに無視する
                expandedParentIds.delete(parentId);
            }
        });
    });
}

/**
 * ステータス選択肢（&lt;option&gt;群）のHTMLを、サーバーが埋め込んだ非表示テンプレートから取得する。
 * これにより、TaskStatus enumの値・表示名を変更してもJS側の修正が不要になる。
 *
 * @returns {string} option要素群のHTML
 */
function getStatusOptionsHtml() {
    return $('#statusOptionsTemplate').html();
}

/**
 * 優先度の表示名を、フィルタ用プルダウン（#filterPriority）の選択肢から取得する。
 *
 * @param {string} value 優先度の値（例: "HIGH"）
 * @returns {string} 表示名（例: "高"）。見つからない場合は value をそのまま返す。
 */
function getPriorityLabel(value) {
    let label = value;
    $('#filterPriority option').each(function () {
        if ($(this).val() === value) {
            label = $(this).text();
        }
    });
    return label;
}

/**
 * カテゴリの表示名・ラベル色を、カテゴリ絞り込み用プルダウン（#filterCategory）の選択肢から取得する。
 *
 * @param {number|string} categoryId カテゴリID
 * @returns {{name: string, color: string}|null} 見つかった場合はカテゴリ情報、見つからない場合は null
 */
function getCategoryInfo(categoryId) {
    let info = null;
    $('#filterCategory option').each(function () {
        const option = $(this);
        if (String(option.val()) === String(categoryId)) {
            info = {
                name: option.text(),
                color: option.data('color') || '#334155'
            };
        }
    });
    return info;
}

/**
 * ISO形式の日付文字列（例: "2026-09-16"）を "yyyy/MM/dd" 形式に変換する。
 *
 * @param {string} isoDate ISO形式の日付文字列
 * @returns {string} 変換後の文字列
 */
function formatDate(isoDate) {
    if (!isoDate) {
        return '-';
    }
    const parts = isoDate.split('-');
    return parts[0] + '/' + parts[1] + '/' + parts[2];
}

/**
 * ISO形式の日時文字列（例: "2026-09-16T10:15:30"）を "yyyy/MM/dd HH:mm" 形式に変換する。
 *
 * @param {string} isoDateTime ISO形式の日時文字列
 * @returns {string} 変換後の文字列
 */
function formatDateTime(isoDateTime) {
    if (!isoDateTime) {
        return '';
    }
    const [datePart, timePart] = isoDateTime.split('T');
    const hourMinute = timePart ? timePart.substring(0, 5) : '';
    return formatDate(datePart) + ' ' + hourMinute;
}

// ------------------------------------------------------------
// フィルタのハイライト表示
// ------------------------------------------------------------

/**
 * 「すべて」以外が選択・入力されているフィルタ項目をハイライト表示する。
 */
function updateFilterHighlight() {
    $('.filter-select').each(function () {
        $(this).toggleClass('filter-active', $(this).val() !== '');
    });
    $('#filterKeyword').toggleClass('filter-active', $('#filterKeyword').val() !== '');
}

/**
 * 現在、フィルタが1つも適用されていない（＝「すべて」表示）かどうかを判定する。
 *
 * @returns {boolean} フィルタなしの場合 true
 */
function isFilterCleared() {
    return $('#filterStatus').val() === ''
        && $('#filterDue').val() === ''
        && $('#filterPriority').val() === ''
        && $('#filterCategory').val() === ''
        && $('#filterKeyword').val() === '';
}

// ------------------------------------------------------------
// 並び替え（ドラッグ＆ドロップ）機能
// ------------------------------------------------------------

/**
 * フィルタの状態に応じて、jQuery UIのsortable()の有効／無効を切り替える。
 */
function updateSortableState() {
    const tbody = $('#taskTableBody');
    const table = $('#taskTable');

    // 既に有効化されている場合は一旦破棄してから状態を作り直す
    if (tbody.hasClass('ui-sortable')) {
        tbody.sortable('destroy');
    }

    if (!isFilterCleared()) {
        // 絞り込み中は並び替えを禁止する
        table.addClass('sort-disabled');
        $('#sortHint').text('絞り込み中は並び替えできません（「すべて」表示に戻すと並び替えできます）');
        return;
    }

    table.removeClass('sort-disabled');
    $('#sortHint').text('行をドラッグして並び替えできます（絞り込み中は並び替えできません）');

    tbody.sortable({
        // サブタスク行（.subtask-row）は並び替えの対象から除外する
        items: '> tr:not(.subtask-row)',
        axis: 'y',
        cursor: 'move',
        start: function (event, ui) {
            // ドラッグ中は、jQuery UIのsortable()が「items」で指定した行（親タスク行）自体
            // しか動かさないため、展開表示中のサブタスク行（別のtr要素）はその場に取り残され、
            // ドラッグ中ずっと「親タスク行だけが動いて、サブタスク行は置き去りになる」という
            // 見た目上の違和感が生じてしまう。
            // これを防ぐため、ドラッグ対象の親タスクが展開中であれば、ドラッグ開始と同時に
            // そのサブタスク行を一旦DOMから退避（非表示に）し、ドラッグ中は画面上に存在しない
            // 状態にしておく（stop時に新しい位置へ戻すことで、親子がセットで動いたように見せる）。
            const parentId = ui.item.data('task-id');
            const subtaskRows = $('.subtask-row[data-parent-id="' + parentId + '"]');
            draggingSubtaskRows = (expandedParentIds.has(parentId) && subtaskRows.length > 0)
                ? subtaskRows.detach()
                : null;
        },
        stop: function (event, ui) {
            // ドラッグが終了したら（並び順が実際に変わったかどうかに関わらず）、startで退避した
            // サブタスク行を、ドロップ後の親タスク行（ui.item＝新しい位置）の直後へ戻す。
            // update（並び順が変わった場合のみ発火）ではなくstop（常に発火）で戻すことで、
            // 元の位置に戻された場合（＝並び順が変わらなかった場合）にもサブタスク行が
            // 消えたままにならないようにしている。
            if (draggingSubtaskRows) {
                ui.item.after(draggingSubtaskRows);
                draggingSubtaskRows = null;
            }
        },
        update: function () {
            const orderedTaskIds = tbody.children('tr:not(.subtask-row)').map(function () {
                return $(this).data('task-id');
            }).get();

            $.ajax({
                url: '/api/tasks/reorder',
                type: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify({ taskIds: orderedTaskIds }),
                error: function (xhr) {
                    const message = xhr.responseText || '並び替えの保存に失敗しました。';
                    alert(message);
                    // 保存に失敗した場合はサーバー側の状態に合わせて表示を戻す
                    reloadTaskList();
                }
            });
        }
    });
}