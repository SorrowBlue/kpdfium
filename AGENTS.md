# Project Guidelines (SSoT) - kpdfium

このファイルは `kpdfium` プロジェクト固有の技術的意思決定、アーキテクチャ方針、ガイドラインを管理する唯一の真実のソース（SSoT）です。

## プロジェクト目標
Google の PDFium をバックエンドとした、高パフォーマンスな Kotlin Multiplatform (KMP) PDF ライブラリの開発。

## 技術スタック & バージョン
- **Kotlin**: `2.3.21` (JVM & Android targets)
- **Java**: `21` (JvmToolchain vendor: ADOPTIUM)
- **Android Gradle Plugin (AGP)**: `9.2.1`
- **Gradle**: `9.5.1`

## 優先サポートプラットフォーム（第一フェーズ）
1. **JVM (Desktop)**: JNA (Java Native Access) による `pdfium` の動的ロードおよび描画。
2. **Android**: Android NDK JNI 経由での C++ ブリッジによる `libpdfium.so` のゼロコピー（`AndroidBitmap_lockPixels`）描画。

## コミット規約
- すべてのコミットは `Conventional Commits` 形式に従い、コミットメッセージは `.agent/rules/git-commit-rules.md` に基づいて生成してください。

## ガイドライン
- 新しいタスクの着手前には、必ず `.agent/rules/indexing-codebase.md` の手順に従いプロジェクト構造を把握してください。
- 破壊的な変更や大規模なリファクタリングの前には、`/plan` ワークフローを用いて計画書を提示し、ユーザーの承認（Approve）を得てください。
- ユーザー向けのテキストおよび成果物（マークダウンや設計書等）は、必ず**日本語**で記述してください。

## 参照先
- **行動規範:** `.agent/rules/` を参照してください。
- **ワークフロー:** `.agent/workflows/` を参照してください。
- **スキル:** `.agent/skills/` を参照してください。