# Backend

Cloud Functions Gen2 / Node.js 24 を使用します。Issue #9 の認証入口は `authExchange`、Issue #5 の画像登録入口は `imageMeal` です。画像登録は `src/apiAuthentication.js` の `authenticateMealRelayRequest` で MealRelay token に対応する `sub` を識別し、`src/healthCredentials.js` の `getGoogleHealthOAuthClient` でそのユーザー専用の OAuth クライアントを取得します。リクエスト本文のユーザー ID で記録先を選びません。

Issue #7 の食事登録処理は `src/registerMeal.js` の `registerMeal` です。後続の画像・テキスト解析エンドポイントは、認証ヘッダー、入力ごとに安定した `mealId`、`occurredAt`（画像は撮影時刻、テキストは入力時刻）、解析結果の `analysis`、`route`（`image` / `text`）を渡します。時刻は UTC offset を含む ISO 8601 形式で渡します。テキスト解析で食事日時を解釈できた場合は `analysis.eatenAt` を渡し、ない場合は入力時刻が使われます。`mealId` は同じ食事の再送で変えず、別の食事には別の ID を付けます。

`imageMeal` は `multipart/form-data` の `image`、`capturedAt`、`mealId` を受け取ります。画像形式は OpenAI API の画像入力仕様に従う `image/jpeg`、`image/png`、`image/webp`、`image/gif` です。`capturedAt` は UTC offset を含む RFC 3339、`mealId` は同じ写真の再送で変わらない値にします。`Authorization: Bearer <MealRelay token>` を必須とし、画像を外部解析へ送る前に token を検証します。本文にユーザー ID は持たせません。成功時は新規登録を `201`、既に同じ内容が登録済みの場合を `200` で返します。

