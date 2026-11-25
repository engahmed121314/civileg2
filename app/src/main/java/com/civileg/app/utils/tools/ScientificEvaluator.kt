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
    enum class ErrorKind { SYNTAX, DIVIDE_BY_ZERO, DOMAIN, OVERFLOW, LIMIT }

    sealed class Result {
        data class Value(val value: Double) : Result()
        data class Error(val kind: ErrorKind, val detail: String) : Result()
    }

    /** n! is finite in Double only up to 170 (171! > Double.MAX_VALUE). */
    const val MAX_FACTORIAL = 170

    /**
     * Implementation safety limits (not engineering limits). Longer or deeper input is rejected with
     * [ErrorKind.LIMIT]; it is never truncated or clamped. Depth counts nested expr()/unary() calls,
     * so 200 allows roughly 100 nested parentheses, far below the JVM stack limit.
     */
    const val MAX_INPUT_LENGTH = 500
    const val MAX_DEPTH = 200

    /** Number of 2-ulp steps around an odd multiple of pi/2 treated as the RAD tangent pole. */
    private const val RAD_POLE_ULPS = 2.0

    private class Fail(val kind: ErrorKind, val detail: String) : Exception(detail, null, false, false)

    fun evaluate(expr: String, angle: AngleMode = AngleMode.DEG): Result = try {
        if (expr.length > MAX_INPUT_LENGTH) throw Fail(ErrorKind.LIMIT, "expression longer than $MAX_INPUT_LENGTH characters")
        val v = Parser(expr, angle).parseAll()
        if (v.isFinite()) Result.Value(v) else Result.Error(ErrorKind.OVERFLOW, "result is not finite")
    } catch (f: Fail) {
        Result.Error(f.kind, f.detail)
    }

    private class Parser(val s: String, val angle: AngleMode) {
        var pos = 0
        var depth = 0
        fun <T> nested(f: () -> T): T {
            if (++depth > MAX_DEPTH) throw Fail(ErrorKind.LIMIT, "expression nested deeper than $MAX_DEPTH levels")
            try { return f() } finally { depth-- }
        }
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

        fun expr(): Double = nested { exprBody() }

        fun exprBody(): Double {
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

        fun unary(): Double = nested { unaryBody() }

        fun unaryBody(): Double {
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
            if (s.substring(start, pos) == ".") syntax("malformed number")
            // Exponent form 1.5e-05 / 2E3: only when 'e' is directly followed by [sign] digit. "2e" and "2e^2" stay 2 x e.
            val c = peek()
            if (c == 'e' || c == 'E') {
                var q = pos + 1
                if (q < s.length && (s[q] == '+' || s[q] == '-')) q++
                if (q < s.length && s[q].isDigit()) {
                    while (q < s.length && s[q].isDigit()) q++
                    pos = q
                }
            }
            val v = s.substring(start, pos).toDouble()
            if (!v.isFinite()) throw Fail(ErrorKind.OVERFLOW, "number literal is outside the Double range")
            return v
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

        /**
         * pi is not representable, so no Double argument is exactly an odd multiple of pi/2. An argument within
         * [RAD_POLE_ULPS] ulp of one is the Double a user gets by typing that pole (π/2, 3π/2, ...), and is treated
         * as the pole. Anything farther away, such as 1.57 or 1.5707963267, is a valid angle.
         */
        fun isRadPole(x: Double): Boolean {
            val k = Math.rint(x / PI - 0.5)
            val pole = (k + 0.5) * PI
            return abs(x - pole) <= RAD_POLE_ULPS * Math.ulp(max(abs(x), abs(pole)))
        }

        fun apply(name: String, x: Double): Double {
            fun dom(msg: String): Nothing = throw Fail(ErrorKind.DOMAIN, "$name: $msg")
            return when (name) {
                "sin" -> sinA(x)
                "cos" -> cosA(x)
                "tan" -> {
                    val c = cosA(x)
                    if (angle != AngleMode.RAD && c == 0.0) dom("undefined at odd multiples of 90° (DEG) / 100 grad (GRAD)")
                    if (angle == AngleMode.RAD && isRadPole(x)) dom("undefined at odd multiples of π/2")
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

/** Persisted calculator history, bounded so saved state cannot grow without limit. */
object CalculatorHistory {
    /** Implementation limit for stored lines. The oldest lines are dropped, never the newest. */
    const val MAX_ENTRIES = 50
    fun append(history: List<String>, entry: String, max: Int = MAX_ENTRIES): List<String> {
        require(max >= 1) { "max must be >= 1" }
        return (history + entry).takeLast(max)
    }
}
