# food / non-food 判定PoC

Issue #4 の判断基準に従い、non-food の誤送信につながる偽陽性を食事写真の偽陰性より重く扱う端末内二値分類のPoCです。学習用画像の取得と学習は開発環境で行いますが、Androidでの判定時に通信は発生しません。

## 採用内容

| 項目 | 採用内容 | 判断理由 |
| --- | --- | --- |
| モデル | ImageNetで事前学習した MobileNetV3Small、224 × 224入力 | 全整数量子化後が1,218,032バイトで、Android上の最小構成を検証できたため |
| 学習 | 特徴抽出層を固定し、二値分類層だけを学習 | Androidの配布時前処理でFood-5K validationを評価し、比較候補中でfood再現率が最も高かったため |
| 学習データ | Food-5Kに Open Images の対象画像を追加 | 端末画面、飲み物、food全般の学習例を補うため。Wikimedia Commonsの追加学習候補はvalidationで選ばれなかった |
| 量子化 | 重み・演算・入出力を全整数化し、入出力を `uint8` に統一 | 学習用分割からfood / non-foodを各150枚使って校正し、Android側で浮動小数点の前後処理を不要にできるため |
| 閾値 | 量子化出力103以上を food とする | Androidの配布時前処理でFood-5K validation 500枚のnon-foodに対する偽陽性率を0.5%以下に制約し、その範囲でfood再現率が最大となる点を選んだため |
| Android実行 | LiteRT Interpreter 1.4.2、CPU 2スレッド | 単一入出力モデルに必要な依存関係だけで構成でき、Android 17の16 KBページ環境で動作確認できたため |

閾値103は、出力量子化スケール1 / 256、ゼロ点0における0.40234375です。モデル、学習方法、サンプリング、閾値はFood-5K validationだけで選定し、最終holdoutの結果を見て変更していません。

## データ

