$(function () {

    // ------------------------------------------------------------
    // 初期化
    // ------------------------------------------------------------

    // 初期表示時に、フィルタ条件（初期状態は「すべて」）でタスク一覧を取得し直す。
    // サーバー側で描画済みのSSR内容はそのままフォールバック表示として利用し、
    // Ajaxが正常に動作すればすぐに同じ内容（＋フィルタ機能）に置き換わる。
    reloadTaskList();

    // ------------------------------------------------------------
    // フィルタ・検索（指示書「フィルタリング・検索仕様」）
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

    $('#taskTableBody').on('change', '.status-select', function () {
        const select = $(this);
        const taskId = select.data('task-id');
        const newStatus = select.val();

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
        if (!confirm('このタスクを削除しますか？')) {
            return;
        }

        const taskId = $(this).data('task-id');

        $.ajax({
            url: '/api/tasks/' + taskId,
            type: 'DELETE',
            success: function () {
                // タスク削除APIは対象が既に存在しない場合も200を返す仕様のため、
                // 成功・失敗にかかわらず必ず何らかのメッセージを表示する
                // （カテゴリ削除時の successMessage 表示と同じ方針に合わせたもの）
                alert('タスクを削除しました。');
                reloadTaskList();
            },
            error: function (xhr) {
                const message = xhr.responseText || 'タスクの削除に失敗しました。';
                alert(message);
            }
        });
    });
});

// ------------------------------------------------------------
// タスク一覧の取得・再描画
// ------------------------------------------------------------

/**
 * 現在のフィルタ条件でタスク一覧をAjax取得し、テーブルを再描画する。
 * 画面全体は再読み込みしない（指示書「フィルタ・検索操作時に画面全体が再読み込みされない」に対応）。
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

    // ステータスのセレクトボックス
    const statusTd = $('<td>');
    const select = $('<select class="status-select">')
        .attr('data-task-id', task.id)
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
    actionTd.append(
        $('<a class="btn btn-secondary btn-sm">')
            .attr('href', '/tasks/' + task.id + '/edit')
            .text('編集')
    );
    actionTd.append(
        $('<button class="btn btn-danger btn-sm btn-delete">')
            .attr('data-task-id', task.id)
            .text('削除')
    );
    row.append(actionTd);

    return row;
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
 * 「すべて」以外が選択・入力されているフィルタ項目をハイライト表示する
 * （指示書「フィルタ選択状態は、選択中のボタンをハイライト表示して画面上に示す」に対応）。
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
// 並び替え（ドラッグ＆ドロップ、指示書「並び替え仕様」）
// ------------------------------------------------------------

/**
 * フィルタの状態に応じて、jQuery UIのsortable()の有効／無効を切り替える。
 * 指示書「並び替えはフィルタ『すべて』表示時のみ許可する。絞り込み中は並び替えを禁止する」に対応。
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
        items: 'tr',
        axis: 'y',
        cursor: 'move',
        update: function () {
            const orderedTaskIds = tbody.children('tr').map(function () {
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