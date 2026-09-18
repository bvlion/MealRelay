# food / non-food 判定PoC

Issue #4 の判断基準に従い、non-food の誤送信につながる偽陽性を食事写真の偽陰性より重く扱う端末内二値分類のPoCです。学習用画像の取得と学習は開発環境で行いますが、Androidでの判定時に通信は発生しません。

## 採用内容

| 項目 | 採用内容 | 判断理由 |
| --- | --- | --- |
| モデル | ImageNetで事前学習した MobileNetV3Small、224 × 224入力 | 全整数量子化後が1,218,032バイトで、Android上の最小構成を検証できたため |
| 学習 | 特徴抽出層を固定し、二値分類層だけを学習 | Food-5K validationで、同じ偽陽性率0.4%の比較候補の中でfood再現率が最も高かったため |
| 学習データ | Food-5Kに Open Images と Wikimedia Commons の対象画像を追加 | Food-5Kだけでは端末画面、飲み物、市販品の対象例が不足するため |
| 量子化 | 重み・演算・入出力を全整数化し、入出力を `uint8` に統一 | Android側で浮動小数点の前後処理を不要にし、CPUで実行できるため |
| 閾値 | 量子化出力164以上を food とする | Food-5K validation 500枚のnon-foodに対する偽陽性率を0.5%以下に制約し、その範囲でfood再現率が最大となる点を選んだため |
| Android実行 | LiteRT Interpreter 1.4.2、CPU 2スレッド | 単一入出力モデルに必要な依存関係だけで構成でき、Android 17の16 KBページ環境で動作確認できたため |

閾値164は、出力量子化スケール1 / 256、ゼロ点0における0.640625です。最終holdoutの結果を見てモデル、学習方法、サンプリング、閾値を変更していません。

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

最終評価には [evaluation_manifest.json](./evaluation_manifest.json) に固定した357枚を使用します。モデル構成と閾値をvalidationだけで再選定した後、推論結果を見ずに候補を確定しました。

- Open Images V5のtest分割: 人物40枚、風景50枚、端末画面50枚、書類50枚、飲み物50枚、food全般50枚
- 学習・tuning・過去の評価に使用していないWikimedia Commons画像: 缶詰43枚、包装食品24枚

各画像の取得URLとSHA-256を固定し、追加学習用manifestとの画像ID・ページ名の重複がないことを確認しています。

## モデル選定と最終評価の分離

当初報告したFood-5K評価用分割のfood再現率77.8%・non-food偽陽性率0.6%、およびOpen Images validationとWikimedia Commonsを使ったカテゴリ別の数値は、候補モデルや学習データ量の判断に参照していました。そのため独立した最終評価値としては撤回し、採用判断には使用しません。

比較済みの候補をFood-5K validationだけで再選定した結果は次のとおりです。偽陽性率が同じ0.4%の候補では、採用モデルのfood再現率が最も高く、モデルと閾値の変更は不要でした。

| 候補 | food再現率 | non-food偽陽性率 |
| --- | ---: | ---: |
| 特徴抽出層の末尾30層を追加学習 | 73.6% | 0.4% |
| 特徴抽出層を固定、Commons追加学習なし | 74.8% | 0.4% |
| 特徴抽出層を固定、採用構成 | 79.6% | 0.4% |
| 市販品学習画像を5倍に重複 | 71.0% | 0.4% |

この再選定後に固定した新規holdoutへ推論を1回だけ実施し、その結果を最終評価としています。

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

python ml/train_and_select.py \
  --dataset ml/data \
  --output ml/output/model

python ml/evaluate_holdout.py \
  --model ml/output/model/food_classifier_int8.tflite \
  --threshold-quantized 164 \
  --manifest ml/evaluation_manifest.json \
  --work-directory ml/output/holdout \
  --output ml/output/holdout/evaluation.json
```

同梱モデルのSHA-256は `47bdb861c4a51a3b29aa5572c42abe2a55d879f04d70da2c254b0def2b424db4`、固定した追加データmanifestのSHA-256は `d05ef8e20aaee447c1683235f959e26ca4ae5bee9719ead16e95627fadf7f004`、最終評価manifestのSHA-256は `7557dcf89b708e1f9fad2a0dc0565d65ffd0b60b178cb5f7a09f7c4f1d90d2a8` です。

## 最終評価結果

固定したholdout全体では、non-food偽陽性率1.05%（2 / 190）、food再現率49.7%（83 / 167）、food適合率97.65%でした。カテゴリ別の結果は次のとおりです。

| 期待値 | カテゴリ | food判定 | 判定率 |
| --- | --- | ---: | ---: |
| non-food | 人物 | 0 / 40 | 0% |
| non-food | 風景 | 0 / 50 | 0% |
| non-food | 端末画面 | 0 / 50 | 0% |
| non-food | 書類 | 2 / 50 | 4% |
| food | 飲み物 | 19 / 50 | 38% |
| food | food全般 | 35 / 50 | 70% |
| food | 缶詰 | 21 / 43 | 48.8% |
| food | 包装食品 | 8 / 24 | 33.3% |

機械可読な選定根拠と結果は [evaluation.json](./evaluation.json) に保存しています。

## 残る課題

- 人物、風景、端末画面、書類のカテゴリ別評価は小規模であり、実際の利用分布に対する誤送信率を保証しません。
- 飲み物、缶詰、包装食品の再現率は低く、Issue #4 の対象全体を実運用水準で満たしたとは判断できません。
- 書類の偽陽性2枚は、広告ポスターの集合写真と、本棚を含む木目調の室内写真でした。書類ラベルの画像でも、食事らしい色・構図や室内の併写物を含む場合は、現在の二値分類だけでは誤送信を防げない代表例です。
- Pixel実機2台での実写真評価と処理時間・消費電力の計測は未実施です。

最小のガードとして、実写真で定めた偽陽性基準を満たすまでは、この判定結果を外部送信の自動許可に使用しません。
