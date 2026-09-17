# MealRelay

MealRelay は、日常の食事記録をできるだけ操作を増やさずに蓄積し、歩数や活動量などと合わせて健康状態や傾向を確認できる形で Google Health へ記録することを目指すプロジェクトです。

プロジェクト全体の要件と方針は [Issue #1](https://github.com/bvlion/MealRelay/issues/1) を正とします。開発・運用時の共通ルールは [AGENTS.md](./AGENTS.md) を参照してください。

## リポジトリ構成

```text
.
├── android/   # Android アプリ関連資産
├── backend/   # Backend 関連資産
├── AGENTS.md  # リポジトリ全体の開発・運用ルール
└── README.md
```

各ディレクトリの詳細は、それぞれの README を参照してください。現時点で Android と Backend の具体的な技術選定や詳細構成は固定しません。food / non-food 判定用モデルと関連資産の配置・管理方式は [Issue #4](https://github.com/bvlion/MealRelay/issues/4) で決定します。
