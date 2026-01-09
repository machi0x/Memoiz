---
title: "メモイズ プライバシーポリシー / Memoiz Privacy Policy"
permalink: /privacy/
layout: default
lang: ja
description: "Privacy policy for Memoiz (日本語 / English). Explains data collected and opt-out information."
---

# Memoiz プライバシーポリシー / Memoiz Privacy Policy

最終更新日 / Last updated: 2026-01-09

---

日本語

## 概要
Memoiz（以下「本アプリ」）は、ユーザーのプライバシーを重視しています。本ページでは、本アプリが収集するデータ、収集の目的、ユーザーの選択（同意／拒否）の方法、問い合わせ先について分かりやすく説明します。

## 重要ポイント（簡単）
- 本アプリは、利用状況の把握と不具合対応のために Firebase（Google）のサービスを利用します。
  - Firebase Analytics（利用状況のイベント）
  - Firebase Crashlytics（クラッシュレポート）
- メモ本文、画像などユーザーが作成したコンテンツは、アプリ側から Firebase に送信されません。
- ユーザーは同意を拒否できます。アプリの設定画面から「送信する」トグルをオフにしてください（初回起動時のチュートリアルでも同意／拒否を選べます）。
- このアプリはオープンソースで、ソースコードは https://github.com/machi0x/Memoiz に公開されています。
- お問い合わせは専用メール（jmowase@gmail.com）または GitHub Issues をご利用ください。

## 収集されるデータ（詳細）
1) Firebase Analytics（イベント）
- 目的：機能利用の傾向把握、改善のための集計。
- 送信される主なイベント名と例のパラメータ（アプリ内部のイベント名をそのまま記載しています）：
  - `tutorial_main_ui_view`
  - `about_thanks_view`
  - `usage_stats_permission_status`（パラメータ：`is_granted` = true/false）
  - `startup_sort_key`（パラメータ：`sort_key`）
  - `startup_my_category_count`（パラメータ：`my_category_count_range`）
  - `startup_memo_stats_ranges`（パラメータ：`text_memo_count_range`, `web_memo_count_range`, `image_memo_count_range`）
  - `memo_created`（パラメータ：`creation_source` = e.g. "clipboard" / "share" / "manual"）
- 備考：これらはイベント名・集計用のラベルやレンジ等であり、個別のメモ本文や画像などの機密データは含みません。

2) Firebase Crashlytics（クラッシュレポート）
- 目的：アプリの安定性向上と不具合の解析。
- 収集される情報の例：スタックトレース、例外情報、アプリバージョン、デバイスモデル、OSバージョン、メモリやプロセスの状態など。
- 備考：クラッシュレポートにメモ本文・画像などユーザーのコンテンツは含めないよう設計しています（コード上でも明示的にメモ本文を送信していません）。

## 同意（Consent）と撤回（Opt-out）
- 初回起動時のチュートリアルで同意を求めます。ユーザーが「同意しない」選択をした場合、Analytics と Crashlytics の収集は無効になります。
- 後から設定を変更するには：アプリ内の「設定」→「送信する（Send usage stats）」トグルをオフにしてください。オフにすると、アプリは Firebase Analytics の収集を無効化し、Crashlytics の収集も停止するようにしています（即時反映されますが、すでに送信済みのデータの削除は Firebase 側のポリシーに従います）。

## データの保管・第三者について
- 収集されたイベント・クラッシュ情報は Firebase（Google）が管理するサーバーに送信されます。詳細な取り扱いやデータ保持期間は Firebase / Google のポリシーに従います：
  - Firebase のプライバシー情報: https://firebase.google.com/support/privacy
  - Google のプライバシー情報: https://policies.google.com/privacy

## ユーザーの権利（アクセス・削除など）
- ご自身のデータの開示、訂正、削除を希望される場合は、まず専用メール（jmowase@gmail.com）または GitHub Issues でご連絡ください。対応には時間を要する場合があります。また、サーバー側（Firebase/Google）に保存されたデータの削除は、当方から Firebase の管理画面を通じて対応しますが、Firebase の仕様や保持期間のため即時にすべて削除できない場合があります。

