package dev.scrollwerk.launcher.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * The launcher's calculator provider: arithmetic typed into the search field
 * (`+ - * / % ^`, parentheses, `x` or `×` for multiply, `,` or `.` as decimal point).
 * Returns null for anything that isn't clearly a calculation, so app search stays primary.
 */
object Calculator {

    private val hasBinaryOperator = Regex("""[\d)]\s*[-+*/^%]""")

    fun evaluate(input: String): String? {
        val expr = input.trim().removePrefix("=")
            .replace('×', '*').replace('x', '*').replace('X', '*')
            .replace('÷', '/').replace(',', '.')
        if (expr.none(Char::isDigit)) return null
        if (expr.any { it !in "0123456789.+-*/^%() " }) return null
        if (!hasBinaryOperator.containsMatchIn(expr)) return null
        val value = runCatching { Parser(expr).parseAll() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    internal fun format(v: Double): String =
        if (v == round(v) && abs(v) < 1e15) v.toLong().toString()
        else BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()

    /** Recursive descent: expr = term (+|- term)*, term = power (*|/|% power)*, power = unary (^ power)?. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parseAll(): Double {
            val v = expr()
            skipSpaces()
            require(i == s.length) { "trailing input" }
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                v = when {
                    eat('*') -> v * power()
                    eat('/') -> v / power()
                    eat('%') -> v % power()
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            return if (eat('^')) base.pow(power()) else base
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> primary()
        }

        private fun primary(): Double {
            if (eat('(')) {
                val v = expr()
                require(eat(')')) { "missing )" }
                return v
            }
            skipSpaces()
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start) { "number expected" }
            return s.substring(start, i).toDouble()
        }

        private fun eat(c: Char): Boolean {
            skipSpaces()
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }

        private fun skipSpaces() {
            while (i < s.length && s[i] == ' ') i++
        }
    }
}
