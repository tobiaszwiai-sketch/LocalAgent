package com.llamaagent.agent.tools

import kotlin.math.pow

/**
 * Kalkulator wyrażeń arytmetycznych.
 *
 * Uwaga: javax.script (ScriptEngine) nie jest dostępny na Androidzie, dlatego
 * używamy własnego parsera rekurencyjnego (recursive-descent).
 * Obsługuje: + - * / %, potęgowanie ^, nawiasy, liczby zmiennoprzecinkowe,
 * jednoargumentowy minus oraz funkcje sqrt, sin, cos, tan, log, ln, abs.
 */
class CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description = "calculator(expression: string) - Oblicza wyrażenia matematyczne, np. \"2*(3+4)^2\""

    override suspend fun execute(params: Map<String, Any?>): String {
        val expr = (params["expression"] ?: params["expr"])?.toString()?.trim().orEmpty()
        if (expr.isEmpty()) return "Błąd: brak parametru 'expression'."
        return try {
            val result = Parser(expr).parse()
            if (result == result.toLong().toDouble()) {
                "${expr} = ${result.toLong()}"
            } else {
                "${expr} = ${result}"
            }
        } catch (e: Exception) {
            "Błąd obliczeń: ${e.message}"
        }
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = parseExpression()
            skipSpaces()
            if (pos < s.length) throw IllegalArgumentException("Nieoczekiwany znak '${s[pos]}'")
            return v
        }

        private fun skipSpaces() { while (pos < s.length && s[pos] == ' ') pos++ }

        private fun peek(): Char? { skipSpaces(); return if (pos < s.length) s[pos] else null }

        // dodawanie / odejmowanie
        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                val c = peek()
                when (c) {
                    '+' -> { pos++; value += parseTerm() }
                    '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        // mnożenie / dzielenie / modulo
        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                val c = peek()
                when (c) {
                    '*' -> { pos++; value *= parsePower() }
                    '/' -> { pos++; value /= parsePower() }
                    '%' -> { pos++; value %= parsePower() }
                    else -> return value
                }
            }
        }

        // potęgowanie (prawostronnie łączne)
        private fun parsePower(): Double {
            val base = parseUnary()
            val c = peek()
            if (c == '^') { pos++; return base.pow(parsePower()) }
            return base
        }

        private fun parseUnary(): Double {
            val c = peek()
            if (c == '-') { pos++; return -parseUnary() }
            if (c == '+') { pos++; return parseUnary() }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            skipSpaces()
            val c = peek() ?: throw IllegalArgumentException("Niespodziewany koniec wyrażenia")

            if (c == '(') {
                pos++
                val v = parseExpression()
                if (peek() != ')') throw IllegalArgumentException("Brakuje ')'")
                pos++
                return v
            }

            if (c.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val fn = s.substring(start, pos)
                if (peek() != '(') throw IllegalArgumentException("Nieznana funkcja '$fn'")
                pos++
                val arg = parseExpression()
                if (peek() != ')') throw IllegalArgumentException("Brakuje ')'")
                pos++
                return applyFunc(fn, arg)
            }

            // liczba
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Oczekiwano liczby")
            return s.substring(start, pos).toDouble()
        }

        private fun applyFunc(fn: String, x: Double): Double = when (fn.lowercase()) {
            "sqrt" -> kotlin.math.sqrt(x)
            "sin" -> kotlin.math.sin(x)
            "cos" -> kotlin.math.cos(x)
            "tan" -> kotlin.math.tan(x)
            "log" -> kotlin.math.log10(x)
            "ln" -> kotlin.math.ln(x)
            "abs" -> kotlin.math.abs(x)
            else -> throw IllegalArgumentException("Nieznana funkcja '$fn'")
        }
    }
}
