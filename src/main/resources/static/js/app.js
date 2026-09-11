$(function () {

    // 各selectの初期選択値を「現在のステータス」として記録しておく
    // （成功時・失敗時に元の表示へ戻すための基準値になる）
	// ページ読み込み直後の「まだ何も変更していない状態」の値
    $('.status-select').each(function () {
        $(this).data('current-status', $(this).val());
    });

	// タスクのステータス変更時の処理
	$('.status-select').on('change', function () {
        const select = $(this);
        const taskId = select.data('task-id');
        const newStatus = select.val();
        const row = $('#task-row-' + taskId);

        // change発生時点でselect.val()は既に新しい値になっているため、
        // 変更前の値はdata属性に保存しておいたものを使う
        const revertStatus = select.data('current-status');

        // 通信中は連続クリック（再変更）を防止する
        select.prop('disabled', true);

		// AJAXリクエストでタスクのステータスを更新
        $.ajax({
            url: '/api/tasks/' + taskId + '/status',
            type: 'PATCH',
            contentType: 'application/json',
            data: JSON.stringify({ status: newStatus }),
            success: function () {
				// 成功時の処理
				// cssクラスを付け替えて、行の背景色を変更する
                // いったん全ステータス用クラスを外してから、現在のステータスに応じたクラスだけを付け直す
                row.removeClass('is-done is-cancelled is-in-progress is-on-hold');
                if (newStatus === 'DONE') {
                    row.addClass('is-done');
                } else if (newStatus === 'CANCELLED') {
                    row.addClass('is-cancelled');
                } else if (newStatus === 'IN_PROGRESS') {
                    row.addClass('is-in-progress');
                } else if (newStatus === 'ON_HOLD') {
                    row.addClass('is-on-hold');
                }
                // 成功した値を「現在のステータス」として記録しておく
                select.data('current-status', newStatus);
            },
			// エラー時の処理
			// TODO: レビュー時に確認
			// 指示書の「ステータス切り替え」には「失敗時は元の状態表示に戻す」としか記載がなく、
			// エラーメッセージの内容（xhr.responseTextをそのまま表示する等）までは規定されていない。
			// サーバー側の400エラーメッセージ（例:「不正なステータス値です: 〇〇」）をそのまま表示する方針を
			// 独自に採用している。要確認。
            error: function (xhr) {
				//あるならresponseTextを表示、なければデフォルトのエラーメッセージを表示
                const message = xhr.responseText || 'ステータスの更新に失敗しました。';
                alert(message);
                // サーバー側で更新が失敗しているため、選択欄の表示を変更前の状態に戻す
                if (revertStatus) {
                    select.val(revertStatus);
                }
            },
            complete: function () {
                // 成功・失敗にかかわらず、通信終了後は再操作できるようにする
                select.prop('disabled', false);
            }
        });
    });

	// タスクの削除ボタン押下時の処理
    $('.btn-delete').on('click', function () {
		// 確認ダイアログを表示
        if (!confirm('このタスクを削除しますか？')) {
            return;
        }

		// 削除ボタンのクリックイベントを処理
        const btn = $(this);
		// data属性からタスクIDを取得
        const taskId = btn.data('task-id');
		// タスクの行を取得
        const row = $('#task-row-' + taskId);

		// AJAXリクエストでタスクを削除
        $.ajax({
            url: '/api/tasks/' + taskId,
            type: 'DELETE',
            success: function () {
                row.fadeOut(300, function () {
                    $(this).remove();

					// タスクが1件もなくなった場合は、ページをリロードして「タスクがありません」の表示に切り替える
                    if ($('#taskTableBody tr').length === 0) {
                        location.reload();
                    }
                });
            },
			// エラー時の処理
            error: function (xhr) {
                const message = xhr.responseText || 'タスクの削除に失敗しました。';
                alert(message);
            }
        });
    });
});