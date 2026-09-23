# Android

MealRelay の Android アプリです。

初回起動時に写真へのフルアクセスを許可すると、標準カメラで新しく撮影した写真をバックグラウンドで検知して端末内で分類します。「選択した写真のみ」の権限では自動検知を開始しません。自動登録の利用中は写真へのフルアクセスが維持されることを前提とします。メイン画面では、手動入力の開始と、送信に失敗して端末内に保持された食事の確認・手動再送ができます。

対象環境は Android 17 です。Android 17 より古い OS には対応しません。
`minSdk`、`compileSdk`、`targetSdk` はすべて 37 です。

## 構成

- `app/`: Android アプリケーションモジュール

Kotlinで実装した `FoodClassifier` は、同梱した量子化モデルを LiteRT で実行し、`Bitmap` を端末内だけで food / non-food に分類します。HARDWARE BitmapはソフトウェアBitmapへコピーしたうえで、縦横比を維持して224 × 224へ縮小し、余白を黒で補います。判定のための通信や外部API呼び出しはありません。

写真検知には WorkManager の MediaStore 変更監視と定期走査を使います。WorkManager が永続化した作業から、アプリを開いていない間や端末の再起動後も走査を再開します。標準カメラが所有する画像を対象とし、所有元が不明な場合は `DCIM/Camera/` の画像を対象とします。通常走査は `GENERATION_MODIFIED` で公開後の写真を検出し、`GENERATION_ADDED` と自動検知開始時のgenerationを比較して、開始前写真の後日の編集・メタデータ更新を除外します。撮影時刻には MediaStore の `DATE_TAKEN` を使用し、取得できない写真は分類しません。判定用画像は ImageDecoder で元の解像度のHARDWARE Bitmapとして読み込み、Issue #4 / PR #16で評価した `FoodClassifier` 内の前処理を通します。

`PhotoClassification` は走査中に所有し、最初の分類対象が見つかったときだけモデルを読み込み、走査終了時に閉じます。

初回利用時には Google Health の OAuth 認可を求めます。`MealRelayAuthorizationActivity` が Activity Result API で認可画面と結果を扱い、ライフサイクルに連動したコルーチンで認可コード交換を進めます。`MealRelayAuthorizationRepository` が `MealRelayAuthorizationTransport` を通じて Backend から返された MealRelay トークンを保存します。テキスト送信は `TextMealSubmissionTransport` が、テキスト送信用endpointだけを基準にRetrofitとOkHttpの認証付き通信を構成します。トークンは `MealRelayTokenStore` が Tink で暗号化し、鍵の保護には利用可能な場合に Android Keystore を使用します。トークンが失われた場合は次の起動時に再認可します。トークンとユーザーを APK に埋め込まず、2台の Pixel に同じ APK を使用します。バックアップと端末移行から認証情報を除外します。

Firebase Cloud Messaging の `onRegistered` callbackからFirebase Installation IDを受け取り、WorkManagerがネットワーク接続時に現在のMealRelay tokenでBackendへ登録します。Google認証後はFCM登録を明示的に要求し、同一FIDでも認証ユーザーの変更がBackendへ反映されます。Firebase Installation IDのSDK内部ファイルと旧Instance ID用共有設定はクラウドバックアップと端末移行から除外します。

ビルド時には、両端末で共通の公開設定 `mealRelayOauthClientId`（Web OAuth client ID）、`mealRelayAuthEndpoint`（Backend HTTPS 関数 URL）、`mealRelayTextEndpoint`（テキスト送信用 Backend HTTPS 関数 URL）、`mealRelayImageEndpoint`（画像送信用 Backend HTTPS 関数 URL）、`mealRelayFirebaseInstallationEndpoint`（Firebase Installation 登録用 Backend HTTPS 関数 URL）を Gradle プロパティで指定します。これらは秘密情報ではありません。Firebase Consoleで `net.ambitious.android.mealrelay` の Android アプリを登録し、取得した `google-services.json` を `app/` に配置します。このファイルはリポジトリへ追加しません。Google Health OAuth client secret と許可メールアドレスは Backend の Secret Manager に置きます。

分類結果は Room の `meal_relay.db` の `photo_results` テーブルに、MediaStore version、写真のURI、撮影時刻（Unix時刻、ミリ秒）、`is_food`（1または0）として保持します。MediaStore version、generation、登録時刻も同じデータベースの `photo_scan_state` テーブルに保持します。`meal_submission_queue` は画像URIまたはテキスト、元のRFC 3339時刻、UUID の `mealId`、自動送信回数、送信状態を保持します。MediaStore versionが変わった場合は、再同期が完了するまで旧versionの走査状態を維持します。画像の読み込みや分類に失敗した場合は `is_food` をNULLとして記録し、次の写真へ進みます。`meal_relay.db` とSQLiteの付随ファイルはcloud backupとdevice transferから除外します。

送信キューはネットワーク接続を条件に、項目ごとに独立した WorkManager の作業として実行します。初回を含め最大3回まで自動送信し、通信例外、タイムアウト、HTTP 408・429・5xx のみを5分、10分のバックオフで再送します。最終失敗または再送しない4xxはキューに残します。画像送信が Backend に拒否された場合は同じ内容の手動再送を表示せず、それ以外の失敗は通知とメイン画面から手動再送できます。手動再送はタップごとに1回だけ送信し、回数制限はありません。成功した項目は削除します。食事入力と写真検知からのキュー追加は、それぞれ Issue #3、Issue #18 で接続済みです。

Room のschemaは `app/schemas/` に保存します。

16 KBページ向けZIPアラインメントを確認済みです。

モデル、閾値、学習データおよび評価結果は [../ml/README.md](../ml/README.md) に記録しています。

## 必要な環境

- JDK 25
- Android SDK Platform 37

## 検証

`android/` ディレクトリで次を実行します。

```shell
./gradlew build \
  -PmealRelayOauthClientId=<WEB_OAUTH_CLIENT_ID> \
  -PmealRelayAuthEndpoint=<AUTH_EXCHANGE_URL> \
  -PmealRelayTextEndpoint=<TEXT_MEAL_URL> \
  -PmealRelayImageEndpoint=<IMAGE_MEAL_URL> \
  -PmealRelayFirebaseInstallationEndpoint=<FIREBASE_INSTALLATION_URL>
```

画像単位の失敗、MediaStore再構築後の識別子再利用と中断後の再同期、開始前写真の後日更新の除外、公開後にgenerationが更新された写真の検出は、`PhotoScannerTest` のJVM単体テストで確認します。テストでは画像読み込み・分類の失敗を模擬し、テスト専用のMediaStore providerとRoomデータベースを使います。

ビルド後のデバッグ用 APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

起動中のAndroidエミュレーターまたは実機で、モデルを含む計装テストを実行する場合は次を実行します。

```shell
./gradlew connectedAndroidTest
```

Kotlinの計装テストでは、Food-5K の配布元評価用分割から選んだ食事1枚と風景1枚に加え、同じ食事画像を224 × 224のHARDWARE Bitmapとして読み込む経路を確認します。配布元が表示するライセンスは CC0 1.0 です。

`FoodClassifierEvaluationTest` は、`ml/evaluate_android.py` から指定された外部画像をHARDWARE Bitmapとしてデコードし、配布対象の `FoodClassifier.isFood()` を通してvalidationとholdoutを評価します。通常の `connectedAndroidTest` では外部画像を要求せず、評価スクリプトから個別に起動した場合だけ全画像を処理します。
