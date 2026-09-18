# Android

MealRelay の Android アプリです。後続 Issue で写真検知や手動入力などの機能を追加するための、最小限の Android プロジェクトを配置しています。

現時点ではランチャー Activity を含まず、通常のメイン画面や機能 UI はありません。

対象環境は Android 17 です。Android 17 より古い OS には対応しません。
`minSdk`、`compileSdk`、`targetSdk` はすべて 37 です。

## 構成

- `app/`: Android アプリケーションモジュール

`FoodClassifier` は、同梱した量子化モデルを LiteRT で実行し、`Bitmap` を端末内だけで food / non-food に分類します。画像は縦横比を維持して224 × 224へ縮小し、余白を黒で補います。判定のための通信や外部API呼び出しはありません。

デバッグ用APKに宣言済み権限がないこと、および16 KBページ向けZIPアラインメントを確認済みです。

モデル、閾値、学習データおよび評価結果は [../ml/README.md](../ml/README.md) に記録しています。

## 必要な環境

- JDK 25
- Android SDK Platform 37

## 検証

`android/` ディレクトリで次を実行します。

```shell
./gradlew build
```

ビルド後のデバッグ用 APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

起動中のAndroidエミュレーターまたは実機で、モデルを含む計装テストを実行する場合は次を実行します。

```shell
./gradlew connectedAndroidTest
```

計装テストの画像は Food-5K の評価用分割から選んだ食事1枚と風景1枚です。配布元が表示するライセンスは CC0 1.0 です。
