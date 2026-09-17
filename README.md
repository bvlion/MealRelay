# MealRelay

MealRelay は、日常の食事記録をできるだけ操作を増やさずに蓄積し、歩数や活動量などと合わせて健康状態や傾向を確認できる形で Google Health へ記録することを目指すプロジェクトです。

プロジェクト全体の要件と方針は [Issue #1](https://github.com/bvlion/MealRelay/issues/1) を正とします。開発・運用時の共通ルールは [AGENTS.md](./AGENTS.md) を参照してください。

## リポジトリ構成

```text
.
├── android/   # Android アプリ関連資産
├── backend/   # Backend 関連資産
├── ml/        # ML / model-related assets
├── AGENTS.md  # リポジトリ全体の開発・運用ルール
└── README.md
```

各ディレクトリの詳細は、それぞれの README を参照してください。現時点で Android、Backend、ML / model-related assets の具体的な技術選定や詳細構成は固定しません。後続 Issue の要件に基づき、必要になった段階で決定します。
