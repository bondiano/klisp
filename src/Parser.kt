package com.bondiano.klisp

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

typealias ParseResult = Either<KlispError.ParseError, Pair<Value, String>>

fun parse(input: String): ParseResult = either {
    val cleanInput = skipWhiteSpacesAndComments(input)
    ensure(cleanInput.isNotEmpty()) { KlispError.ParseError("Unexpected end of input") }

    when {
        cleanInput.startsWith("\"") -> parseString(cleanInput).bind()
        cleanInput.startsWith("(") -> parseList(cleanInput.drop(1)).bind()
        cleanInput.startsWith("'") -> {
            val (quotedValue, rest) = parse(cleanInput.drop(1)).bind()
            Value.Cons(Value.Builtin(SpecialForm.QUOTE), Value.Cons(quotedValue, Value.Nil)) to rest
        }

        else -> parseAtom(cleanInput).bind()
    }
}

private fun parseString(input: String): ParseResult = either {
    ensure(input.startsWith("\"")) { KlispError.ParseError("Expected string to start with \"") }

    val content = StringBuilder()
    var i = 1
    var escaped = false

    while (i < input.length) {
        val char = input[i]

        if (escaped) {
            when (char) {
                'n' -> content.append('\n')
                't' -> content.append('\t')
                'r' -> content.append('\r')
                '\\' -> content.append('\\')
                '"' -> content.append('"')
                else -> {
                    content.append('\\')
                    content.append(char)
                }
            }
            escaped = false
        } else {
            when (char) {
                '\\' -> escaped = true
                '"' -> return@either Value.Str(content.toString()) to input.drop(i + 1)
                else -> content.append(char)
            }
        }
        i++
    }

    raise(KlispError.ParseError("Unterminated string literal"))
}

private fun parseAtom(input: String): ParseResult = either {
    val atomString = takeUntilDelimiter(input)
    val rest = input.drop(atomString.length)

    ensure(atomString.isNotEmpty()) {
        KlispError.ParseError("Unexpected end of atom for input: $input")
    }

    Value.fromString(atomString) to rest
}

private fun parseList(input: String): ParseResult = either {
    var rest = skipWhiteSpacesAndComments(input)
    val items = mutableListOf<Value>()

    while (rest.isNotEmpty() && rest.first() != ')') {
        val (item, itemRest) = parse(rest).bind()
        items.add(item)
        rest = skipWhiteSpacesAndComments(itemRest)
    }

    ensure(rest.isNotEmpty()) { KlispError.ParseError("Unexpected end of input, expected ')'") }

    // Transform (.method ...) to (. method ...)
    // Transform (.-field ...) to (.- field ...)
    // Transform (. method ...) where "." is Symbol to Builtin
    // BUT: Don't transform (. rest) - it's variadic lambda params
    val result = if (items.isNotEmpty() && items[0] is Value.Symbol) {
        val symbol = items[0] as Value.Symbol
        when {
            symbol.name == "." && items.size > 2 -> {
                // (. method obj args...) -> Replace Symbol(".") with Builtin(DOT)
                // Only if there are at least 2 more elements (method and object)
                val transformed = listOf(Value.Builtin(SpecialForm.DOT)) + items.drop(1)
                transformed.foldRight(Value.Nil as Value) { item, acc -> Value.Cons(item, acc) }
            }

            symbol.name == ".-" && items.size > 2 -> {
                // (.- field obj) -> Replace Symbol(".-") with Builtin(DOT_FIELD)
                val transformed = listOf(Value.Builtin(SpecialForm.DOT_FIELD)) + items.drop(1)
                transformed.foldRight(Value.Nil as Value) { item, acc -> Value.Cons(item, acc) }
            }

            symbol.name.startsWith(".-") && symbol.name.length > 2 -> {
                // (.-field obj) -> (.- field obj)
                val fieldName = symbol.name.drop(2)
                val transformed = listOf(Value.Builtin(SpecialForm.DOT_FIELD), Value.Symbol(fieldName)) + items.drop(1)
                transformed.foldRight(Value.Nil as Value) { item, acc -> Value.Cons(item, acc) }
            }

            symbol.name.startsWith(".") && symbol.name.length > 1 && symbol.name != ".-" -> {
                // (.method obj args...) -> (. method obj args...)
                val methodName = symbol.name.drop(1)
                val transformed = listOf(Value.Builtin(SpecialForm.DOT), Value.Symbol(methodName)) + items.drop(1)
                transformed.foldRight(Value.Nil as Value) { item, acc -> Value.Cons(item, acc) }
            }

            else -> items.foldRight(Value.Nil as Value) { item, acc -> Value.Cons(item, acc) }
        }
    } else {
        items.foldRight(Value.Nil as Value) { item, acc -> Value.Cons(item, acc) }
    }

    result to rest.drop(1)
}

private fun skipWhiteSpacesAndComments(input: String): String {
    var rest = input
    while (rest.isNotEmpty()) {
        when {
            rest.first().isWhitespace() -> rest = rest.drop(1)
            rest.first() == ';' -> {
                rest = rest.dropWhile { it != '\n' }
                if (rest.isNotEmpty()) rest = rest.drop(1)
            }

            else -> break
        }
    }
    return rest
}

private val delimiters = setOf('(', ')', ' ', '\n', '\t', ';', '\'', '`', ',')

private fun takeUntilDelimiter(input: String): String = input.takeWhile { it !in delimiters }
