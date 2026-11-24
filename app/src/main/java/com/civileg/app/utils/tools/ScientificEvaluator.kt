package com.civileg.app.utils.tools

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.*

/**
 * Expression evaluator for the calculator. Pure Kotlin, no Android types.
 * Every failure is a typed [Result.Error]; nothing is clamped or silently defaulted.
 *
 * Grammar (highest binding last):
 *   expr   = term (('+'|'-') term)*
 *   term   = unary (('×'|'*'|'÷'|'/') unary)*  or implicit multiplication (2π, 3(4+1), 2sin(30))
 *   unary  = ('+'|'-') unary | power
 *   power  = postfix ('^' unary)?          right associative, so -2^2 = -4 and 2^-1 = 0.5
 *   postfix= primary '!'*                  factorial of a non-negative integer only
 */
object ScientificEvaluator {
    enum class AngleMode { DEG, RAD, GRAD }
    enum class ErrorKind { SYNTAX, DIVIDE_BY_ZERO, DOMAIN, OVERFLOW }

    sealed class Result {
        data class Value(val value: Double) : Result()
        data class Error(val kind: ErrorKind, val detail: String) : Result()
    }

    /** n! is finite in Double only up to 170 (171! > Double.MAX_VALUE). */
    const val MAX_FACTORIAL = 170

    private class Fail(val kind: ErrorKind, val detail: String) : Exception(detail, null, false, false)

    fun evaluate(expr: String, angle: AngleMode = AngleMode.DEG): Result = try {
        val v = Parser(expr, angle).parseAll()
        if (v.isFinite()) Result.Value(v) else Result.Error(ErrorKind.OVERFLOW, "result is not finite")
    } catch (f: Fail) {
        Result.Error(f.kind, f.detail)
    }

    private class Parser(val s: String, val angle: AngleMode) {
        var pos = 0
        fun peek(): Char? = if (pos < s.length) s[pos] else null
        fun skip() { while (peek() == ' ') pos++ }
        fun eat(c: Char): Boolean { skip(); return if (peek() == c) { pos++; true } else false }
        fun syntax(msg: String): Nothing = throw Fail(ErrorKind.SYNTAX, msg)

        fun parseAll(): Double {
            skip()
            if (peek() == null) syntax("empty expression")
            val v = expr()
            skip()
            if (peek() != null) syntax("unexpected '${peek()}' at $pos")
            return v
        }

        fun expr(): Double {
            var x = term()
            while (true) {
                x = when {
                    eat('+') -> x + term()
                    eat('-') -> x - term()
                    else -> return x
                }
            }
        }

        fun startsOperand(): Boolean {
            skip()
            val c = peek() ?: return false
            return c == '(' || c == 'π' || c == '√' || c == '∛' || c.isLetter()
        }

        fun term(): Double {
            var x = unary()
            while (true) {
                skip()
                x = when {
                    eat('×') || eat('*') -> x * unary()
                    eat('÷') || eat('/') -> {
                        val d = unary()
                        if (d == 0.0) throw Fail(ErrorKind.DIVIDE_BY_ZERO, "division by zero")
                        x / d
                    }
                    startsOperand() -> x * unary() // implicit multiplication
                    else -> return x
                }
            }
        }

        fun unary(): Double {
            if (eat('+')) return unary()
            if (eat('-')) return -unary()
            return power()
        }

        fun power(): Double {
            val base = postfix()
            if (eat('^')) {
                val ex = unary()
                if (base == 0.0 && ex < 0.0) throw Fail(ErrorKind.DIVIDE_BY_ZERO, "0 to a negative power")
                val r = base.pow(ex)
                if (r.isNaN()) throw Fail(ErrorKind.DOMAIN, "negative base with non-integer exponent")
                return r
            }
            return base
        }

        fun postfix(): Double {
            var x = primary()
            while (eat('!')) x = factorial(x)
            return x
        }

        fun factorial(x: Double): Double {
            if (x < 0.0 || x != floor(x)) throw Fail(ErrorKind.DOMAIN, "factorial needs a non-negative integer")
            if (x > MAX_FACTORIAL) throw Fail(ErrorKind.OVERFLOW, "factorial above $MAX_FACTORIAL! exceeds the Double range")
            var f = 1.0
            for (i in 2..x.toInt()) f *= i
            return f
        }

        fun number(): Double {
            val start = pos
            var dots = 0
            while (peek()?.let { it.isDigit() || it == '.' } == true) { if (peek() == '.') dots++; pos++ }
            if (dots > 1) syntax("malformed number")
            val text = s.substring(start, pos)
            if (text == ".") syntax("malformed number")
            return text.toDouble()
        }

