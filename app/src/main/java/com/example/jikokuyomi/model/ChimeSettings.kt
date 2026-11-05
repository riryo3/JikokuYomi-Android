package com.example.jikokuyomi.model

enum class OmitMode(val label: String) {
    SPEAK_ALL("毎回、時を言う"),
    QUARTERS("15分・45分は時を言わない"),
    ALL_MINUTES("時を言わない（分のみ）");

    fun getDisplayLabel(interval: Int): String {
        return when (this) {
            SPEAK_ALL -> "毎回、時を言う"
            QUARTERS -> "15分・45分は時を言わない"
            ALL_MINUTES -> if (interval == 15) {
                "15分・30分・45分は時を言わない"
            } else {
                "30分は時を言わない"
            }
        }
    }

    fun isMinuteOnly(minute: Int, interval: Int): Boolean {
        if (minute == 0 || interval == 60) return false
        return when (this) {
            SPEAK_ALL -> false
            QUARTERS -> (interval == 15 && (minute == 15 || minute == 45))
            ALL_MINUTES -> if (interval == 15) {
                minute == 15 || minute == 30 || minute == 45
            } else {
                minute == 30
            }
        }
    }
}

data class ChimeSettings(
    val enabled: Boolean = false,
    val startMinutes: Int = 7 * 60,       // 07:00
    val endMinutes: Int = 22 * 60 + 45,   // 22:45
    val interval: Int = 15,                // 15, 30, 60
    val omitMode: OmitMode = OmitMode.SPEAK_ALL,
    val defaultVoiceSource: VoiceSource = VoiceSource.TTS,
    val defaultPackId: String? = null,    // 選択中のデフォルトボイスパックID（"__random__" ならランダム）
    val randomPackIds: Set<String> = emptySet(), // ランダム再生の対象パック（空なら全パック）
    val language: SpeechLanguage = SpeechLanguage.JAPANESE,
    val speechRate: Float = 1.0f,
    val japaneseStyle: JapaneseStyle = JapaneseStyle(),
    val englishStyle: EnglishStyle = EnglishStyle(),
    val customizations: Map<String, SlotCustomization> = emptyMap() // key: "t0700", "t1200", etc.
) {
    fun isWithinActiveHours(totalMinutes: Int): Boolean {
        return if (startMinutes <= endMinutes) {
            totalMinutes in startMinutes..endMinutes
        } else {
            // Over midnight (e.g., 22:00 to 06:00)
            totalMinutes >= startMinutes || totalMinutes <= endMinutes
        }
    }

    fun isSlotEnabled(slot: Slot): Boolean {
        val key = String.format(java.util.Locale.US, "t%02d%02d", slot.hour, slot.minute)
        val custom = customizations[key]
        if (custom != null && !custom.enabled) return false
        return isWithinActiveHours(slot.totalMinutes)
    }

    fun resolveText(slot: Slot): String {
        val key = String.format(java.util.Locale.US, "t%02d%02d", slot.hour, slot.minute)
        val custom = customizations[key]

        // Custom line specified by user takes precedence
        if (!custom?.customLine.isNullOrBlank()) {
            return CustomLines.fill(custom!!.customLine!!, slot.hour, slot.minute)
        }

        // Standard wording
        val minuteOnly = omitMode.isMinuteOnly(slot.minute, interval)
        return when (language) {
            SpeechLanguage.JAPANESE -> {
                if (minuteOnly) japaneseStyle.minuteOnlySpeech(slot.minute)
                else japaneseStyle.speech(slot)
            }
            SpeechLanguage.ENGLISH -> {
                if (minuteOnly) englishStyle.minuteOnlySpeech(slot.minute)
                else englishStyle.speech(slot)
            }
        }
    }
}
