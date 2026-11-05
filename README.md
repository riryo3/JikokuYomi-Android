# 時刻読み (JikokuYomi) for Android

iOS版「JikokuYomi」の機能を大幅に拡張し、**「誰に・何時を・どう喋らせるか」を完全自由にカスタマイズできる**、野良アプリ（サイドロード配布・自己ビルド）特化型のAndroid時報アプリです。

---

## 👑 「誰に何時をどう喋らせるか」最高マックス機能

1. **誰に（マルチ話者・ボイスパック管理）**:
   - 複数のボイスパック（例: 「ずんだもん」「四国めたん」「春日部つむぎ」など）をフォルダから何個でも追加可能。
   - デフォルトの話者をワンタップで指定可能。
   - 各パックの試聴・削除も簡単。

2. **何時を（自由なタイムライン）**:
   - 24時間の全時刻スロット（15分・30分・60分間隔）を一覧表示。
   - 時刻ごとに**ON/OFF個別切り替え**が可能（例: 7:00, 12:00, 15:00, 22:00 のみ鳴らす、など自由自在）。

3. **どう喋らせるか（セリフと話者の個別指定）**:
   - 各時刻をタップするとカスタマイズ画面が起動：
     - **話者指定**: この時間だけ「四国めたん」に喋らせる、または「標準TTS」にする、などを個別に指定。
     - **セリフ入力**: `{時}` や `{分}` を変数として含めたオリジナルセリフを自由に入力可能！
       - 例: `朝だよ！起きて！{時}時だよ〜`
       - 例: `お昼ご飯の時間です！12時になりました`
       - 例: `おやつの時間！3時だよ`
     - **即時テスト再生**: 設定したその場でテスト試聴可能。

---

## 🌟 野良アプリ（サイドロード）特化の常駐・定時起動
- **Google Playの制限を受けない安定設計**:
  - `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` をフル活用し、スリープ時でも秒単位で確実に起動。
  - **バッテリー最適化のワンタップ無効化 (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**:
    - アプリ内の専用カードから1タップでDozeモードから除外でき、中華端末やGalaxy/Pixelの強力なタスクキラーによる停止を防止。

---

## 🛠 VOICEVOX 音声パックの作り方
親リポジトリの `tools/make_voicevox_pack.py` でパックを作成して端末へ転送するだけです：

```bash
# 1. VOICEVOX を起動（http://127.0.0.1:50021）

# 2. ずんだもん（ノーマル: speaker 3）でパック生成
python3 tools/make_voicevox_pack.py --speaker 3 --out ~/Desktop/zundamon_pack

# 3. 四国めたん（ノーマル: speaker 2）でパック生成
python3 tools/make_voicevox_pack.py --speaker 2 --out ~/Desktop/metan_pack

# 4. 生成したフォルダをAndroid端末に転送し、アプリの「新しいボイスパックを取り込む」で選択！
```

---

## 🚀 ビルド済み APK とインストール方法

### すでにビルド済みのAPK
プロジェクト直下に **`app-debug.apk`** が生成されています！
Android端末にこのファイルを転送（AirDrop、Google Drive、adb install など）してタップするだけでインストール可能です。

```bash
# adb を使って接続中のAndroid端末にインストールする場合:
adb install app-debug.apk
```

### 再度ビルドする場合 (ターミナルまたは Android Studio)
- **ターミナル**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Android Studio**:
  1. `Open` で本フォルダ (`JikokuYomiAndroid`) を開く
  2. `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`
