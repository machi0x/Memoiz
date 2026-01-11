# Cat Comment 機能 — 実装メモ（一時）

作成日: 2026-01-11
作成者: 自動生成ドキュメント

---

## 要約
このドキュメントは、Memoiz アプリに追加した「メモイズから一言（Cat Comment）」機能の設計、実装状況、現時点でのファイル差分、ビルド／動作確認手順、残タスクをまとめた一時メモです。別のPC で作業を続けるための参照資料として使ってください。

目的: ユーザーの既存メモをランダム（最近のものにバイアス）で選び、GenAI（on-device ML Kit Generation）に対して「男性の子猫」になりきって短く心温まる一言（最大3文）を生成させ、感情ラベルを対応する子猫イラストで表示する。

---

## 高レベル設計（変更点の要約）
- FAB メニューに「メモイズから一言」ボタンを追加（`MainScreen.kt`）
  - 表示条件: GenAI の text generation が AVAILABLE の場合のみ
- タップで新規 Activity（ダイアログ風） `CatCommentDialogActivity` を起動
  - 起動後は indeterminate progress を表示し、処理中メッセージをランダムに切り替え（20 種類の英日メッセージ）
  - 最短 5 秒は progress 部分を表示する（クールダウン）
- メモ選択: 重み付きランダム（最新5件 weight=5、次の5件 weight=3、それ以降 weight=1）
- GenAI 呼び出し: `CatCommentService` で ML Kit `Generation` クライアントを利用
  - 12 秒タイムアウトを採用（withTimeoutOrNull）
  - プロンプト文はリソース化（EN/JA の `strings.xml`）に移動済み
  - 出力フォーマットを厳密に指示: コメント（<=3文）行のあとに1行で feeling ラベル（confused, cool, curious, difficult, happy, neutral, thoughtful）
- 表示: 選択したメモのプレビュー（画像/テキスト/URL 簡易表示） + AI コメント + 子猫イメージ + Close ボタン
- 例外時の UI: メモ無し / AI 応答無し の場合に固定メッセージ＋該当イラストを表示

---

## 主要設計・実装上の注意
- 「AI がメモの内容をどう扱うか」はオンデバイス生成を前提にしています。将来外部 API を使う場合はプライバシー方針の見直しが必要。
- プロンプトは string resource に切り出しています (`catcomment_prompt_template_en` / `_ja`)。テンプレートは %1$s で memo data を受け取ります。
- AI 出力のパースは簡易（最後の行にラベルを含む想定）。将来的には JSON 出力を要求してパースを厳密化したほうが堅牢。

---

## 変更済み・追加ファイル（現時点）
下はワークツリー（`app` モジュール）の相対パスです。

編集／追加済み:

- 編集: `app/src/main/res/values/strings.xml`
  - 追加: `fab_cat_comment_label`, `catcomment_close`, `catcomment_no_memo_message`, `catcomment_no_response_message`, `catcomment_processing_array`, `catcomment_processing_prefix`, `catcomment_prompt_template_en`

- 編集: `app/src/main/res/values-ja/strings.xml`
  - 追加: 日本語版の上記項目（`catcomment_prompt_template_ja` も追加）

- 編集: `app/src/main/java/com/machi/memoiz/ui/screens/MainScreen.kt`
  - 変更: Expanded FAB リストに "メモイズから一言" ボタンを追加
  - 軽量 GenAI の textGeneration AVAILABLE チェックを追加（起動時に1回）

- 追加: `app/src/main/java/com/machi/memoiz/service/CatCommentService.kt`
  - ML Kit Generation クライアントを使い、プロンプトをテンプレートから作成して生成を実行。12 秒のタイムアウトを適用。
  - 出力から感情ラベルを抽出し、最大3文に切り詰めて返す `CatCommentResult` を返却。

- 追加: `app/src/main/java/com/machi/memoiz/ui/dialog/CatCommentDialogActivity.kt`
  - Compose ベースのダイアログ風 Activity。処理中表示 → 生成結果表示までの一連 UI を実装。

- 追加: `app/src/main/java/com/machi/memoiz/ui/components/MemoPreview.kt`
  - ダイアログ内で使用する簡易メモプレビュー（Campus note 風の簡易コピー）。既存の UI を壊さないよう、独立した小さな実装。

- 既存 Drawables を利用:
  - 感情に対応する画像: `drawable-xxhdpi/confused.png`, `cool.png`, `curious.png`, `difficult.png`, `happy.png`, `neutral.png`, `thoughtful.png`

---

## 現在の進捗（チェックリスト）
- [x] EN/JP の string リソース追加（プロンプトテンプレート含む）
- [x] FAB ボタンの追加（MainScreen）
- [x] ダイアログ Activity の追加（Compose）
- [x] CatCommentService の追加（Generation 呼び出し、タイムアウト、パース）
- [x] MemoPreview コンポーネント追加
- [x] ビルド静的チェック（致命エラーなし。警告あり）

