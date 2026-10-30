// ============================================================
// グローバル状態管理（フィールド定義）
// ============================================================

/** 現在展開中（サブタスクを表示中）の親タスクIDの集合（再描画時の状態復元用） */
const expandedParentIds = new Set();

/** ドラッグ&ドロップ中に一時退避させているサブタスク行のDOM要素 */
let draggingSubtaskRows = null;

// ============================================================
//  初期化 & イベントバインド（メインエントリーポイント）
// ============================================================

$(function() {

	// 初期表示時にタスク一覧を取得
	reloadTaskList();

	// フィルタ・検索のイベントハンドラー登録
	$('#filterStatus, #filterDue, #filterPriority, #filterCategory').on('change', reloadTaskList);

	let keywordTimer = null;
	$('#filterKeyword').on('input', function() {
		clearTimeout(keywordTimer);
		keywordTimer = setTimeout(reloadTaskList, 300);
	});

	// 各操作のイベントハンドラー登録（イベント委譲）

	//　ステータス変更
	$('#taskTableBody').on('change', '.status-select', handleStatusChange);

	//　タスク削除
	$('#taskTableBody').on('click', '.btn-delete', handleDeleteTask);

	// サブタスク展開
	$('#taskTableBody').on('click', '.btn-toggle-subtasks', handleToggleSubtasks);
});

// ============================================================
//  イベントハンドラー関数（各操作の実体）
// ============================================================

/**
 * ステータス変更時の処理ハンドラー。
 */
function handleStatusChange() {
	const select = $(this);
	const taskId = select.data('task-id');
	const newStatus = select.val();
	const oldStatus = select.data('status');
	const hasSubtasks = select.data('has-subtasks') === true || select.data('has-subtasks') === 'true';
	const row = select.closest('tr');
	const isSubtaskRow = row.hasClass('subtask-row');

	if (newStatus === 'DONE' && hasSubtasks) {
		if (!confirm('このタスクを完了にすると、紐づく全てのサブタスクも完了になります。よろしいですか？')) {
			select.val(oldStatus);
			return;
		}
	}

	if (newStatus === 'CANCELLED' && hasSubtasks) {
		if (!confirm('この操作を行うと、未完了のサブタスクもすべて中止になります。よろしいですか？')) {
			select.val(oldStatus);
			return;
		}
	}

	if (newStatus === 'DONE' && isSubtaskRow) {
		const parentId = row.data('parent-id');
		const siblingRows = $('.subtask-row[data-parent-id="' + parentId + '"]').not(row);

		const willCompleteAll = siblingRows.toArray().every(function(siblingRow) {
			const siblingStatus = $(siblingRow).find('.status-select').data('status');
			return siblingStatus === 'DONE' || siblingStatus === 'CANCELLED';
		});

		if (willCompleteAll) {
			if (!confirm('この操作で全てのサブタスクが完了になるため、親タスクも自動的に完了になります。よろしいですか？')) {
				select.val(oldStatus);
				return;
			}
		}
	}

	select.prop('disabled', true);

	$.ajax({
		url: '/api/tasks/' + taskId + '/status',
		type: 'PATCH',
		contentType: 'application/json',
		data: JSON.stringify({ status: newStatus }),
		success: function() {
			reloadTaskList();
		},
		error: function(xhr) {
			const message = xhr.responseText || 'ステータスの更新に失敗しました。';
			alert(message);
			select.prop('disabled', false);
			reloadTaskList();
		}
	});
}

/**
 * タスク削除ボタンクリック時の処理ハンドラー。
 */
function handleDeleteTask() {
	const button = $(this);
	const hasSubtasks = button.data('has-subtasks') === true || button.data('has-subtasks') === 'true';
	const confirmMessage = hasSubtasks
		? 'このタスクを削除しますか？（紐づくサブタスクも削除されます）'
		: 'このタスクを削除しますか？';

	if (!confirm(confirmMessage)) {
		return;
	}

	const taskId = button.data('task-id');

	$.ajax({
		url: '/api/tasks/' + taskId,
		type: 'DELETE',
		success: function() {
			alert('タスクを削除しました。');
			reloadTaskList();
		},
		error: function(xhr) {
			const message = xhr.responseText || 'タスクの削除に失敗しました。';
			alert(message);
		}
	});
}