基礎データには [Food-5KのKaggleミラー](https://www.kaggle.com/datasets/trolukovich/food5k-image-dataset)を使用します。配布元の表示ライセンスは CC0 1.0 です。取得したZIPのSHA-256は `6838829edbb56c4859e733960050d2a59974e52848093a61abdcfb1b9a580f09` です。

基礎データの構成は次のとおりです。

| 分割 | food | non-food |
| --- | ---: | ---: |
| 学習 | 1,500 | 1,500 |
| validation | 500 | 500 |
| 配布元の評価用分割 | 500 | 500 |

`prepare_supplemental_data.py` は、学習用分割だけに次の400枚を追加します。

- [Open Images V5](https://storage.googleapis.com/openimages/web/index.html) の端末画面100枚、飲み物100枚、food 100枚
- [Wikimedia Commons](https://commons.wikimedia.org/) の包装食品、缶詰、包装済みサンドイッチ、包装済み寿司、合計100枚

追加後の学習用分割はfood 1,800枚、non-food 1,600枚です。採用モデルは比較結果に基づきWikimedia Commonsの学習画像100枚を除き、food 1,700枚、non-food 1,600枚で学習します。別のWikimedia Commons画像67枚は、候補比較に使用したtuningデータとして学習ディレクトリ外へ取得します。追加画像のID、取得URL、画像ごとのSHA-256、配布元URL、ライセンスは [supplemental_manifest.json](./supplemental_manifest.json) に固定しています。

取得処理はmanifestだけを参照し、取得不能またはSHA-256不一致の場合に失敗します。また、管理対象ディレクトリに `open_images_`、`commons_`、または過去の重複実験で使用した `oversample_` で始まり、現在のmanifestに含まれないファイルが残っている場合も、学習開始前に失敗します。過去のmanifestや比較実験で作成した補助画像が再実行時の学習対象へ混入することはありません。学習画像自体はリポジトリへ含めません。

最終評価には [evaluation_manifest.json](./evaluation_manifest.json) に固定した357枚を使用します。前処理差の修正後も、モデル構成と閾値の再選定にはFood-5K validationだけを使用し、固定済みholdoutの画像と結果は使用していません。

- Open Images V5のtest分割: 人物40枚、風景50枚、端末画面50枚、書類50枚、飲み物50枚、food全般50枚
- 学習・tuning・過去の評価に使用していないWikimedia Commons画像: 缶詰43枚、包装食品24枚

各画像の取得URLとSHA-256を固定し、追加学習用manifestとの画像ID・ページ名の重複がないことを確認しています。

## モデル選定と最終評価の分離

当初報告したFood-5K評価用分割のfood再現率77.8%・non-food偽陽性率0.6%、およびOpen Images validationとWikimedia Commonsを使ったカテゴリ別の数値は、候補モデルや学習データ量の判断に参照していました。そのため独立した最終評価値としては撤回し、採用判断には使用しません。

量子化校正を学習用分割のfood / non-food各150枚へ変更して各候補を再生成し、Android 17のPixel 9 AVD上で `ImageDecoder` のHARDWARE Bitmapから配布対象の `FoodClassifier` を実行して再選定した結果は次のとおりです。各候補について、偽陽性率0.5%以下でfood再現率が最大となる閾値をFood-5K validationだけから選びました。Commons追加学習なしの候補が最も高いfood再現率だったため、採用モデルを変更しました。

| 候補 | 量子化閾値 | food再現率 | non-food偽陽性率 |
| --- | ---: | ---: | ---: |
| 特徴抽出層の末尾30層を追加学習 | 256 | 0.0% | 0.0% |
| 特徴抽出層を固定、Commons追加学習なし（採用） | 103 | 79.6% | 0.4% |
| 特徴抽出層を固定、Commons追加学習あり | 164 | 73.6% | 0.4% |
| 市販品学習画像を5倍に重複 | 182 | 67.4% | 0.2% |

固定済みのholdoutは候補比較と閾値選定には使用せず、Android経路で選定を完了した後に最終評価だけを再実行しています。

前回記録したAndroid validationの閾値153・food再現率83.0%と固定holdoutのfood再現率50.3%は、non-foodだけで量子化校正した旧モデルの結果です。今回の採用モデルの指標は上記の再選定結果と以下の固定holdout結果に置き換えます。

以前の候補比較とholdout評価は、TensorFlowまたはPillowの縮小・余白配置を使用しており、Android実装とpixel-equivalentではありませんでした。このため、旧モデルのvalidation food再現率79.6%・閾値164と、holdout food再現率49.7%を配布時の指標として撤回し、上記Android経路の結果へ置き換えています。

## 再現手順

Python 3.11で確認しています。

```shell
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r ml/requirements.txt

curl -L https://www.kaggle.com/api/v1/datasets/download/trolukovich/food5k-image-dataset -o /tmp/food5k.zip
shasum -a 256 /tmp/food5k.zip
mkdir -p ml/data
unzip /tmp/food5k.zip -d ml/data

python ml/prepare_supplemental_data.py \
  --dataset ml/data \
  --manifest ml/supplemental_manifest.json \
  --work-directory ml/output/supplemental

selected_data_directory=$(mktemp -d)
rsync -a --exclude='commons_*' ml/data/ "$selected_data_directory/"
python ml/train_model.py \
  --dataset "$selected_data_directory" \
  --output ml/output/model

cd android
./gradlew assembleDebug assembleDebugAndroidTest
cd ..

python ml/evaluate_android.py \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --app-apk android/app/build/outputs/apk/debug/app-debug.apk \
  --test-apk android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --dataset "$selected_data_directory/validation" \
  --model ml/output/model/food_classifier_int8.tflite \
  --work-directory ml/output/android-validation \
  --output ml/output/android-validation/evaluation.json

python ml/evaluate_android.py \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --app-apk android/app/build/outputs/apk/debug/app-debug.apk \
  --test-apk android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --threshold-quantized 103 \
  --manifest ml/evaluation_manifest.json \
  --model ml/output/model/food_classifier_int8.tflite \
  --work-directory ml/output/holdout \
  --output ml/output/holdout/evaluation.json
```

Android 17のエミュレーターまたは実機を起動して実行します。`selected_data_directory` は採用構成の学習画像だけを含む新規の一時ディレクトリです。評価スクリプトはAPKとテストAPKをインストールし、画像を端末の一時領域へ転送します。計装テストは各画像をHARDWARE Bitmapとしてデコードし、配布対象の `FoodClassifier.isFood()` をそのまま呼び出します。validationとholdoutは両方とも `--model` で同じ再生成モデルを指定します。

同梱モデルのSHA-256は `b3dc2cca6475637e65cd9eff1111d6bc4dcdd8dc948463592ee999ea07a29076`、固定した追加データmanifestのSHA-256は `d05ef8e20aaee447c1683235f959e26ca4ae5bee9719ead16e95627fadf7f004`、最終評価manifestのSHA-256は `7557dcf89b708e1f9fad2a0dc0565d65ffd0b60b178cb5f7a09f7c4f1d90d2a8` です。

## 最終評価結果

Androidの配布時前処理で固定holdoutを評価した結果、non-food偽陽性率0%（0 / 190）、food再現率38.9%（65 / 167）、food適合率100%でした。カテゴリ別の結果は次のとおりです。

| 期待値 | カテゴリ | food判定 | 判定率 |
| --- | --- | ---: | ---: |
| non-food | 人物 | 0 / 40 | 0% |
| non-food | 風景 | 0 / 50 | 0% |
| non-food | 端末画面 | 0 / 50 | 0% |
| non-food | 書類 | 0 / 50 | 0% |
| food | 飲み物 | 14 / 50 | 28% |
| food | food全般 | 30 / 50 | 60% |
| food | 缶詰 | 17 / 43 | 39.5% |
| food | 包装食品 | 4 / 24 | 16.7% |

機械可読な選定根拠と結果は [evaluation.json](./evaluation.json) に保存しています。

## 残る課題

- 人物、風景、端末画面、書類のカテゴリ別評価は小規模であり、実際の利用分布に対する誤送信率を保証しません。
- 飲み物、缶詰、包装食品の再現率は低く、Issue #4 の対象全体を実運用水準で満たしたとは判断できません。
- 今回の固定holdoutでは偽陽性0件でしたが、各non-foodカテゴリの母数が小さく、実写真での偽陽性率は未確認です。
- Pixel実機2台での実写真評価と処理時間・消費電力の計測は未実施です。

最小のガードとして、実写真で定めた偽陽性基準を満たすまでは、この判定結果を外部送信の自動許可に使用しません。
