package com.example.jikokuyomi.model

data class SlotCustomization(
    val slotKey: String,                // e.g. "t0700", "t1200", "m15"
    val enabled: Boolean = true,        // この時刻を鳴らすかどうか
    val speakerPackId: String? = null,  // null: デフォルト設定に従う, "__tts__": 強制TTS, またはボイスパックID
    val customLine: String? = null      // null: 標準の読み上げ, 入力時はカスタムセリフ（{時}, {分}対応）
)