残タスク / 未実施（推奨）:
- [ ] エンドツーエンド動作テスト（実機 or エミュレータ、on-device AI インストール済み）
- [ ] 出力パースの強化（ラベル抽出のロバスト化）
- [ ] ユニットテスト：`CatCommentService.buildPrompt()` と出力パーサのテスト
- [ ] メイン UI のリファクタ（共通プレビューの共通化。今回の `MemoPreview` は副次的）
- [ ] UI 微調整（レイアウト、文字サイズ、アクセシビリティ）

---

## ビルド & 動作確認手順（別PCでの再現）
1. リポジトリをクローン／更新して、依存を同期。
2. Windows PowerShell（プロジェクトルート）でビルド:

```powershell
# プロジェクトルートで
.\gradlew.bat assembleDebug
```

3. 実機にインストールして起動（Android Studio から Run でも可）。
4. 動作確認手順:
   - メイン画面で FAB をタップしてメニューを展開
   - GenAI が利用可能な端末なら「メモイズから一言」ボタンが見えるのでタップ
   - ダイアログが開き「XXX中」表示 → 最短5秒後に結果（またはエラー）表示
   - 結果は: メモプレビュー、生成された短いコメント、対応する子猫画像、Close

ヒント: エミュレータでは on-device AI の利用可否により FAB が表示されない場合があります。そのときは `GenAiStatusCheckDialogActivity` の既存ロジックや `GenAiStatusManager` の状態を参照してください。

---

## 重要な設計上の決定と理由
- プロンプトはリソース化しました（EN/JA）: ローカライズしやすく、将来テンプレ改訂や A/B テストがしやすい
- 12s timeout + 最短5s 表示: ユーザーが連打したときの UX を穏やかにするため。12s はユーザー要望。
- 感情ラベルは応答の末尾1行で返す指示にしたがい抽出する簡易実装。堅牢化は次フェーズ推奨。

---

## 次にやること（優先順）
1. 別 PC でのビルドと実機テスト。動作に問題があればログ（Logcat）を確認して原因を修正。
2. `CatCommentService` の出力パースを改善（正規表現でラベルを明示的に抽出、ラベルが無ければ安全 fallback）
3. ユニットテスト追加（Kotlin/JUnit）
4. 必要なら UI の文言やテンプレートを微調整（UX チームに確認）

---

## 参考: 主要関係コード位置
- FAB 表示 / 起動: `app/src/main/java/com/machi/memoiz/ui/screens/MainScreen.kt`
- ダイアログ Activity: `app/src/main/java/com/machi/memoiz/ui/dialog/CatCommentDialogActivity.kt`
- AI 呼び出しサービス: `app/src/main/java/com/machi/memoiz/service/CatCommentService.kt`
- メモリポジトリ: `app/src/main/java/com/machi/memoiz/data/repository/MemoRepository.kt`（ダイアログで即時取得している）
- 文字列テンプレート: `app/src/main/res/values/strings.xml` / `app/src/main/res/values-ja/strings.xml`
- 画像リソース（感情）: `app/src/main/res/drawable-xxhdpi/{confused,cool,curious,difficult,happy,neutral,thoughtful}.png`

---

## よくあるトラブルと対処
- FAB が表示されない: 端末に on-device GenAI がインストールされていないか、`GenAiStatusManager` が AVAILABLE を返していない可能性。既存の GenAI ステータスチェックダイアログ（`GenAiStatusCheckDialogActivity`）を確認。
- 生成が毎回失敗する: ML Kit GenAI のモデルがダウンロードされていない、またはネットワーク／端末の制約で内部エラーが出ている可能性。ログ（Logcat）で `CatCommentService` の TAG を確認。
- 感情ラベルが抽出されない: モデルがラベル行を出さない場合がある。テンプレートをさらに厳密に指定するか、出力を解析するロジックを強化する。

---

## メモ（開発者向け）
- 現在は `MemoPreview` をダイアログ専用に作成しています。Main UI の既存 Campus note 表示を共通化したい場合は、`CampusNoteTextAligned` の抽出／公開化を検討してください。
- `CatCommentService` の `promptModel` は `Generation.getClient(GenerationConfig.Builder().build())` を使っています。必要に応じて `GenerationConfig` の設定を見直してください。

---

## 最後に
何か追加でこのファイルに含めたい項目（ログのサンプル、特定のテストケース、UI スクショ等）があれば教えてください。別PC でも継続しやすいように、追加のコンテキストや調査結果をこのファイルに追記していくのが良いです。


<!-- EOF -->

