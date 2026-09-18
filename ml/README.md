# food / non-food 判定PoC

Issue #4 の判断基準に従い、non-food の誤送信につながる偽陽性を食事写真の偽陰性より重く扱う端末内二値分類のPoCです。学習用画像の取得と学習は開発環境で行いますが、Androidでの判定時に通信は発生しません。

## 採用内容

| 項目 | 採用内容 | 判断理由 |
| --- | --- | --- |
| モデル | ImageNetで事前学習した MobileNetV3Small、224 × 224入力 | 全整数量子化後が1,218,032バイトで、Android上の最小構成を検証できたため |
| 学習 | 特徴抽出層を固定し、二値分類層だけを学習 | Androidの配布時前処理でFood-5K validationを評価し、同じ偽陽性率0.4%の比較候補の中でfood再現率が最も高かったため |
| 学習データ | Food-5Kに Open Images と Wikimedia Commons の対象画像を追加 | Food-5Kだけでは端末画面、飲み物、市販品の対象例が不足するため |
| 量子化 | 重み・演算・入出力を全整数化し、入出力を `uint8` に統一 | Android側で浮動小数点の前後処理を不要にし、CPUで実行できるため |
| 閾値 | 量子化出力153以上を food とする | Androidの配布時前処理でFood-5K validation 500枚のnon-foodに対する偽陽性率を0.5%以下に制約し、その範囲でfood再現率が最大となる点を選んだため |
| Android実行 | LiteRT Interpreter 1.4.2、CPU 2スレッド | 単一入出力モデルに必要な依存関係だけで構成でき、Android 17の16 KBページ環境で動作確認できたため |

閾値153は、出力量子化スケール1 / 256、ゼロ点0における0.59765625です。モデル、学習方法、サンプリング、閾値はFood-5K validationだけで選定し、最終holdoutの結果を見て変更していません。

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

追加後の学習用分割はfood 1,800枚、non-food 1,600枚です。別のWikimedia Commons画像67枚は、候補比較に使用したtuningデータとして学習ディレクトリ外へ取得します。採用モデルで使用した画像ID、取得URL、画像ごとのSHA-256、配布元URL、ライセンスは [supplemental_manifest.json](./supplemental_manifest.json) に固定しています。

取得処理はmanifestだけを参照し、取得不能またはSHA-256不一致の場合に失敗します。また、管理対象ディレクトリに `open_images_`、`commons_`、または過去の重複実験で使用した `oversample_` で始まり、現在のmanifestに含まれないファイルが残っている場合も、学習開始前に失敗します。過去のmanifestや比較実験で作成した補助画像が再実行時の学習対象へ混入することはありません。学習画像自体はリポジトリへ含めません。

最終評価には [evaluation_manifest.json](./evaluation_manifest.json) に固定した357枚を使用します。前処理差の修正後も、モデル構成と閾値の再選定にはFood-5K validationだけを使用し、固定済みholdoutの画像と結果は使用していません。

- Open Images V5のtest分割: 人物40枚、風景50枚、端末画面50枚、書類50枚、飲み物50枚、food全般50枚
- 学習・tuning・過去の評価に使用していないWikimedia Commons画像: 缶詰43枚、包装食品24枚

各画像の取得URLとSHA-256を固定し、追加学習用manifestとの画像ID・ページ名の重複がないことを確認しています。

## モデル選定と最終評価の分離

当初報告したFood-5K評価用分割のfood再現率77.8%・non-food偽陽性率0.6%、およびOpen Images validationとWikimedia Commonsを使ったカテゴリ別の数値は、候補モデルや学習データ量の判断に参照していました。そのため独立した最終評価値としては撤回し、採用判断には使用しません。

比較済みの候補を、Android 17のPixel 9 AVD上で `ImageDecoder` のHARDWARE Bitmapから配布対象の `FoodClassifier` を実行して再選定した結果は次のとおりです。各候補について、偽陽性率0.5%以下でfood再現率が最大となる閾値をFood-5K validationだけから選びました。偽陽性率が同じ0.4%の候補では、採用モデルのfood再現率が最も高いため、モデルは維持しています。

