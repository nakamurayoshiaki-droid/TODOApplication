# TODO アプリケーション

Spring Boot（Java 21 / Maven）で実装した、タスク管理アプリケーションです。
画面表示は Thymeleaf、ステータス変更・削除・フィルタ・並び替えなどの即時反映操作は jQuery（jQuery UI含む）による Ajax 通信で行います。

現時点の実装範囲は、作業指示書のフェーズ1（基本CRUD・ステータス管理）＋フェーズ2（期限・優先度・カテゴリ・絞り込み・並び替え）です。

---

## 概要

- タスクは5段階のステータス（未着手・進行中・保留・完了・中止）を持ちます。
- タスクには優先度（高・中・低）、期限（日付のみ）、カテゴリ（0または1個、任意）を設定できます。
- 一覧画面（Thymeleaf）でタスクの追加・編集・ステータス変更・削除ができます。
- 一覧画面では、ステータス／期限／優先度／カテゴリ／キーワードによる絞り込み（AND結合）ができます。
- 一覧画面でタスクをドラッグ＆ドロップして並び替えできます（絞り込み「すべて」表示時のみ）。
- カテゴリは専用の管理画面（`/categories`）で新規作成・削除ができます。
- ステータス変更・削除・絞り込み・並び替えは、画面を再読み込みせず jQuery の Ajax（`/api/tasks/**`）経由で即時反映します。
- データの永続化には MySQL / MariaDB + Spring Data JPA を使用します。

### 主なパッケージ構成

- `com.example.todo.model` - ドメインモデル
  - `Task`（タスク本体） / `TaskStatus`（ステータスenum） / `Priority`（優先度enum） / `Category`（カテゴリ）
- `com.example.todo.controller` - コントローラー
  - `TaskViewController` : 画面表示・フォーム保存（`/tasks/**`）
  - `TaskRestController` : 検索・ステータス更新・削除・並び替えの REST API（`/api/tasks/**`）
  - `CategoryViewController` : カテゴリ管理画面（`/categories/**`）
  - `RestApiExceptionHandler` : REST API 用の例外ハンドラ（`IllegalArgumentException` を 404 に変換）
- `com.example.todo.service` - ビジネスロジック（`TaskService`, `CategoryService`）
- `com.example.todo.repository` - 永続化（`TaskRepository`, `CategoryRepository`, `TaskSpecifications`、Spring Data JPA）

---

## 前提

- Java 21
- Maven（プロジェクト同梱の `mvnw` / `mvnw.cmd` を利用可能）
- MySQL または MariaDB（データベース名: `tododb`、文字コード: `utf8mb4` 推奨）

※ Windows でのコマンド例は cmd.exe 用に記載しています。

---

## データベースの準備

`src/main/resources/application.properties` に接続設定があります。

- 接続先: `jdbc:mysql://localhost:3306/tododb`
- ユーザー名/パスワードは環境変数 `DB_USERNAME` / `DB_PASSWORD` で上書きできます（未設定時は開発用デフォルト値 `root` / `root` を使用）
- `spring.jpa.hibernate.ddl-auto=update` になっているため、テーブル未作成でもアプリ起動時に自動生成されます（`tododb` データベース自体は事前に作成しておく必要があります）

```sql
CREATE DATABASE tododb CHARACTER SET utf8mb4;
```

環境変数の設定例（Windows cmd.exe）:

```cmd
set DB_USERNAME=root
set DB_PASSWORD=your_local_password
mvnw.cmd spring-boot:run
```

※ 実際のDBパスワードを `application.properties` に直書きしてコミットしないでください。

---

## ビルドと実行

プロジェクトルート（この README があるディレクトリ）で以下を実行します。

ビルド（パッケージ作成）:

```cmd
mvnw.cmd clean package
```

アプリを起動（Maven 経由）:

```cmd
mvnw.cmd spring-boot:run
```

起動後、ブラウザで下記を開くとタスク一覧画面が表示されます。

