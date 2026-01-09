# THIRD-PARTY NOTICES

このプロジェクトで利用しているサードパーティライブラリおよびフォントのライセンス情報をまとめた文書です。

※ 詳細かつ全文は `licenses/` ディレクトリ内のファイルを参照してください（`licenses/third_party/third_party_licenses.txt` 等）。

---

## 概要
- 本リポジトリには、ビルド時に Gradle の Google OSS Licenses プラグインで生成されたサードパーティライセンス情報を収集し、`licenses/` 以下に配置しています。
- アプリ内の「Open Source Licenses」画面はビルド生成物（`app/src/main/res/raw/third_party_licenses`）を参照して表示します。`licenses/` はリポジトリ上でライセンスを確認しやすくするためのコピー／抽出です。

## 含まれる主なライブラリ／フォント（抜粋）
以下は `licenses/third_party/components/` および `licenses/fonts/` に抽出された代表的な項目です（完全一覧は `licenses/third_party/third_party_licenses.txt` を参照してください）。

- フォント
  - MPlus 1 Code Regular
    - ライセンス: SIL Open Font License 1.1 (OFL-1.1)
    - ソース: https://mplus-fonts.osdn.jp/ 等
    - ファイル: `licenses/fonts/Yomogi-LICENSE.txt`（抽出ファイル）および `licenses/third_party/components/MPlus_1_Code_Regular.txt` 等
  - Yomogi
    - ライセンス: SIL Open Font License 1.1 (OFL-1.1)
    - ソース: https://github.com/google/fonts 等
    - ファイル: `licenses/fonts/Yomogi-LICENSE.txt`

- その他のサードパーティライブラリ
  - すべての依存ライブラリの完全なライセンス一覧は `licenses/third_party/third_party_licenses.txt` を参照してください。
  - 個別ファイルは `licenses/third_party/components/` に分割されています。

## ファイル位置（リポジトリ内）
- `licenses/third_party/third_party_licenses.txt` — 生成された結合版のライセンス全文（全依存分）
- `licenses/third_party/third_party_license_metadata.json` — メタデータ
- `licenses/third_party/components/` — コンポーネントごとの分割ファイル
- `licenses/fonts/` — 抽出したフォントのライセンスファイル（OFL 等）

## 生成手順（再現方法）
ローカルや CI で新しい依存が追加されたときにライセンスを再生成する手順:

```powershell
# プロジェクトルートで実行
.
\gradlew.bat :app:mergeThirdPartyLicenses -PwriteThirdPartyRes=true
# 生成後、app/src/main/res/raw/ の生成物を licenses/ にコピーする
# 例（PowerShell）:
Copy-Item -Path app\src\main\res\raw\third_party_licenses -Destination licenses\third_party\third_party_licenses.txt -Force
Copy-Item -Path app\src\main\res\raw\third_party_license_metadata -Destination licenses\third_party\third_party_license_metadata.json -Force
```

## 注意事項
- 生成済みの `licenses/` ファイルはビルド生成物からのコピーです。CI で自動生成・更新するフローを作ることを推奨します。そうすれば依存更新時に自動で `licenses/` が更新され PR に反映できます。
- フォントのライセンス（OFL など）は埋め込みや配布に関する条件を含む場合があるため、配布（APK や再配布）時に該当ライセンス文を含めることを忘れないでください。

---

必要なら、この `THIRD_PARTY_NOTICES.md` を README の冒頭やライセンス節に目立つ形でリンク追加します（既に README のライセンス節にリンクを追加済みですが、別場所へも移したい場合は指示してください）。

