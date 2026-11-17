package com.example.phantomtouch

class Key(val label: String, val code: String, val weight: Float)

object KeyLayout {

    private fun k(l: String, w: Float = 1f): Key = Key(l, l, w)

    private fun s(label: String, code: String, w: Float): Key = Key(label, code, w)

    private fun nums(): List<Key> = "1234567890".map { k(it.toString()) }

    private fun bottom(symLabel: String): List<Key> = listOf(
        s(symLabel, "#SYM", 1.5f),
        k(","),
        s("space", "#SPACE", 4f),
        k("."),
        s("enter", "#ENTER", 1.5f),
        s("X", "#CLOSE", 1f)
    )

    val letters: List<List<Key>> = listOf(
        nums(),
        "qwertyuiop".map { k(it.toString()) },
        listOf(s("", "#NONE", 0.5f)) +
            "asdfghjkl".map { k(it.toString()) } +
            listOf(s("", "#NONE", 0.5f)),
        listOf(s("shift", "#SHIFT", 1.5f)) +
            "zxcvbnm".map { k(it.toString()) } +
            listOf(s("del", "#BKSP", 1.5f)),
        bottom("?123")
    )

    val symbols: List<List<Key>> = listOf(
        nums(),
        "@#\$%&-+()/".map { k(it.toString()) },
        "*\"':;!?_=\\".map { k(it.toString()) },
        listOf(k("~", 1.5f)) +
            "<>{}[]|".map { k(it.toString()) } +
            listOf(s("del", "#BKSP", 1.5f)),
        bottom("ABC")
    )

    fun rows(sym: Boolean): List<List<Key>> = if (sym) symbols else letters

    // nx and ny are 0..1 inside the keyboard area.
    fun find(rows: List<List<Key>>, nx: Float, ny: Float): Key? {
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
        val r = (ny * rows.size).toInt().coerceIn(0, rows.size - 1)
        val row = rows[r]
        var total = 0f
        for (key in row) total += key.weight
        val target = nx * total
        var acc = 0f
        for (key in row) {
            acc += key.weight
            if (target < acc) return key
        }
        return row.last()
    }
}