package com.civileg.app.domain.modeler

/** Entry parsing never substitutes a default. Signed finite loads/coordinates are valid. */
object FrameInputValidation {
    enum class Rule { FINITE, POSITIVE, NON_NEGATIVE, ID, REQUIRED }
    fun normalized(text: String): String = buildString {
        text.trim().forEach { c -> append(when {
            Character.digit(c, 10) >= 0 -> ('0'.code + Character.digit(c, 10)).toChar()
            c == '\u066B' -> '.'
            c == '\u2212' -> '-'
            else -> c
        }) }
    }
    fun number(text: String): Double? = normalized(text).toDoubleOrNull()?.takeIf { it.isFinite() }
    fun id(text: String): Int? = normalized(text).toIntOrNull()?.takeIf { it > 0 }
    fun valid(text: String, rule: Rule): Boolean = when (rule) {
        Rule.REQUIRED -> text.isNotBlank()
        Rule.ID -> id(text) != null
        Rule.FINITE -> number(text) != null
        Rule.POSITIVE -> number(text)?.let { it > 0.0 } == true
        Rule.NON_NEGATIVE -> number(text)?.let { it >= 0.0 } == true
    }
    fun positionValid(type: com.civileg.app.domain.entities.MemberLoadType, text: String, length: Double): Boolean {
        if (type == com.civileg.app.domain.entities.MemberLoadType.UDL || type == com.civileg.app.domain.entities.MemberLoadType.LinearVarying) return true
        val position = number(text) ?: return false
        if (!length.isFinite() || length <= 1e-6) return false
        return if (type == com.civileg.app.domain.entities.MemberLoadType.Trapezoidal) position > 0.0 && position <= length / 2.0 + 1e-9
        else position >= -1e-9 && position <= length + 1e-9
    }
}