```
http://localhost:8080/tasks
```

カテゴリ管理画面:

```
http://localhost:8080/categories
```

実行時にポートを変更したい場合は、`--server.port` オプションを指定できます。

```cmd
mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8081"
```

---

## 画面（Thymeleaf）

- タスク一覧画面
  - パス: `GET /tasks`
  - 各行にステータス選択用の `<select>`、優先度バッジ、カテゴリバッジ、タスク名、期限、登録日時、編集・削除ボタンを表示
  - フィルタ・検索バー（ステータス／期限／優先度／カテゴリ／キーワード）を画面上部に表示。選択・入力中の項目はハイライト表示される
  - 行をドラッグ＆ドロップして並び替え可能（絞り込み「すべて」表示時のみ。絞り込み中は無効化される）
  - タスクが0件の場合は「タスクはありません」と表示
  - `DONE` は取り消し線、`CANCELLED` は取り消し線＋斜体、`IN_PROGRESS`／`ON_HOLD` は背景色で区別
  - 期限切れタスクは赤字＋「期限切れ」ラベル、当日期限はオレンジ色＋「本日期限」ラベルで強調
- タスク追加画面
  - パス: `GET /tasks/new`
  - 入力項目はタスク名（必須）、詳細（任意）、優先度、期限、カテゴリ。ステータス選択欄は表示されず、保存時に常に `TODO` になる
- タスク編集画面
  - パス: `GET /tasks/{id}/edit`
  - 追加画面の項目に加え、ステータスの選択欄が表示される
- 保存
  - パス: `POST /tasks`（新規作成・更新共通）
  - バリデーションエラー時（タイトル未入力・文字数超過など）はフォームを再表示
- カテゴリ管理画面
  - パス: `GET /categories`
  - カテゴリ名（必須・重複不可・20文字以内）とラベル色を指定して新規作成
  - 削除時、そのカテゴリに属していたタスクは自動的に「カテゴリなし」になる

---

## REST API（Ajax用）

一覧画面からの検索・状態変更・削除・並び替えを扱う JSON API です。

- タスク検索
  - `GET /api/tasks?status=&due=&priority=&category=&keyword=`
  - 全パラメータ任意。複数指定時はAND結合で絞り込む
    - `status`: `TaskStatus` の値（例: `TODO`）。未指定・空文字は「すべて」
    - `due`: `TODAY_OR_EARLIER`（今日まで）／`OVERDUE`（期限切れのみ）。未指定・空文字は「すべて」
    - `priority`: `Priority` の値（例: `HIGH`）。未指定・空文字は「すべて」
    - `category`: カテゴリIDの数値文字列／`NONE`（カテゴリなし）。未指定・空文字は「すべて」
    - `keyword`: タイトル・詳細に対する部分一致・大文字小文字を区別しない検索
  - 不正な値が指定された場合は `400 Bad Request`

- ステータス更新
  - `PATCH /api/tasks/{id}/status`
  - リクエストボディ例: `{"status":"DONE"}`
  - `status` を `DONE` にすると `completedAt` に現在日時が設定され、それ以外に変更すると `null` に戻る
  - 不正なステータス文字列の場合は `400 Bad Request`
  - 存在しない `id` の場合は `404 Not Found`

- タスク削除
  - `DELETE /api/tasks/{id}`
  - 成功時は `200 OK`
  - 存在しない `id` を指定した場合も、例外を投げずに `200 OK`

- 並び替え
  - `PUT /api/tasks/reorder`
  - リクエストボディ例: `{"taskIds":[3,1,2]}`（ドロップ後の並び順どおりのIDリスト）
  - 渡された順序で `sortOrder`（0始まりの連番）を一括更新する
  - `taskIds` が未指定の場合は `400 Bad Request`

Windows (cmd.exe) での curl 例:

```cmd
curl "http://localhost:8080/api/tasks?status=IN_PROGRESS&priority=HIGH"

curl -X PATCH "http://localhost:8080/api/tasks/1/status" -H "Content-Type: application/json" -d "{\"status\":\"DONE\"}"

curl -X PUT "http://localhost:8080/api/tasks/reorder" -H "Content-Type: application/json" -d "{\"taskIds\":[3,1,2]}"

curl -X DELETE "http://localhost:8080/api/tasks/1"
```

---

## フロントエンド（静的リソース）

- `src/main/resources/static/js/app.js`
  - ページ読み込み時・フィルタ変更時に `GET /api/tasks` を呼び出し、`#taskTableBody` の中身をAjaxで再構築する（画面全体は再読み込みしない）
  - ステータス選択（`.status-select`）の `change` イベントで `PATCH /api/tasks/{id}/status` を呼び出し、成功後に一覧を再取得する
  - 削除ボタン（`.btn-delete`）の `click` イベントで `confirm()` 表示後 `DELETE /api/tasks/{id}` を呼び出し、成功後に一覧を再取得する
  - jQuery UI の `sortable()` により行のドラッグ＆ドロップを実現し、ドロップ時に `PUT /api/tasks/reorder` へ新しい順序を送信する（絞り込み中は無効化）
  - いずれもエラー時はサーバーからのエラーメッセージ（`xhr.responseText`）を `alert()` で表示する
- `src/main/resources/static/css/` - 一覧・フォーム・カテゴリ管理画面のスタイル

---

## 開発のヒント

- `TaskStatus` / `Priority` は表示用の日本語名（`displayName`）を保持しており、Thymeleaf テンプレート側で利用しています。
- `Task` エンティティの `status` / `priority` は `@Enumerated(EnumType.STRING)` で保存されるため、DB には定数名がそのまま文字列で保存されます。
- `status` と `sortOrder` は、ユーザーが直接入力しない項目のため Bean Validation の対象にせず、`@PrePersist`（`Task#onCreate()`）内で null の場合にデフォルト値を補完しています。同様に `priority` も未設定時は `MEDIUM` を補完します。
- `Task#categoryId` は「カテゴリなし」を許容する任意項目のため、デフォルト値の補完は行いません（null がそのまま「カテゴリなし」を表す正しい状態）。
- タスク検索は `TaskRepository`（`JpaSpecificationExecutor`）と `TaskSpecifications` を使い、条件ごとに独立した `Specification` をAND結合して組み立てています。
- DB接続や各種プロパティの変更は `src/main/resources/application.properties` を編集してください。

---

## テスト

テスト実行:

```cmd
mvnw.cmd test
```

`src/test/java/com/example/todo` 配下にコントローラー・サービス層のテストがあります。`pom.xml` に JaCoCo プラグインが設定されているため、テスト実行後に `target/site/jacoco/index.html` でカバレッジレポートを確認できます。

---

## ソースを確認する場所

- モデル: `src/main/java/com/example/todo/model/`（`Task.java`, `TaskStatus.java`, `Priority.java`, `Category.java`）
- コントローラー: `src/main/java/com/example/todo/controller/`（`TaskViewController.java`, `TaskRestController.java`, `CategoryViewController.java`, `RestApiExceptionHandler.java`）
- サービス: `src/main/java/com/example/todo/service/`（`TaskService.java`, `CategoryService.java`）
- リポジトリ: `src/main/java/com/example/todo/repository/`（`TaskRepository.java`, `CategoryRepository.java`, `TaskSpecifications.java`）
- 画面テンプレート: `src/main/resources/templates/tasks/`（`list.html`, `form.html`）、`src/main/resources/templates/categories/`（`list.html`）
- フロントエンドJS: `src/main/resources/static/js/app.js`

---

## 今後の実装予定（作業指示書より）

- フェーズ3: サブタスク（`parentTaskId`）、繰り返しタスク（`recurrenceRule`）
- フェーズ4: 認証・ユーザー管理（`ownerUserId`）、通知、API化

---

最終更新日: 2026-09-16
