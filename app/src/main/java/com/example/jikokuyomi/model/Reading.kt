package com.example.jikokuyomi.model

enum class SpeechLanguage(val label: String, val code: String) {
    JAPANESE("日本語", "ja"),
    ENGLISH("English", "en")
}

enum class JapaneseEnding(val label: String, val kana: String) {
    DESU("です", "です"),
    NARIMASHITA("になりました", "になりました"),
    NONE("なし", "")
}

data class JapaneseStyle(
    val twelveHour: Boolean = true,
    val ampmAfter: Boolean = false,
    val ending: JapaneseEnding = JapaneseEnding.DESU,
    val halfWord: Boolean = true,
    val noonWord: Boolean = true,
    val tadaima: Boolean = false
) {
    companion object {
        private val ONES_KANA = arrayOf("", "いち", "に", "さん", "よ", "ご", "ろく", "しち", "はち", "く")

        fun hourKana(h: Int): String {
            return when (h) {
                0 -> "れい"
                in 1..9 -> ONES_KANA[h]
                10 -> "じゅう"
                in 11..19 -> "じゅう" + ONES_KANA[h - 10]
                20 -> "にじゅう"
                else -> "にじゅう" + ONES_KANA[h - 20]
            }
        }
    }

    private fun isHalf(m: Int) = m == 30 && halfWord

    private fun isNoon(slot: Slot) = twelveHour && slot.hour == 12 && slot.minute == 0 && noonWord

    private fun minuteSpeech(m: Int) = if (isHalf(m)) "はん" else minuteDisplay(m)

    private fun minuteDisplay(m: Int) = if (isHalf(m)) "半" else "${m}分"

    private fun parts(slot: Slot): Pair<String, String> {
        val h = slot.hour
        if (isNoon(slot)) {
            return Pair("正午", "正午")
        }
        val shown = if (twelveHour) {
            val mod = h % 12
            if (mod == 0) 12 else mod
        } else h

        var display = "${shown}時"
        var speech: String

        if (isHalf(slot.minute)) {
            speech = "${hourKana(shown)}じ はん"
            display += minuteDisplay(slot.minute)
        } else {
            speech = (if (shown == 0) "零" else "$shown") + "時"
            if (slot.minute != 0) {
                speech += minuteSpeech(slot.minute)
                display += minuteDisplay(slot.minute)
            }
        }

        if (!twelveHour) {
            return Pair(speech, display)
        }

        val ampm = if (h < 12) "午前" else "午後"
        return if (ampmAfter) {
            Pair("$speech、$ampm", "$display$ampm")
        } else {
            Pair("$ampm $speech", "$ampm$display")
        }
    }

    fun speech(slot: Slot): String {
        val prefix = if (tadaima) "ただいま、" else ""
        val (speechPart, _) = parts(slot)
        val separator = if (twelveHour && ampmAfter && !isNoon(slot)) " " else ""
        return prefix + speechPart + separator + ending.kana
    }

    fun display(slot: Slot): String {
        val prefix = if (tadaima) "ただいま、" else ""
        val (_, displayPart) = parts(slot)
        return prefix + displayPart + ending.label
    }

    fun minuteOnlySpeech(minute: Int): String {
        return minuteSpeech(minute) + ending.kana
    }

    fun minuteOnlyDisplay(minute: Int): String {
        return minuteDisplay(minute) + ending.label
    }
}

enum class EnglishSingleDigitHour {
    OH, ZERO, PLAIN
}

enum class EnglishTopOfHour {
    OCLOCK, HUNDRED
}

data class EnglishStyle(
    val singleDigitHour: EnglishSingleDigitHour = EnglishSingleDigitHour.OH,
    val midnightWord: Boolean = true,
    val its: Boolean = false,
    val topOfHour: EnglishTopOfHour = EnglishTopOfHour.OCLOCK
) {
    companion object {
        private val ONES = arrayOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
        )
        private val TENS = arrayOf("", "", "twenty", "thirty", "forty", "fifty")

        fun word(n: Int): String {
            return when {
                n < 20 -> ONES[n]
                n % 10 == 0 -> TENS[n / 10]
                else -> "${TENS[n / 10]} ${ONES[n % 10]}"
            }
        }
    }

    fun speech(slot: Slot): String {
        val prefix = if (its) "It's " else ""
        return prefix + bareText(slot)
    }

    private fun bareText(slot: Slot): String {
        if (slot.minute == 0) {
            if (slot.hour == 0 && midnightWord) return "midnight"
            val hourWord = word(slot.hour)
            return if (topOfHour == EnglishTopOfHour.HUNDRED) "$hourWord hundred" else "$hourWord o'clock"
        }
        val hour = when (slot.hour) {
            0 -> if (singleDigitHour == EnglishSingleDigitHour.PLAIN) "zero" else "zero zero"
            in 1..9 -> when (singleDigitHour) {
                EnglishSingleDigitHour.OH -> "oh " + ONES[slot.hour]
                EnglishSingleDigitHour.ZERO -> "zero " + ONES[slot.hour]
                EnglishSingleDigitHour.PLAIN -> ONES[slot.hour]
            }
            else -> word(slot.hour)
        }
        return "$hour ${word(slot.minute)}"
    }

    fun minuteOnlySpeech(minute: Int): String = word(minute)
}
