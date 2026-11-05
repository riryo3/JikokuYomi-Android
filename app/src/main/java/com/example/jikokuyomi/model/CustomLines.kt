package com.example.jikokuyomi.model

object CustomLines {
    fun fill(template: String, hour: Int, minute: Int): String {
        return template
            .replace("{時}", hour.toString())
            .replace("{h}", hour.toString())
            .replace("{分}", minute.toString())
            .replace("{m}", minute.toString())
    }
}