| 候補 | 量子化閾値 | food再現率 | non-food偽陽性率 |
| --- | ---: | ---: | ---: |
| 特徴抽出層の末尾30層を追加学習 | 231 | 71.8% | 0.4% |
| 特徴抽出層を固定、Commons追加学習なし | 116 | 82.0% | 0.4% |
| 特徴抽出層を固定、採用構成 | 153 | 83.0% | 0.4% |
| 市販品学習画像を5倍に重複 | 162 | 75.0% | 0.4% |

固定済みのholdoutは候補比較と閾値選定には使用せず、Android経路で選定を完了した後に最終評価だけを再実行しています。

以前の候補比較とholdout評価は、TensorFlowまたはPillowの縮小・余白配置を使用しており、Android実装とpixel-equivalentではありませんでした。このため、以前のvalidation food再現率79.6%・閾値164と、holdout food再現率49.7%を配布時の指標として撤回し、上記Android経路の結果へ置き換えています。

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

python ml/train_model.py \
  --dataset ml/data \
  --output ml/output/model

cd android
./gradlew assembleDebug assembleDebugAndroidTest
cd ..

python ml/evaluate_android.py \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --app-apk android/app/build/outputs/apk/debug/app-debug.apk \
  --test-apk android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --dataset ml/data/validation \
  --model ml/output/model/food_classifier_int8.tflite \
  --work-directory ml/output/android-validation \
  --output ml/output/android-validation/evaluation.json

python ml/evaluate_android.py \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --app-apk android/app/build/outputs/apk/debug/app-debug.apk \
  --test-apk android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --threshold-quantized 153 \
  --manifest ml/evaluation_manifest.json \
  --work-directory ml/output/holdout \
  --output ml/output/holdout/evaluation.json
```

Android 17のエミュレーターまたは実機を起動して実行します。評価スクリプトはAPKとテストAPKをインストールし、画像を端末の一時領域へ転送します。計装テストは各画像をHARDWARE Bitmapとしてデコードし、配布対象の `FoodClassifier.isFood()` をそのまま呼び出します。候補モデルを比較する場合は `--model` で量子化モデルを指定できます。

同梱モデルのSHA-256は `47bdb861c4a51a3b29aa5572c42abe2a55d879f04d70da2c254b0def2b424db4`、固定した追加データmanifestのSHA-256は `d05ef8e20aaee447c1683235f959e26ca4ae5bee9719ead16e95627fadf7f004`、最終評価manifestのSHA-256は `7557dcf89b708e1f9fad2a0dc0565d65ffd0b60b178cb5f7a09f7c4f1d90d2a8` です。

## 最終評価結果

Androidの配布時前処理で固定holdoutを評価した結果、non-food偽陽性率1.05%（2 / 190）、food再現率50.3%（84 / 167）、food適合率97.67%でした。カテゴリ別の結果は次のとおりです。

| 期待値 | カテゴリ | food判定 | 判定率 |
| --- | --- | ---: | ---: |
| non-food | 人物 | 0 / 40 | 0% |
| non-food | 風景 | 0 / 50 | 0% |
| non-food | 端末画面 | 0 / 50 | 0% |
| non-food | 書類 | 2 / 50 | 4% |
| food | 飲み物 | 21 / 50 | 42% |
| food | food全般 | 35 / 50 | 70% |
| food | 缶詰 | 21 / 43 | 48.8% |
| food | 包装食品 | 7 / 24 | 29.2% |

機械可読な選定根拠と結果は [evaluation.json](./evaluation.json) に保存しています。

## 残る課題

- 人物、風景、端末画面、書類のカテゴリ別評価は小規模であり、実際の利用分布に対する誤送信率を保証しません。
- 飲み物、缶詰、包装食品の再現率は低く、Issue #4 の対象全体を実運用水準で満たしたとは判断できません。
- 書類の偽陽性2枚は、人物が描かれた古い舞台ポスターと、本棚を含む木目調の室内写真でした。書類ラベルの画像でも、人物や室内の併写物を含む場合は、現在の二値分類だけでは誤送信を防げない代表例です。
- Pixel実機2台での実写真評価と処理時間・消費電力の計測は未実施です。

最小のガードとして、実写真で定めた偽陽性基準を満たすまでは、この判定結果を外部送信の自動許可に使用しません。