## OSS とソースコード
- 本アプリはオープンソースです： https://github.com/machi0x/Memoiz
- ソースコードはライセンス（リポジトリ内の LICENSE）に従います。実装を確認したい場合、`app/src/main/java/com/machi/memoiz/analytics/AnalyticsManager.kt` などを参照してください。

## 問い合わせ
- メール: jmowase@gmail.com
- GitHub Issues: https://github.com/machi0x/Memoiz/issues

---

English

## Summary
Memoiz ("the app") respects user privacy. This page explains what data the app collects, why it is collected, how you can opt out, and how to contact the developer.

## Key points (short)
- The app uses Firebase services (Google) for usage analytics and crash reporting:
  - Firebase Analytics (usage events)
  - Firebase Crashlytics (crash reports)
- User-created content such as memo text or images is not uploaded to Firebase by the app.
- You can decline or revoke consent in the app settings (the "Send usage stats" toggle). You can also decline during the first-run tutorial.
- The app is open-source: https://github.com/machi0x/Memoiz
- Contact: jmowase@gmail.com or open an issue on GitHub.

## What data is collected (details)
1) Firebase Analytics (events)
- Purpose: aggregated usage statistics and product improvement.
- Example event names and parameters (these match internal event names used in the app):
  - `tutorial_main_ui_view`
  - `about_thanks_view`
  - `usage_stats_permission_status` (param: `is_granted` = true/false)
  - `startup_sort_key` (param: `sort_key`)
  - `startup_my_category_count` (param: `my_category_count_range`)
  - `startup_memo_stats_ranges` (params: `text_memo_count_range`, `web_memo_count_range`, `image_memo_count_range`)
  - `memo_created` (param: `creation_source` = e.g. "clipboard" / "share" / "manual")
- Note: These are event names, labels, and bucketed ranges used for analytics. They do NOT include memo texts or image content.

2) Firebase Crashlytics (crash reports)
- Purpose: diagnose crashes and improve app stability.
- Typical data: stack traces, exception messages, app version, device model, OS version, memory/process state, etc.
- Note: Crash reports are designed not to include memo contents or user-created files.

## Consent and opt-out
- On first run the app asks for consent in the tutorial. Choosing "No" disables Analytics and Crashlytics collection.
- To change your preference later: open Settings → toggle off "Send usage stats". Turning this off disables Firebase Analytics collection and stops Crashlytics collection. Data already transmitted to Firebase before you opted out may remain according to Firebase retention policies.

## Storage and third parties
- Collected events and crash reports are sent to Firebase (Google) servers. For details about how Firebase/Google handle data, please see:
  - Firebase privacy: https://firebase.google.com/support/privacy
  - Google privacy: https://policies.google.com/privacy

## Your rights (access, deletion)
- If you request access, correction, or deletion of data, contact jmowase@gmail.com or open a GitHub Issue. We will assist and, when applicable, request deletion via Firebase. Note that deletion may be subject to Firebase retention rules and may not be instantaneous.

## Open source
- Source code: https://github.com/machi0x/Memoiz
- See implementation (for example): `app/src/main/java/com/machi/memoiz/analytics/AnalyticsManager.kt`

## Contact
- Email: jmowase@gmail.com
- GitHub Issues: https://github.com/machi0x/Memoiz/issues

---

Notes for publishing and Play Store

1) Publish this file via GitHub Pages: enable Pages to serve the repository from the `docs/` folder (Repository settings → Pages → Source: `main` branch / `docs` folder). The public URL will typically be `https://machi0x.github.io/Memoiz/privacy` or similar — confirm after enabling.

2) In Google Play Console, add the published URL to the "Privacy Policy" field for your app listing.

3) Play Store short text suggestion (Japanese / English):
- JP（短文）: プライバシー：本アプリは利用状況分析とクラッシュレポートに Firebase を使用します。メモ本文や画像は送信しません。詳しくはプライバシーポリシーをご覧ください。
- EN (short): Privacy: The app uses Firebase for analytics and crash reporting. Memo contents or images are not uploaded. See the privacy policy for details.

If you want, I can:
- Create the GitHub Pages configuration PR / instructions to enable Pages,
- Add a short link in your repository `README.md` pointing to the privacy page,
- Prepare the exact Play Console copy (localized) and screenshots if needed.
