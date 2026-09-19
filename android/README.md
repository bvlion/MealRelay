# Android

MealRelay の Android アプリです。

通常のメイン画面はありません。初回起動時に写真へのフルアクセスを許可すると、標準カメラで新しく撮影した写真をバックグラウンドで検知して端末内で分類します。「選択した写真のみ」の権限では自動検知を開始しません。

対象環境は Android 17 です。Android 17 より古い OS には対応しません。
`minSdk`、`compileSdk`、`targetSdk` はすべて 37 です。

## 構成

- `app/`: Android アプリケーションモジュール

Kotlinで実装した `FoodClassifier` は、同梱した量子化モデルを LiteRT で実行し、`Bitmap` を端末内だけで food / non-food に分類します。HARDWARE BitmapはソフトウェアBitmapへコピーしたうえで、縦横比を維持して224 × 224へ縮小し、余白を黒で補います。判定のための通信や外部API呼び出しはありません。

写真検知には MediaStore の画像変更を契機とする JobScheduler のジョブを使います。端末の再起動後には監視ジョブを登録し直し、保存済みの世代番号より新しい写真を確認します。標準カメラが所有する画像を対象とし、所有元が不明な場合は `DCIM/Camera/` の画像を対象とします。撮影時刻には MediaStore の `DATE_TAKEN` を使用し、取得できない写真は分類しません。判定用画像は ImageDecoder で長辺224画素以下のソフトウェアBitmapとして読み込むため、フル解像度のソフトウェアBitmapコピーは作りません。

分類結果はアプリ内の `photo_results.db` の `photo_results` テーブルに、写真の MediaStore URI、撮影時刻（Unix時刻、ミリ秒）、`is_food`（1または0）として保持します。元画像の複製や外部送信は行いません。外部送信は Issue #18 の対象です。

16 KBページ向けZIPアラインメントを確認済みです。

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

Kotlinの計装テストでは、Food-5K の配布元評価用分割から選んだ食事1枚と風景1枚に加え、同じ食事画像を224 × 224のHARDWARE Bitmapとして読み込む経路を確認します。配布元が表示するライセンスは CC0 1.0 です。

`FoodClassifierEvaluationTest` は、`ml/evaluate_android.py` から指定された外部画像をHARDWARE Bitmapとしてデコードし、配布対象の `FoodClassifier.isFood()` を通してvalidationとholdoutを評価します。通常の `connectedAndroidTest` では外部画像を要求せず、評価スクリプトから個別に起動した場合だけ全画像を処理します。
