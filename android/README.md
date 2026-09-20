# Android

MealRelay の Android アプリです。

通常のメイン画面はありません。初回起動時に写真へのフルアクセスを許可すると、標準カメラで新しく撮影した写真をバックグラウンドで検知して端末内で分類します。「選択した写真のみ」の権限では自動検知を開始しません。自動登録の利用中は写真へのフルアクセスが維持されることを前提とします。

対象環境は Android 17 です。Android 17 より古い OS には対応しません。
`minSdk`、`compileSdk`、`targetSdk` はすべて 37 です。

## 構成

- `app/`: Android アプリケーションモジュール

Kotlinで実装した `FoodClassifier` は、同梱した量子化モデルを LiteRT で実行し、`Bitmap` を端末内だけで food / non-food に分類します。HARDWARE BitmapはソフトウェアBitmapへコピーしたうえで、縦横比を維持して224 × 224へ縮小し、余白を黒で補います。判定のための通信や外部API呼び出しはありません。

写真検知には WorkManager の MediaStore 変更監視と定期走査を使います。WorkManager が永続化した作業から、アプリを開いていない間や端末の再起動後も走査を再開します。標準カメラが所有する画像を対象とし、所有元が不明な場合は `DCIM/Camera/` の画像を対象とします。通常走査は `GENERATION_MODIFIED` で公開後の写真を検出し、`GENERATION_ADDED` と自動検知開始時のgenerationを比較して、開始前写真の後日の編集・メタデータ更新を除外します。撮影時刻には MediaStore の `DATE_TAKEN` を使用し、取得できない写真は分類しません。判定用画像は ImageDecoder で元の解像度のHARDWARE Bitmapとして読み込み、Issue #4 / PR #16で評価した `FoodClassifier` 内の前処理を通します。

`PhotoClassification` は走査中に所有し、最初の分類対象が見つかったときだけモデルを読み込み、走査終了時に閉じます。

分類結果は Room の `photo_detection.db` の `photo_results` テーブルに、MediaStore version、写真のURI、撮影時刻（Unix時刻、ミリ秒）、`is_food`（1または0）として保持します。MediaStore version、generation、登録時刻も同じデータベースの `photo_scan_state` テーブルに保持します。MediaStore versionが変わった場合は、再同期が完了するまで旧versionの走査状態を維持します。画像の読み込みや分類に失敗した場合は `is_food` をNULLとして記録し、次の写真へ進みます。`photo_detection.db` とSQLiteの付随ファイルはcloud backupとdevice transferから除外します。元画像の複製や外部送信は行いません。外部送信は Issue #18 の対象です。

Room のschemaは `app/schemas/` に保存し、version 1以降の履歴を管理します。

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

画像単位の失敗、MediaStore再構築後の識別子再利用と中断後の再同期、開始前写真の後日更新の除外、公開後にgenerationが更新された写真の検出は、`PhotoScannerTest` のJVM単体テストで確認します。テストでは画像読み込み・分類の失敗を模擬し、テスト専用のMediaStore providerとRoomデータベースを使います。

ビルド後のデバッグ用 APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

起動中のAndroidエミュレーターまたは実機で、モデルを含む計装テストを実行する場合は次を実行します。

```shell
./gradlew connectedAndroidTest
```

Kotlinの計装テストでは、Food-5K の配布元評価用分割から選んだ食事1枚と風景1枚に加え、同じ食事画像を224 × 224のHARDWARE Bitmapとして読み込む経路を確認します。配布元が表示するライセンスは CC0 1.0 です。

`FoodClassifierEvaluationTest` は、`ml/evaluate_android.py` から指定された外部画像をHARDWARE Bitmapとしてデコードし、配布対象の `FoodClassifier.isFood()` を通してvalidationとholdoutを評価します。通常の `connectedAndroidTest` では外部画像を要求せず、評価スクリプトから個別に起動した場合だけ全画像を処理します。
