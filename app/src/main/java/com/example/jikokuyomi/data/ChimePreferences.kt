package com.example.jikokuyomi.data

import android.content.Context
import android.content.SharedPreferences
import com.example.jikokuyomi.model.*
import org.json.JSONObject

class ChimePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jikokuyomi_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): ChimeSettings {
        val enabled = prefs.getBoolean("enabled", false)
        val startMinutes = prefs.getInt("startMinutes", 7 * 60)
        val endMinutes = prefs.getInt("endMinutes", 22 * 60 + 45)
        val interval = prefs.getInt("interval", 15)
        val omitMode = OmitMode.valueOf(prefs.getString("omitMode", OmitMode.SPEAK_ALL.name) ?: OmitMode.SPEAK_ALL.name)
        val voiceSource = VoiceSource.valueOf(prefs.getString("defaultVoiceSource", VoiceSource.TTS.name) ?: VoiceSource.TTS.name)
        val defaultPackId = prefs.getString("defaultPackId", null)
        val language = SpeechLanguage.valueOf(prefs.getString("language", SpeechLanguage.JAPANESE.name) ?: SpeechLanguage.JAPANESE.name)
        val speechRate = prefs.getFloat("speechRate", 1.0f)

        val jaTwelveHour = prefs.getBoolean("ja_twelveHour", true)
        val jaAmpmAfter = prefs.getBoolean("ja_ampmAfter", false)
        val jaEnding = JapaneseEnding.valueOf(prefs.getString("ja_ending", JapaneseEnding.DESU.name) ?: JapaneseEnding.DESU.name)
        val jaHalfWord = prefs.getBoolean("ja_halfWord", true)
        val jaNoonWord = prefs.getBoolean("ja_noonWord", true)
        val jaTadaima = prefs.getBoolean("ja_tadaima", false)

        val enSingleDigit = EnglishSingleDigitHour.valueOf(
            prefs.getString("en_singleDigit", EnglishSingleDigitHour.OH.name) ?: EnglishSingleDigitHour.OH.name
        )
        val enMidnight = prefs.getBoolean("en_midnight", true)
        val enIts = prefs.getBoolean("en_its", false)
        val enTopOfHour = EnglishTopOfHour.valueOf(
            prefs.getString("en_topOfHour", EnglishTopOfHour.OCLOCK.name) ?: EnglishTopOfHour.OCLOCK.name
        )

        // Load customizations map
        val customizations = mutableMapOf<String, SlotCustomization>()
        val customJsonStr = prefs.getString("customizations_json", null)
        if (!customJsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(customJsonStr)
                for (key in json.keys()) {
                    val obj = json.getJSONObject(key)
                    val slotKey = obj.optString("slotKey", key)
                    val isEnabled = obj.optBoolean("enabled", true)
                    val speaker = if (obj.has("speakerPackId") && !obj.isNull("speakerPackId")) obj.getString("speakerPackId") else null
                    val line = if (obj.has("customLine") && !obj.isNull("customLine")) obj.getString("customLine") else null
                    customizations[key] = SlotCustomization(
                        slotKey = slotKey,
                        enabled = isEnabled,
                        speakerPackId = speaker,
                        customLine = line
                    )
                }
            } catch (_: Exception) {}
        }

        return ChimeSettings(
            enabled = enabled,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            interval = interval,
            omitMode = omitMode,
            defaultVoiceSource = voiceSource,
            defaultPackId = defaultPackId,
            language = language,
            speechRate = speechRate,
            japaneseStyle = JapaneseStyle(
                twelveHour = jaTwelveHour,
                ampmAfter = jaAmpmAfter,
                ending = jaEnding,
                halfWord = jaHalfWord,
                noonWord = jaNoonWord,
                tadaima = jaTadaima
            ),
            englishStyle = EnglishStyle(
                singleDigitHour = enSingleDigit,
                midnightWord = enMidnight,
                its = enIts,
                topOfHour = enTopOfHour
            ),
            customizations = customizations
        )
    }

    fun saveSettings(settings: ChimeSettings) {
        prefs.edit().apply {
            putBoolean("enabled", settings.enabled)
            putInt("startMinutes", settings.startMinutes)
            putInt("endMinutes", settings.endMinutes)
            putInt("interval", settings.interval)
            putString("omitMode", settings.omitMode.name)
            putString("defaultVoiceSource", settings.defaultVoiceSource.name)
            putString("defaultPackId", settings.defaultPackId)
            putString("language", settings.language.name)
            putFloat("speechRate", settings.speechRate)

            putBoolean("ja_twelveHour", settings.japaneseStyle.twelveHour)
            putBoolean("ja_ampmAfter", settings.japaneseStyle.ampmAfter)
            putString("ja_ending", settings.japaneseStyle.ending.name)
            putBoolean("ja_halfWord", settings.japaneseStyle.halfWord)
            putBoolean("ja_noonWord", settings.japaneseStyle.noonWord)
            putBoolean("ja_tadaima", settings.japaneseStyle.tadaima)

            putString("en_singleDigit", settings.englishStyle.singleDigitHour.name)
            putBoolean("en_midnight", settings.englishStyle.midnightWord)
            putBoolean("en_its", settings.englishStyle.its)
            putString("en_topOfHour", settings.englishStyle.topOfHour.name)

            // Save customizations map as JSON
            val json = JSONObject()
            for ((key, custom) in settings.customizations) {
                val obj = JSONObject().apply {
                    put("slotKey", custom.slotKey)
                    put("enabled", custom.enabled)
                    put("speakerPackId", custom.speakerPackId)
                    put("customLine", custom.customLine)
                }
                json.put(key, obj)
            }
            putString("customizations_json", json.toString())

            apply()
        }
    }
}
