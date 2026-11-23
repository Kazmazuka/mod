# Realistic View (NeoForge 1.21.1 / クライアント専用)

- リアルなモーションブラー: 深度バッファ再投影。回転・移動・スプリントFOV変化のすべてがブラーになる。露光時間はfpsに依存しない
- リアルな歩行の揺れ: 上下/左右/前後/ヨー/ロール/ピッチ + 接地の衝撃 + ストレイフ傾き + 着地衝撃 + 呼吸
- 一人称で自分の体を表示(頭・腕は非表示、下を向くと体が見える)

設定: ゲーム内 Mods > Realistic View > Config (または config/realisticview-client.toml)

## ビルド
- GitHubにpushすると Actions が jar を作る(Actions > Artifacts > realisticview-jar)
- ローカル: Java 21 + Gradle 8.10 で `gradle build` → build/libs/realisticview-1.0.0.jar
- 開発実行: `gradle runClient`

jar を mods フォルダに入れる(NeoForge 21.1.x / Minecraft 1.21.1)。