/**
 * サブタスク展開／折りたたみボタンクリック時の処理ハンドラー。
 */
function handleToggleSubtasks() {
	const button = $(this);
	const parentId = button.data('task-id');
	const isExpanded = button.attr('aria-expanded') === 'true';

	if (isExpanded) {
		collapseSubtasks(parentId);
	} else {
		expandSubtasks(parentId);
	}
}

// ============================================================
//  タスク一覧の取得・再描画・ヘルパー関数
// ============================================================

/**
 * 現在のフィルタ条件でタスク一覧をAjax取得し、テーブルを再描画する。
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
		success: function(tasks) {
			renderTaskTable(tasks);
			updateFilterHighlight();
			updateSortableState();
			restoreExpandedSubtasks();
		},
		error: function(xhr) {
			alert(xhr.responseText || 'タスク一覧の取得に失敗しました。');
		}
	});
}

/**
 * 取得したタスク配列を元に、テーブルの中身（#taskTableBody）を丸ごと作り直す。
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

	tasks.forEach(function(task) {
		tbody.append(buildTaskRow(task));
	});
}

/**
 * 1件のタスクからテーブル行（tr要素）を組み立てる。
 * @param {Object} task タスクオブジェクト
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

	const statusTd = $('<td>');
	const select = $('<select class="status-select">')
		.attr('data-task-id', task.id)
		.attr('data-status', task.status)
		.attr('data-has-subtasks', !!task.hasSubtasks)
		.html(getStatusOptionsHtml());
	select.val(task.status);
	statusTd.append(select);
	row.append(statusTd);

	const titleTd = $('<td>');
	const metaLine = $('<div class="task-meta-line">');
	metaLine.append(
		$('<span class="priority-badge">')
			.addClass('priority-' + String(task.priority).toLowerCase())
			.text(getPriorityLabel(task.priority))
	);
	if (task.recurrenceRule && task.recurrenceRule !== 'NONE') {
		metaLine.append(
			$('<span class="recurrence-badge">')
				.addClass('recurrence-' + String(task.recurrenceRule).toLowerCase())
				.text('🔁 ' + getRecurrenceLabel(task.recurrenceRule))
		);
	}
	if (task.categoryId) {
		const categoryInfo = getCategoryInfo(task.categoryId);
		if (categoryInfo) {
			metaLine.append(
				$('<span class="category-badge">')
					.css('background-color', categoryInfo.color)
					.text(categoryInfo.name)
			);
		}
	}
	if (task.overdue) {
		metaLine.append($('<span class="due-label">').text('期限切れ'));
	}
	if (task.dueToday) {
		metaLine.append($('<span class="due-label due-label-today">').text('本日期限'));
	}
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
		metaLine.append(wrapper);
	}
	if (metaLine.children().length > 0) {
		titleTd.append(metaLine);
	}
	const mainLine = $('<div class="task-main-line">');
	mainLine.append($('<span class="task-title">').text(task.title));
	titleTd.append(mainLine);
	if (task.description) {
		titleTd.append($('<p class="task-desc">').text(task.description));
	}
	row.append(titleTd);

	const dueTd = $('<td class="text-muted">');
	if (task.overdue) {
		dueTd.addClass('due-overdue');
	} else if (task.dueToday) {
		dueTd.addClass('due-today');
	}
	dueTd.text(task.dueDate ? formatDate(task.dueDate) : '-');
	row.append(dueTd);

	row.append($('<td class="text-muted col-created-at">').text(formatDateTime(task.createdAt)));
	row.append($('<td class="text-muted col-completed-at">').text(task.completedAt ? formatDateTime(task.completedAt) : '-'));

	const actionTd = $('<td class="action-buttons">');
	if (task.status === 'DONE' || task.status === 'CANCELLED') {
		actionTd.append(
			$('<button type="button" class="btn btn-secondary btn-sm btn-add-subtask btn-subtask-disabled" disabled>')
				.attr('title', '完了済み・中止済みのタスクにはサブタスクを追加できません')
				.text('＋サブタスク')
		);
	} else {
		actionTd.append(
			$('<a class="btn btn-secondary btn-sm btn-add-subtask">')
				.attr('href', '/tasks/new?parentTaskId=' + task.id)
				.text('＋サブタスク')
		);
	}
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
 * @param {Object} subtask サブタスクオブジェクト
 * @param {number} parentId 親タスクのID
 * @returns {jQuery} 組み立てた tr 要素
 */