        fun primary(): Double {
            skip()
            val c = peek() ?: syntax("unexpected end")
            return when {
                c == '(' -> { pos++; val v = expr(); if (!eat(')')) syntax("missing ')'"); v }
                c.isDigit() || c == '.' -> number()
                c == 'π' -> { pos++; PI }
                c == '√' -> { pos++; apply("sqrt", argument()) }
                c == '∛' -> { pos++; apply("cbrt", argument()) }
                c.isLetter() -> {
                    val start = pos
                    while (peek()?.isLetter() == true) pos++
                    val name = s.substring(start, pos)
                    if (name == "e") return E
                    if (name == "pi") return PI
                    skip()
                    if (peek() != '(') syntax("function '$name' needs '('")
                    apply(name, argument())
                }
                else -> syntax("unexpected '$c' at $pos")
            }
        }

        fun argument(): Double {
            skip()
            if (peek() == '(') { pos++; val v = expr(); if (!eat(')')) syntax("missing ')'"); return v }
            return unary() // "√9" without brackets
        }

        fun toRad(x: Double) = when (angle) { AngleMode.DEG -> Math.toRadians(x); AngleMode.RAD -> x; AngleMode.GRAD -> x * PI / 200.0 }
        fun fromRad(x: Double) = when (angle) { AngleMode.DEG -> Math.toDegrees(x); AngleMode.RAD -> x; AngleMode.GRAD -> x * 200.0 / PI }

        /** Quarter-turn exact sine so sin(180°) is 0 and sin(90°) is 1, not 1.2e-16 / rounded values. */
        fun sinA(x: Double): Double {
            val quarters = when (angle) { AngleMode.DEG -> 90.0; AngleMode.GRAD -> 100.0; AngleMode.RAD -> return sin(x) }
            val r = ((x % (4 * quarters)) + 4 * quarters) % (4 * quarters)
            if (r % quarters == 0.0) return when ((r / quarters).toInt()) { 1 -> 1.0; 3 -> -1.0; else -> 0.0 }
            return sin(toRad(x))
        }
        fun cosA(x: Double): Double = when (angle) {
            AngleMode.DEG -> sinA(x + 90.0); AngleMode.GRAD -> sinA(x + 100.0); AngleMode.RAD -> cos(x)
        }

        fun apply(name: String, x: Double): Double {
            fun dom(msg: String): Nothing = throw Fail(ErrorKind.DOMAIN, "$name: $msg")
            return when (name) {
                "sin" -> sinA(x)
                "cos" -> cosA(x)
                "tan" -> {
                    val c = cosA(x)
                    if (angle != AngleMode.RAD && c == 0.0) dom("undefined at odd multiples of 90°")
                    sinA(x) / c
                }
                "asin" -> { if (x < -1.0 || x > 1.0) dom("input must be within [-1, 1]"); fromRad(asin(x)) }
                "acos" -> { if (x < -1.0 || x > 1.0) dom("input must be within [-1, 1]"); fromRad(acos(x)) }
                "atan" -> fromRad(atan(x))
                "sinh" -> sinh(x)
                "cosh" -> cosh(x)
                "tanh" -> tanh(x)
                "log" -> { if (x <= 0.0) dom("input must be > 0"); log10(x) }
                "ln" -> { if (x <= 0.0) dom("input must be > 0"); ln(x) }
                "sqrt" -> { if (x < 0.0) dom("input must be >= 0"); sqrt(x) }
                "cbrt" -> cbrt(x)
                "abs" -> abs(x)
                "exp" -> exp(x)
                else -> throw Fail(ErrorKind.SYNTAX, "unknown function '$name'")
            }
        }
    }

    /**
     * Display text: [digits] significant digits (default 10, as many Casio models show), trailing zeros removed.
     * Plain notation for 1e-4 <= |v| < 1e10, scientific outside, so no Int overflow and no "-0".
     */
    fun format(value: Double, digits: Int = 10): String {
        require(digits in 1..15) { "digits must be 1..15" }
        if (!value.isFinite()) throw IllegalArgumentException("value must be finite")
        if (value == 0.0) return "0"
        val bd = BigDecimal(value).round(MathContext(digits, RoundingMode.HALF_UP))
        val a = abs(bd.toDouble())
        if (a >= 1e-4 && a < 1e10) {
            val plain = bd.stripTrailingZeros().toPlainString()
            return plain
        }
        val unscaled = bd.unscaledValue().abs().toString().trimEnd('0').ifEmpty { "0" }
        val exp = bd.precision() - bd.scale() - 1
        val mant = if (unscaled.length > 1) unscaled[0] + "." + unscaled.substring(1) else unscaled
        return String.format(Locale.ROOT, "%s%se%s%02d", if (bd.signum() < 0) "-" else "", mant, if (exp < 0) "-" else "+", abs(exp))
    }
}
