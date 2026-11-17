package com.example.phantomtouch

class KeyTyper {

    var shift = 0
    var sym = false
    var typed = 0
    var deleted = 0

    fun rows(): List<List<Key>> = KeyLayout.rows(sym)

    fun resetState() {
        shift = 0
        sym = false
    }

    fun resetCounts() {
        typed = 0
        deleted = 0
    }

    private fun code(r: Int): String = when (r) {
        0 -> "OK"
        2 -> "SECURE"
        else -> "NOFIELD"
    }

    // Returns: "", "OK", "CLOSE", "NOFIELD", "SECURE" or "NOACC"
    fun press(key: Key, blind: Boolean): String {
        if (key.code == "#NONE") return ""
        if (key.code == "#SHIFT") {
            shift = (shift + 1) % 3
            return ""
        }
        if (key.code == "#SYM") {
            if (!blind) sym = !sym
            return ""
        }
        if (key.code == "#CLOSE") {
            return if (blind) "" else "CLOSE"
        }
        val acc = PhantomAccess.instance ?: return "NOACC"
        if (key.code == "#BKSP") {
            val r = acc.backspace()
            if (r == 0) deleted++
            return code(r)
        }
        if (key.code == "#ENTER") {
            return code(acc.enter())
        }
        var text = if (key.code == "#SPACE") " " else key.label
        if (shift > 0 && !sym) text = text.uppercase()
        val r = acc.typeText(text)
        if (r == 0) {
            typed++
            if (shift == 1) shift = 0
        }
        return code(r)
    }
}
