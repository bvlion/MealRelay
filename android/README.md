# Android

MealRelay の Android アプリです。後続 Issue で写真検知や手動入力などの機能を追加するための、最小限の Android プロジェクトを配置しています。

現時点ではランチャー Activity を含まず、通常のメイン画面や機能 UI はありません。

対象環境は Android 17 です。Android 17 より古い OS には対応しません。
`minSdk`、`compileSdk`、`targetSdk` はすべて 37 です。

## 構成

- `app/`: Android アプリケーションモジュール

## 必要な環境

- JDK 25
- Android SDK Platform 37

## 検証

`android/` ディレクトリで次を実行します。

```shell
./gradlew build
```

ビルド後のデバッグ用 APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。