function buildSubtaskRow(subtask, parentId) {
	const row = buildTaskRow(subtask);
	row.addClass('subtask-row').attr('data-parent-id', parentId);
	row.find('.btn-add-subtask').remove();
	row.find('.task-title').addClass('subtask-title');
	return row;
}

/**
 * 指定した親タスクに紐づくサブタスクをAjaxで取得し、親タスク行の直下に展開表示する。
 * @param {number} parentId 親タスクのID
 */
function expandSubtasks(parentId) {
	const button = $('#task-row-' + parentId).find('.btn-toggle-subtasks');
	button.prop('disabled', true);

	$.ajax({
		url: '/api/tasks/' + parentId + '/subtasks',
		type: 'GET',
		success: function(subtasks) {
			expandedParentIds.add(parentId);
			renderSubtaskRows(parentId, subtasks);
			button.attr('aria-expanded', 'true').text('▼').prop('disabled', false);
		},
		error: function(xhr) {
			alert(xhr.responseText || 'サブタスクの取得に失敗しました。');
			button.prop('disabled', false);
		}
	});
}

/**
 * 指定した親タスクのサブタスク行を折りたたむ（DOMから除去する）。
 * @param {number} parentId 親タスクのID
 */
function collapseSubtasks(parentId) {
	expandedParentIds.delete(parentId);
	$('.subtask-row[data-parent-id="' + parentId + '"]').remove();
	$('#task-row-' + parentId).find('.btn-toggle-subtasks').attr('aria-expanded', 'false').text('▶');
}

/**
 * 指定した親タスクのサブタスク行（DOM）を親タスク行の直後へ動的に挿入・追加する。
 * @param {number} parentId 親タスクのID
 * @param {Array} subtasks サブタスクの配列
 */
function renderSubtaskRows(parentId, subtasks) {
	$('.subtask-row[data-parent-id="' + parentId + '"]').remove();
	let insertAfter = $('#task-row-' + parentId);
	subtasks.forEach(function(subtask) {
		const row = buildSubtaskRow(subtask, parentId);
		insertAfter.after(row);
		insertAfter = row;
	});
}

/**
 * reloadTaskList による再描画後、ユーザーが開いていたサブタスクの展開状態を非同期で再取得・復元する。
 */
function restoreExpandedSubtasks() {
	expandedParentIds.forEach(function(parentId) {
		const parentRow = $('#task-row-' + parentId);
		if (parentRow.length === 0) {
			expandedParentIds.delete(parentId);
			return;
		}
		$.ajax({
			url: '/api/tasks/' + parentId + '/subtasks',
			type: 'GET',
			success: function(subtasks) {
				renderSubtaskRows(parentId, subtasks);
				parentRow.find('.btn-toggle-subtasks').attr('aria-expanded', 'true').text('▼');
			},
			error: function() {
				expandedParentIds.delete(parentId);
			}
		});
	});
}

/**
 * ステータス選択肢（option要素群）のHTMLテンプレートを取得する。
 * @returns {string} テンプレートHTML文字列
 */
function getStatusOptionsHtml() {
	return $('#statusOptionsTemplate').html();
}

/**
 * 優先度の表示名（高・中・低など）をフィルタ用プルダウンの選択肢から取得する。
 * @param {string} value 優先度の内部文字列（例: "HIGH"）
 * @returns {string} 表示用ラベル名
 */
function getPriorityLabel(value) {
	let label = value;
	$('#filterPriority option').each(function() {
		if ($(this).val() === value) {
			label = $(this).text();
		}
	});
	return label;
}

/**
 * 期限の表示名（本日・明日・今週など）をフィルタ用プルダウンの選択肢から取得する。
 * @param {string} value 期限の内部文字列（例: "TODAY"）
 * @returns {string} 表示用ラベル名
 */
const RECURRENCE_RULE_LABELS = {
	NONE: '繰り返しなし',
	DAILY: '毎日',
	WEEKLY: '毎週',
	MONTHLY: '毎月'
};

