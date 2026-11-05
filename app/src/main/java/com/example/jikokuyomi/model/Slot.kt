package com.example.jikokuyomi.model

import java.util.Locale

data class Slot(
    val hour: Int,
    val minute: Int
) {
    val id: String
        get() = String.format(Locale.US, "%02d%02d", hour, minute)

    val label: String
        get() = String.format(Locale.US, "%02d:%02d", hour, minute)

    val totalMinutes: Int
        get() = hour * 60 + minute
}
