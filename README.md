# TODO アプリケーション

Spring Boot（Java 21 / Maven）で実装した、タスク管理アプリケーションです。
画面表示は Thymeleaf、ステータス変更・削除などの即時反映操作は jQuery による Ajax 通信で行います。

現時点の実装範囲は、作業指示書のフェーズ1（基本CRUD・ステータス管理）です。

---

## 概要

- タスクは5段階のステータス（未着手・進行中・保留・完了・中止）を持ちます。
- 一覧画面（Thymeleaf）でタスクの追加・編集・ステータス変更・削除ができます。
- ステータス変更と削除は、画面を再読み込みせず jQuery の Ajax（`/api/tasks/**`）経由で即時反映します。
- データの永続化には MySQL / MariaDB + Spring Data JPA を使用します。

### 主なパッケージ構成

- `com.example.todo.model` - ドメインモデル（`Task` エンティティ、`TaskStatus` enum）
- `com.example.todo.controller` - コントローラー
  - `TaskViewController` : 画面表示・フォーム保存（`/tasks/**`）
  - `TaskRestController` : ステータス更新・削除の REST API（`/api/tasks/**`）
  - `RestApiExceptionHandler` : REST API 用の例外ハンドラ（`IllegalArgumentException` を 404 に変換）
- `com.example.todo.service` - ビジネスロジック（`TaskService`）
- `com.example.todo.repository` - 永続化（`TaskRepository`、Spring Data JPA）

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

実行時にポートを変更したい場合は、`--server.port` オプションを指定できます。

```cmd
mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8081"
```

---

## 画面（Thymeleaf）

- タスク一覧画面
  - パス: `GET /tasks`
  - 各行にステータス選択用の `<select>`、タスク名、登録日時、編集・削除ボタンを表示
  - タスクが0件の場合は「タスクはありません」と表示
  - `DONE` は取り消し線、`CANCELLED` は取り消し線＋斜体、`IN_PROGRESS`／`ON_HOLD` は背景色で区別
- タスク追加画面
  - パス: `GET /tasks/new`
  - 入力項目はタスク名（必須）と詳細（任意）のみ。ステータス選択欄は表示されず、保存時に常に `TODO` になる
- タスク編集画面
  - パス: `GET /tasks/{id}/edit`
  - 追加画面の項目に加え、ステータスの選択欄が表示される
- 保存
  - パス: `POST /tasks`（新規作成・更新共通）
  - バリデーションエラー時（タイトル未入力・文字数超過など）はフォームを再表示

---

## REST API（Ajax用）

一覧画面からの状態変更・削除のみを扱う、小さな JSON API です。

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

Windows (cmd.exe) での curl 例:

```cmd
curl -X PATCH "http://localhost:8080/api/tasks/1/status" -H "Content-Type: application/json" -d "{\"status\":\"DONE\"}"

curl -X DELETE "http://localhost:8080/api/tasks/1"
```

---

## フロントエンド（静的リソース）

- `src/main/resources/static/js/app.js`
  - ステータス選択（`.status-select`）の `change` イベントで `PATCH /api/tasks/{id}/status` を呼び出し、成功時はその行の見た目（クラス）だけを切り替える
  - 削除ボタン（`.btn-delete`）の `click` イベントで `confirm()` 表示後 `DELETE /api/tasks/{id}` を呼び出し、成功時は該当行を DOM から削除する
  - いずれもエラー時はサーバーからのエラーメッセージ（`xhr.responseText`）を `alert()` で表示する
- `src/main/resources/static/css/` - 一覧・フォーム画面のスタイル

---

## 開発のヒント

- `TaskStatus` は表示用の日本語名（`displayName`）を保持しており、Thymeleaf テンプレート側で `st.displayName` として利用しています。
- `Task` エンティティの `status` は `@Enumerated(EnumType.STRING)` で保存されるため、DB には `TODO` `DONE` のような定数名がそのまま文字列で保存されます。
- `status` と `sortOrder` は、ユーザーが直接入力しない項目のため Bean Validation の対象にせず、`@PrePersist`（`Task#onCreate()`）内で null の場合にデフォルト値を補完しています。
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

- モデル: `src/main/java/com/example/todo/model/`（`Task.java`, `TaskStatus.java`）
- コントローラー: `src/main/java/com/example/todo/controller/`（`TaskViewController.java`, `TaskRestController.java`, `RestApiExceptionHandler.java`）
- サービス: `src/main/java/com/example/todo/service/`（`TaskService.java`）
- リポジトリ: `src/main/java/com/example/todo/repository/`（`TaskRepository.java`）
- 画面テンプレート: `src/main/resources/templates/tasks/`（`list.html`, `form.html`）
- フロントエンドJS: `src/main/resources/static/js/app.js`
- 設計ドキュメント: `docs/architecture.md`

---

## 今後の実装予定（作業指示書より）

- フェーズ2: 期限（`dueDate`）、優先度（`priority`）、カテゴリ（`categoryId`）、フィルタ・検索、並び替え
- フェーズ3: サブタスク（`parentTaskId`）、繰り返しタスク（`recurrenceRule`）
- フェーズ4: 認証・ユーザー管理（`ownerUserId`）、通知、API化

---

最終更新日: 2026-09-10
