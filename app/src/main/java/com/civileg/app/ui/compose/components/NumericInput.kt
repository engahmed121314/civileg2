package com.civileg.app.ui.compose.components

/**
 * Normalizes what a user types into a numeric field so every screen's plain parse works:
 * Arabic-Indic and Extended Arabic-Indic digits become Western digits, the Arabic decimal
 * separator and a single comma (when there is no dot) become a dot. Nothing else is changed,
 * so invalid text stays invalid and is reported by the validator, never silently fixed.
 */
internal fun normalizeNumericInput(raw: String): String {
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
        sb.append(
            when (ch) {
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                '\u066B' -> '.'
                else -> ch
            }
        )
    }
    val s = sb.toString()
    return if (s.count { it == ',' } == 1 && !s.contains('.')) s.replace(',', '.') else s
}