画像解析には OpenAI API の [`gpt-5.6-luna`](https://developers.openai.com/api/docs/models/gpt-5.6-luna) と公式 [`openai`](https://developers.openai.com/api/docs/libraries) SDK を使用します。Responses API の画像入力と Structured Outputs を一度のリクエストで使用し、料理ごとの実摂取量を含む表示名と、1食全体のカロリー・たんぱく質・炭水化物・脂質を取得します。複数品は1食へ集約し、大皿・作り置き・複数人分は写真全量ではなく1人が実際に摂取したと考えられる量を推定するよう指示します。画像中の文字列は栄養情報としてだけ扱い、命令として扱いません。モデルの reasoning 設定は API の既定値を使用します。

栄養表示の値は、表示値と摂取した serving 数の両方が明確で、その値が食事全体を表す場合だけ `packageLabel` 由来の確定値にします。条件を満たさない場合は、確定値と一部推定値を混ぜて確定値とせず、食事全体を推定値として渡します。Issue #7 の共通モデルは項目ごとに確定値を優先し、Google Health へ1食1件で登録します。OpenAI API の画像入力上限は[公式仕様](https://developers.openai.com/api/docs/guides/images-vision)に従います。画像圧縮、Cloud Storage への一時保存、端末側の送信処理はこの実装に含めません。

`analysis` は `foodDisplayName`、任意の `mealType`（`BREAKFAST` / `LUNCH` / `DINNER` / `SNACK`）、`estimated`、`confirmed` を持ちます。栄養項目は `energyKcal`、`proteinGrams`、`carbohydrateGrams`、`fatGrams` です。`estimated` には数値を、`confirmed` には `{ value, origin }` を指定し、`origin` は `packageLabel` または `userInput` とします。両方ある項目は確定値を Google Health へ送ります。共通モデルには両方の値と由来を保持します。認証済み token の `sub` をユーザー ID とし、本文のユーザー ID は受け付けません。

共通モデルと送信時刻は Firestore の `meals/{SHA-256(sub + 区切り文字 + mealId)}` に保存します。Google Health には匿名食品の Nutrition Log を 1 食 1 件として登録し、食事時刻から 1 秒の interval、元の UTC offset、選ばれた栄養値、食品名を送ります。同じ `sub` と `mealId` の再送では、保存済みの解析結果を使って Google Health 登録を再開し、画像解析を再実行しません。同じ ID で撮影時刻が異なる再送は競合エラーになります。Google Health の匿名食品ログは作成後に編集できないため、変更・削除はこの処理の対象外です。[Nutrition Log の公式仕様](https://developers.google.com/health/data-types/nutrition)と[DataPoint の識別子仕様](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints)に従います。

Google Health の `create` は Operation を返します。即時完了が確認できた場合のみ登録済みにします。処理中または応答を失った場合、再試行時に同じ data point ID を[公式の `get` API](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/get)で確認します。存在を確認できない場合は未登録状態のまま再試行可能なエラーにし、409 だけで成功と判断しません。Google Health の現行 REST 一覧と Discovery Document には Operation の取得メソッドがないため、Operation 自体の終端状態は照会しません。`get` には nutrition 読み取り権限が必要です。新しい認可では読み取り・書き込み両方を要求します。既存の書き込み専用 token から移行する端末は Google Health の再認可が必要です。

`index.js` は HTTP 入口、`src/config.js` は環境設定の読み込み、`src/googleOAuth.js` は Google 認可コード交換と ID token 検証、`src/auth.js` は MealRelay token と登録規則、`src/firestoreAuthRepository.js` は Firestore の読み書きを担当します。MealRelay token による API 認証は `src/apiAuthentication.js`、Google Health 用 OAuth クライアントの取得は `src/healthCredentials.js` が担当します。

初回利用または端末のトークン喪失時、Android は Google の認可コードを `authExchange` に HTTPS で送ります。Backend はコードを Google に交換し、Google Auth Library で access token の権限と ID token の署名・audience・issuer・有効期限を検証します。初回登録時のみ、検証済みメールアドレスを Secret Manager に置いた許可メールアドレスと比較します。登録後は OpenID Connect の `sub` でユーザーを識別します。Google Health の refresh token は `users/{sub の SHA-256 ハッシュ}`、MealRelay トークンの SHA-256 ハッシュと `sub` の対応は `deviceTokens/{token の SHA-256 ハッシュ}` として Firestore に保存します。MealRelay トークンの生値は発行時の応答以外に保持しません。

`GOOGLE_OAUTH_CLIENT_ID` は Web OAuth client ID です。`GOOGLE_OAUTH_CLIENT_SECRET` と `MEAL_RELAY_ALLOWED_EMAILS` は Secret Manager の secret を環境変数として Cloud Functions にバインドします。後者は許可する2つのメールアドレスの JSON 配列です。初回登録は Firestore のトランザクションで2ユーザーまでに制限し、既存ユーザーへの端末トークン追加発行はこの枠を消費しません。関数の実行アカウントには対象 Firestore の読み書き権限と、これらの secret へのアクセス権限が必要です。デプロイ時のランタイムは `nodejs24`、entry point は `authExchange` とします。OAuth consent screen は実運用前に `In Production` にします。

Google Cloud には Android アプリのパッケージ名と署名証明書を登録した Android OAuth client と、Backend 用 Web OAuth client を同じプロジェクトに設定し、Google Health API を有効化します。Android には Backend の HTTPS 関数 URL と Web OAuth client ID を共通のビルド設定として渡します。両端末に同じ APK をインストールできます。認証 token や OAuth client secret を APK またはリポジトリに含めません。

ローカル検証は `npm ci && npm test` で実行します。Google への実際の認可、Firestore、Secret Manager、Google Health への書き込みは実運用環境での設定と確認が必要です。

## `imageMeal` のデプロイ

後述する Issue #9 の Firestore、OAuth client、Secret Manager、実行サービスアカウントを先に準備します。OpenAI API key と OAuth client secret は環境変数の平文ではなく Secret Manager の secret をバインドします。

```sh
gcloud functions deploy imageMeal \
  --gen2 \
  --runtime=nodejs24 \
  --region=<REGION> \
  --source=backend \
  --entry-point=imageMeal \
  --trigger-http \
  --allow-unauthenticated \
  --service-account="mealrelay-auth@<PROJECT_ID>.iam.gserviceaccount.com" \
  --set-env-vars="GOOGLE_OAUTH_CLIENT_ID=<WEB_OAUTH_CLIENT_ID>" \
  --set-secrets="GOOGLE_OAUTH_CLIENT_SECRET=google-oauth-client-secret:latest,OPENAI_API_KEY=openai-api-key:latest"
```

HTTP trigger 自体は Android から到達できるよう未認証呼び出しを許可しますが、関数内では有効な MealRelay token がない要求を画像解析前に拒否します。実際の写真を用いた栄養推定精度、OpenAI API、Firestore、Google Health への一連の登録は実運用環境で確認が必要です。

## Issue #9 の実環境確認

この手順では、同じ APK を2台に入れ、異なる Google アカウントで初回認可したときに、Issue #9 が定めるユーザー識別、端末 token、Google Health 用認証情報の対応を確認します。コマンド中の `<…>` は各環境の値に置き換えます。secret の値、OAuth client secret、メールアドレス、発行された MealRelay token はシェル履歴、リポジトリ、Issue、Pull Request に残しません。

### 1. Google Cloud プロジェクトと API を準備する

1. [Google Cloud Console](https://console.cloud.google.com/) で、この確認専用のプロジェクトを選ぶか作成します。ローカルでは `gcloud auth login` を実行し、次を実行します。

   ```sh
   gcloud config set project <PROJECT_ID>
   gcloud services enable \
     cloudfunctions.googleapis.com \
     run.googleapis.com \
     cloudbuild.googleapis.com \
     artifactregistry.googleapis.com \
     secretmanager.googleapis.com \
     firestore.googleapis.com
   ```

2. Console の **APIs & Services** → **Library** で **Google Health API** を検索し、有効にします。Cloud Run functions のデプロイに必要な API は、上記コマンドで有効になります。組織のポリシーで追加の API 有効化やロール付与を求められた場合は、Console に表示される手順に従います。

### 2. OAuth consent screen と OAuth client を作成する

1. Console の **Google Auth Platform** で consent screen を設定します。通常の Google アカウント2件で確認する場合は Audience を **External** にし、Branding の必須項目を入力します。Data Access で Google Health の nutrition 読み取り・書き込み scope を追加します。公開状態を **In production** にします。Testing のままでは refresh token が7日で失効するため、Issue #9 の確認条件を満たしません。
2. **Clients** → **Create client** から Android client を作成します。パッケージ名には `net.ambitious.android.mealrelay` を、署名証明書 SHA-1 には確認に使う APK の署名を指定します。debug APK なら、リポジトリの `android` ディレクトリで次を実行し、`debug` variant の SHA-1 を使用します。

   ```sh
   ./gradlew signingReport
   ```

3. 同じプロジェクトに Web application 型の OAuth client を作成します。[Google Health のセットアップ手順](https://developers.google.com/health/setup)に従い、**Where are you calling from?** では **Web Server** を選び、Authorized redirect URI には `https://www.google.com` を登録します。表示される client ID と client secret を控えます。

   Android は Web リダイレクトを受けません。実装済みの `authExchange` は Android の server auth code を交換する際に空の `redirect_uri` を Google へ送ります。これは Android に Web 版がない場合の[Google の server-side OAuth 手順](https://developers.google.com/identity/sign-in/android/offline-access)に従うものであり、関数 URL を OAuth client の redirect URI に登録する必要はありません。

### 3. Firestore と実行サービスアカウントを用意する

1. Console の **Firestore Database** → **Create database** で、**Native mode** の `(default)` データベースを作成し、利用するロケーションを選びます。[Firestore の作成手順](https://firebase.google.com/docs/firestore/quickstart)も参照してください。
2. 関数専用の実行サービスアカウントを作り、Firestore の読み書き権限を付与します。

   ```sh
   gcloud iam service-accounts create mealrelay-auth \
     --display-name="MealRelay authExchange"

   gcloud projects add-iam-policy-binding <PROJECT_ID> \
     --member="serviceAccount:mealrelay-auth@<PROJECT_ID>.iam.gserviceaccount.com" \
     --role="roles/datastore.user"
   ```

### 4. Secret Manager に認証設定を保存する

secret は作成後に標準入力から渡します。入力を終えたら EOF を送ってください（macOS / Linux の端末では `Control-D`）。この方法では secret 値をコマンド引数やファイルに書きません。

1. Google Cloud Console の Web OAuth client から取得した **client secret** を `google-oauth-client-secret` に保存します。

   ```sh
   gcloud secrets create google-oauth-client-secret --replication-policy=automatic
   gcloud secrets versions add google-oauth-client-secret --data-file=-
   ```

2. OpenAI API key を `openai-api-key` に保存します。

   ```sh
   gcloud secrets create openai-api-key --replication-policy=automatic
   gcloud secrets versions add openai-api-key --data-file=-
   ```

3. 初回登録を許可する2つのメールアドレスだけを、JSON 配列として `meal-relay-allowed-emails` に保存します。入力は `[` で始まり `]` で終わる有効な JSON とし、メールアドレスは二重引用符で囲みます。

   ```sh
   gcloud secrets create meal-relay-allowed-emails --replication-policy=automatic
   gcloud secrets versions add meal-relay-allowed-emails --data-file=-
   ```

4. 実行サービスアカウントだけに、各 secret の読み取りを許可します。

   ```sh
   gcloud secrets add-iam-policy-binding google-oauth-client-secret \
     --member="serviceAccount:mealrelay-auth@<PROJECT_ID>.iam.gserviceaccount.com" \
     --role="roles/secretmanager.secretAccessor"

   gcloud secrets add-iam-policy-binding openai-api-key \
     --member="serviceAccount:mealrelay-auth@<PROJECT_ID>.iam.gserviceaccount.com" \
     --role="roles/secretmanager.secretAccessor"

   gcloud secrets add-iam-policy-binding meal-relay-allowed-emails \
     --member="serviceAccount:mealrelay-auth@<PROJECT_ID>.iam.gserviceaccount.com" \
     --role="roles/secretmanager.secretAccessor"
   ```

Secret version の追加と Cloud Run functions への secret の設定は、それぞれ[Secret Manager](https://cloud.google.com/secret-manager/docs/add-secret-version)と[Cloud Run の secret 設定](https://cloud.google.com/run/docs/configuring/services/secrets)の公式手順に基づきます。

### 5. `authExchange` を Cloud Functions Gen2 にデプロイする

リポジトリのルートで次を実行します。`GOOGLE_OAUTH_CLIENT_ID` には手順2で作成した **Web** OAuth client ID を指定します。`--set-secrets` がsecretを環境変数として関数へバインドし、手順4の IAM 設定が実行時の読み取りを許可します。

```sh
gcloud functions deploy authExchange \
  --gen2 \
  --runtime=nodejs24 \
  --region=<REGION> \
  --source=backend \
  --entry-point=authExchange \
  --trigger-http \
  --allow-unauthenticated \
  --service-account="mealrelay-auth@<PROJECT_ID>.iam.gserviceaccount.com" \
  --set-env-vars="GOOGLE_OAUTH_CLIENT_ID=<WEB_OAUTH_CLIENT_ID>" \
  --set-secrets="GOOGLE_OAUTH_CLIENT_SECRET=google-oauth-client-secret:latest,MEAL_RELAY_ALLOWED_EMAILS=meal-relay-allowed-emails:latest"
```

Android から HTTPS で呼ぶため、HTTP trigger は未認証呼び出しを許可します。関数自身は Google authorization code を検証してから MealRelay token を発行します。`gcloud functions deploy` の各オプションは[公式リファレンス](https://cloud.google.com/sdk/gcloud/reference/functions/deploy)を参照してください。

デプロイ後、関数 URL と secret バインドを値を表示せずに確認します。

```sh
gcloud functions describe authExchange \
  --gen2 \
  --region=<REGION> \
  --format='yaml(serviceConfig.uri,serviceConfig.serviceAccountEmail,serviceConfig.secretEnvironmentVariables)'
```

出力された `serviceConfig.uri` を以降 `<AUTH_EXCHANGE_URL>` と呼びます。次の応答が `405` であれば、URL が到達可能で HTTP メソッド制約も有効です。Google authorization code を手入力して `curl` に渡す必要はありません。

```sh
curl --include --request GET <AUTH_EXCHANGE_URL>
```

### 6. 同じ APK を2台に用意する

Android のリポジトリの `android` ディレクトリで、公開設定を Gradle property として渡して APK を作成します。これらは client ID と関数 URL であり secret ではありません。`<AUTH_EXCHANGE_URL>` には手順5で確認した完全な HTTPS URL を使用します。

```sh
./gradlew :app:assembleDebug \
  -PmealRelayOauthClientId=<WEB_OAUTH_CLIENT_ID> \
  -PmealRelayAuthEndpoint=<AUTH_EXCHANGE_URL>
```

生成される `app/build/outputs/apk/debug/app-debug.apk` を、同じ署名のまま2台の Android 端末へインストールします。実機へのインストールと UI 操作は、この確認を行う利用者が実施します。

### 7. 初回認可と再認可を確認する

1. 1台目でアプリを開き、写真権限を許可して Google の認可画面へ進みます。手順4の許可メールアドレスに含めた1つ目の Google アカウントを選び、Google Health の nutrition 読み取り・書き込み権限を許可します。認可画面の完了後に `MealRelayAuthorizationActivity` が終了し、認可エラーのダイアログが表示されなければ、手順8の Firestore 確認へ進みます。`users` と `deviceTokens` の追加を確認して、`authExchange` が MealRelay token を発行したことを判断します。
2. 2台目で同じ手順を実施し、もう一方の Google アカウントを選びます。端末ごとに別の Google アカウントを選ぶ以外は、APK と公開設定を変えません。
3. 1台目で Android の設定から MealRelay アプリのストレージを消去し、再度アプリを開きます。1台目で使ったものと同じ Google アカウントを選んで認可します。これは端末 token を失った場合の再認可を再現します。

### 8. Firestore で Issue #9 の結果を確認する

Console の **Firestore Database** → **Data** で、以下を順に確認します。表示された `sub`、refresh token、端末 token をコピー、共有、記録しません。

1. `metadata/registration` の `count` は `2` です。2台目の初回認可で2ユーザーが登録され、手順7-3の再認可で増えません。
2. `users` には異なるドキュメントが2件あります。それぞれに別の `sub` と、そのユーザー専用の `refreshToken` があります。ドキュメント ID は `sub` の SHA-256 ハッシュです。
3. `registeredEmails` には2件あり、各ドキュメントが対応する `sub` を参照します。`deviceTokens` には、初回認可後に2件、手順7-3の再認可後に3件あります。再認可で追加されたドキュメントも、1台目の最初の認可と同じ `sub` を持ちます。
4. `deviceTokens` の各ドキュメント ID は端末 token の SHA-256 ハッシュです。フィールドに端末 token の生値がなく、`users`、`registeredEmails`、`metadata` にも端末 token の生値がないことを確認します。これにより、再認可で新しい端末 token が発行され、Firestore にはその対応だけが保存されることを確認できます。

Issue #9 の範囲では、Google Health への記録送信はまだ実装していません。このため実環境で確認できる Google Health 認証情報の分離は、上記の `users` の `sub` ごとの `refreshToken` と、`deviceTokens` の `sub` 対応までです。2台の初回認可で別々の `sub` と `refreshToken` が作られ、同じアカウントの再認可が元の `sub` にだけ追加の `deviceTokens` を作ることを確認すれば、後続の Google Health 通信が `sub` からユーザー専用認証情報を取得するための Issue #9 の対応関係を確認できます。Google Health への実際の書き込みと記録先の検証は、送信 endpoint を追加する後続 Issue の確認対象です。