/**
 * 繰り返し設定の内部識別子を表示名へ変換する。
 * @param {string} value 繰り返し識別子（例: "DAILY"）
 * @returns {string} 表示用ラベル名
 */
function getRecurrenceLabel(value) {
	return RECURRENCE_RULE_LABELS[value] || value;
}

/**
 * カテゴリIDからカテゴリの表示名および表示カラーを取得する。
 * @param {number|string} categoryId カテゴリID
 * @returns {{name: string, color: string}|null} カテゴリ情報オブジェクト
 */
function getCategoryInfo(categoryId) {
	let info = null;
	$('#filterCategory option').each(function() {
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
 * ISO形式の日付文字列 (yyyy-MM-dd) を "yyyy/MM/dd" 形式へフォーマットする。
 * @param {string} isoDate ISO日付文字列
 * @returns {string} フォーマット済み文字列
 */
function formatDate(isoDate) {
	if (!isoDate) {
		return '-';
	}
	const parts = isoDate.split('-');
	return parts[0] + '/' + parts[1] + '/' + parts[2];
}

/**
 * ISO形式の日時文字列 (yyyy-MM-ddTHH:mm:ss) を "yyyy/MM/dd HH:mm" 形式へフォーマットする。
 * @param {string} isoDateTime ISO日時文字列
 * @returns {string} フォーマット済み文字列
 */
function formatDateTime(isoDateTime) {
	if (!isoDateTime) {
		return '';
	}
	const [datePart, timePart] = isoDateTime.split('T');
	const hourMinute = timePart ? timePart.substring(0, 5) : '';
	return formatDate(datePart) + ' ' + hourMinute;
}

/**
 * 条件が指定されている（「すべて」以外）フィルタ選択肢に対してハイライト用CSSクラスを切り替える。
 */
function updateFilterHighlight() {
	$('.filter-select').each(function() {
		$(this).toggleClass('filter-active', $(this).val() !== '');
	});
	$('#filterKeyword').toggleClass('filter-active', $('#filterKeyword').val() !== '');
}

/**
 * フィルタ条件が一切指定されていない（＝全件表示状態）かを判定する。
 * @returns {boolean} 絞り込みなしの場合 true
 */
function isFilterCleared() {
	return $('#filterStatus').val() === ''
		&& $('#filterDue').val() === ''
		&& $('#filterPriority').val() === ''
		&& $('#filterCategory').val() === ''
		&& $('#filterKeyword').val() === '';
}

// ============================================================
//  並び替え（ドラッグ＆ドロップ）機能
// ============================================================

/**
 * ドラッグ＆ドロップ後のテーブル行（tr）の親子並び順構造が正当かを判定する。
 * @param {jQuery} tbody 対象の tbody 要素
 * @returns {boolean} 正当な並び順の場合 true
 */
function isTaskOrderValid(tbody) {
	let currentParentId = null;
	let valid = true;
	tbody.children('tr').each(function() {
		const row = $(this);
		if (row.hasClass('subtask-row')) {
			if (currentParentId === null || String(row.data('parent-id')) !== String(currentParentId)) {
				valid = false;
				return false;
			}
		} else {
			currentParentId = row.data('task-id');
		}
	});
	return valid;
}

/**
 * 絞り込み状態に応じて jQuery UI sortable の有効化／無効化を切り替える。
 */
function updateSortableState() {
	const tbody = $('#taskTableBody');
	const table = $('#taskTable');

	if (tbody.hasClass('ui-sortable')) {
		tbody.sortable('destroy');
	}

	if (!isFilterCleared()) {
		table.addClass('sort-disabled');
		$('#sortHint').text('絞り込み中は並び替えできません（「すべて」表示に戻すと並び替えできます）');
		return;
	}

	table.removeClass('sort-disabled');
	$('#sortHint').text('行をドラッグして並び替えできます（サブタスクは同じ親タスク内でのみ並び替え可能。絞り込み中は並び替えできません）');

	// jQuery UI sortable の初期化処理を実行
	tbody.sortable({
		// 並び替え可能な対象要素を tbody 直下の tr のみに限定する
		items: '> tr',

		// ドラッグ中の移動軸を垂直（Y軸）方向に固定する（※「高さを固定する」ではなく移動軸の制限）
		axis: 'y',

		// ドラッグ中のマウスカーソルの形状を move（移動用カーソル）に変更する
		cursor: 'move',

		// ドラッグ開始（持ち上げた瞬間）に発火するイベントハンドラ
		start: function(event, ui) {
			// ドラッグされた行（ui.item）の data-task-id 属性からタスクIDを取得
			const parentId = ui.item.data('task-id');

			// ドラッグ対象の親タスクIDに紐づくすべてのサブタスク行（.subtask-row）を取得
			const subtaskRows = $('.subtask-row[data-parent-id="' + parentId + '"]');

			// 親タスクが開いている（expandedParentIdsに存在する）かつサブタスクが存在する場合のみ
			// DOMからサブタスク行を一時的に切り離し（detach）、変数に保持する（非表示なら null）
			draggingSubtaskRows = (expandedParentIds.has(parentId) && subtaskRows.length > 0)
				? subtaskRows.detach()
				: null;
		},

		// ドロップして並び替え動作が終了した瞬間に発火するイベントハンドラ
		stop: function(event, ui) {
			// 並び替え後の順番が妥当かどうかを判定する
			if (!isTaskOrderValid(tbody)) {
				// 並び順が不正（親と子の位置関係が壊れた等）な場合はドラッグ前の状態にキャンセル
				tbody.sortable('cancel');
			}

			// start イベントで切り離したサブタスク行が存在する場合の復元処理
			if (draggingSubtaskRows) {
				// 移動完了した親タスク行（ui.item）のすぐ直後に切り離していたサブタスク行を再挿入
				ui.item.after(draggingSubtaskRows);

				// 保持していた参照をクリアしてメモリを解放
				draggingSubtaskRows = null;
			}
		},

		// DOMの位置変更が確定した際（ドロップ成功時）に発火するイベントハンドラ
		update: function(event, ui) {
			// 移動された要素がサブタスク行（.subtask-row）であるかを判定
			if (ui.item.hasClass('subtask-row')) {
				// 移動されたサブタスク行の data-parent-id 属性から親タスクIDを取得
				const parentId = ui.item.data('parent-id');

				// 同一の親IDを持つ全サブタスク行を現在のDOM順に走査し、タスクIDの配列を作成
				const orderedSubtaskIds = $('.subtask-row[data-parent-id="' + parentId + '"]').map(function() {
					return $(this).data('task-id');
				}).get();

				// 並び替え後のサブタスクID順をサーバーへ非同期送信（PUTリクエスト）
				$.ajax({
					url: '/api/tasks/' + parentId + '/subtasks/reorder',
					type: 'PUT',
					contentType: 'application/json',
					data: JSON.stringify({ taskIds: orderedSubtaskIds }),
					// 送信失敗時のエラーハンドリング
					error: function(xhr) {
						// サーバーからのエラーメッセージ、またはデフォルトメッセージを設定
						const message = xhr.responseText || 'サブタスクの並び替えの保存に失敗しました。';
						// ユーザーにアラートで通知
						alert(message);
						// 不整合を防ぐため一覧表示を再読み込みして復元
						reloadTaskList();
					}
				});
				// サブタスクの並び替え処理が終わったため、後続の親タスク用処理を行わずに抜け出す
				return;
			}

			// サブタスク行（.subtask-row）を除外した親タスク行のみを現在のDOM順に走査し、タスクIDの配列を作成
			const orderedTaskIds = tbody.children('tr:not(.subtask-row)').map(function() {
				return $(this).data('task-id');
			}).get();

			// 並び替え後の親タスクID順をサーバーへ非同期送信（PUTリクエスト）
			$.ajax({
				url: '/api/tasks/reorder',
				type: 'PUT',
				contentType: 'application/json',
				data: JSON.stringify({ taskIds: orderedTaskIds }),
				// 送信失敗時のエラーハンドリング
				error: function(xhr) {
					// サーバーからのエラーメッセージ、またはデフォルトメッセージを設定
					const message = xhr.responseText || '並び替えの保存に失敗しました。';
					// ユーザーにアラートで通知
					alert(message);
					// 不整合を防ぐため一覧表示を再読み込みして復元
					reloadTaskList();
				}
			});
		}
	});
}