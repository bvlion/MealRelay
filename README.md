# MealRelay

MealRelay は、日常の食事記録をできるだけ操作を増やさずに蓄積し、歩数や活動量などと合わせて健康状態や傾向を確認できる形で Google Health へ記録することを目指すプロジェクトです。

プロジェクト全体の要件と方針は [Issue #1](https://github.com/bvlion/MealRelay/issues/1) を正とします。開発・運用時の共通ルールは [AGENTS.md](./AGENTS.md) を参照してください。

## リポジトリ構成

```text
.
├── android/   # Android アプリ関連資産
├── backend/   # Backend 関連資産
├── ml/        # food / non-food 判定モデルの学習・評価資産
├── AGENTS.md  # リポジトリ全体の開発・運用ルール
└── README.md
```

各ディレクトリの詳細は、それぞれの README を参照してください。Backend は Cloud Functions Gen2 / Node.js 24 を使用します。food / non-food 判定のPoCについては [ml/README.md](./ml/README.md) を参照してください。
