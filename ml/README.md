# food / non-food 判定PoC

Issue #4 の判断基準に従い、non-food の誤送信につながる偽陽性を食事写真の偽陰性より重く扱う端末内二値分類のPoCです。学習用画像の取得と学習は開発環境で行いますが、Androidでの判定時に通信は発生しません。

## 採用内容

| 項目 | 採用内容 | 判断理由 |
| --- | --- | --- |
| モデル | ImageNetで事前学習した MobileNetV3Small、224 × 224入力 | 全整数量子化後が1,218,032バイトで、Android上の最小構成を検証できたため |
| 学習 | 特徴抽出層を固定し、二値分類層だけを学習 | 特徴抽出層の末尾30層を追加学習した比較では、独立評価の偽陽性率が悪化したため |
| 学習データ | Food-5Kに Open Images と Wikimedia Commons の対象画像を追加 | Food-5Kだけでは端末画面、飲み物、市販品の対象例が不足するため |
| 量子化 | 重み・演算・入出力を全整数化し、入出力を `uint8` に統一 | Android側で浮動小数点の前後処理を不要にし、CPUで実行できるため |
| 閾値 | 量子化出力164以上を food とする | 検証用500枚の non-food に対する偽陽性率を0.5%以下に制約し、その範囲で food 再現率が最大となる点を選んだため |
| Android実行 | LiteRT Interpreter 1.4.2、CPU 2スレッド | 単一入出力モデルに必要な依存関係だけで構成でき、Android 17の16 KBページ環境で動作確認できたため |

閾値164は、出力量子化スケール1 / 256、ゼロ点0における0.640625です。評価用分割の結果を見て閾値を調整していません。

## データ

基礎データには [Food-5KのKaggleミラー](https://www.kaggle.com/datasets/trolukovich/food5k-image-dataset)を使用します。配布元の表示ライセンスは CC0 1.0 です。取得したZIPのSHA-256は `6838829edbb56c4859e733960050d2a59974e52848093a61abdcfb1b9a580f09` です。

基礎データの構成は次のとおりです。

| 分割 | food | non-food |
| --- | ---: | ---: |
| 学習 | 1,500 | 1,500 |
| 検証 | 500 | 500 |
| 評価 | 500 | 500 |

`prepare_supplemental_data.py` は、学習用分割だけに次の400枚を追加します。

- [Open Images V5](https://storage.googleapis.com/openimages/web/index.html) の端末画面100枚、飲み物100枚、food 100枚
- [Wikimedia Commons](https://commons.wikimedia.org/) の包装食品、缶詰、包装済みサンドイッチ、包装済み寿司、合計100枚

追加後の学習用分割は food 1,800枚、non-food 1,600枚です。採用モデルで使用した画像ID、取得URL、画像ごとのSHA-256、配布元URL、ライセンスは [supplemental_manifest.json](./supplemental_manifest.json) に固定しています。取得処理はカテゴリの現在内容から画像を再選択せず、このmanifestだけを参照します。取得不能またはSHA-256不一致の場合は処理を失敗させます。Wikimedia Commonsの作者情報は各配布元URLから確認できます。学習画像自体はリポジトリへ含めません。

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

python ml/train_and_evaluate.py \
  --dataset ml/data \
  --output ml/output/model

python ml/evaluate_open_images.py \
  --model ml/output/model/food_classifier_int8.tflite \
  --threshold-quantized 164 \
  --work-directory ml/output/open_images \
  --excluded-manifest ml/supplemental_manifest.json \
  --output ml/output/open_images/evaluation.json

python ml/evaluate_food_directory.py \
  --model ml/output/model/food_classifier_int8.tflite \
  --threshold-quantized 164 \
  --images ml/output/supplemental/packaged_evaluation \
  --output ml/output/supplemental/packaged_evaluation.json
```

同梱モデルのSHA-256は `47bdb861c4a51a3b29aa5572c42abe2a55d879f04d70da2c254b0def2b424db4`、固定した追加データmanifestのSHA-256は `f65ce1272bddb841f0a37c034f4074d3c8267ba2e5fb39ed6f6d73b1a2d4a141` です。

## 評価結果

Food-5Kの評価用分割1,000枚では、food再現率77.8%、non-food偽陽性率0.6%（3 / 500）、food適合率99.23%でした。カテゴリ別の独立評価と比較実験を含む機械可読な結果は [evaluation.json](./evaluation.json) に保存しています。

追加学習に使用した画像IDをすべて除外したOpen Images V5の評価結果は次のとおりです。風景は `Tree`、`House`、`Building` の画像ラベルを代替指標にしています。

| 期待値 | カテゴリ | food判定 | 判定率 |
| --- | --- | ---: | ---: |
| non-food | 人物 | 0 / 40 | 0% |
| non-food | 風景 | 0 / 50 | 0% |
| non-food | 端末画面 | 1 / 50 | 2% |
| non-food | 書類 | 0 / 50 | 0% |
| food | 飲み物 | 21 / 50 | 42% |
| food | food全般 | 33 / 50 | 66% |

市販品に近いWikimedia Commonsの未学習画像67枚では、food再現率44.8%でした。学習用の市販品100枚を5倍に重複させた比較では、市販品の再現率は46.3%にとどまり、Food-5Kのfood再現率が72.4%へ低下したため採用していません。

## 残る課題

- 人物、風景、端末画面、書類のカテゴリ別評価は小規模であり、実際の利用分布に対する誤送信率を保証しません。
- 飲み物と市販品の再現率は低く、Issue #4 の対象全体を実運用水準で満たしたとは判断できません。
- 端末画面の評価では、テレビが画面の一部にあり、空のボウルが大きく写る室内写真1枚を food と誤判定しました。端末画面に食事らしい物体が併写されると、現在の二値分類だけでは誤送信を防げない代表例です。
- Pixel実機2台での実写真評価と処理時間・消費電力の計測は未実施です。

最小のガードとして、実写真で定めた偽陽性基準を満たすまでは、この判定結果を外部送信の自動許可に使用しません。
