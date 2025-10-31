class ParseError(message: String) : Exception(message)

fun parse(input: String): Pair<Value, String> {
    val cleanInput = skipWhiteSpacesAndComments(input)
    return when {
        cleanInput.isEmpty() -> throw ParseError("Unexpected end of input")
        cleanInput.startsWith("(") -> parseList(cleanInput.drop(1))
        cleanInput.startsWith("'") -> {
            val (quotedValue, rest) = parse(cleanInput.drop(1))
            Value.Cons(Value.Builtin(SpecialForm.QUOTE), Value.Cons(quotedValue, Value.Nil)) to rest
        }

        else -> parseAtom(cleanInput)
    }
}

private fun parseAtom(input: String): Pair<Value, String> {
    val atomString = takeUntilDelimiter(input)
    val rest = input.drop(atomString.length)

    if (atomString.isEmpty()) throw ParseError("Unexpected end of atom for input: $input")

    return Value.fromString(atomString) to rest
}

private fun parseList(input: String): Pair<Value, String> {
    var rest = skipWhiteSpacesAndComments(input)
    val items = mutableListOf<Value>()

    while (rest.isNotEmpty() && rest.first() != ')') {
        val (item, itemRest) = parse(rest)
        items.add(item)
        rest = skipWhiteSpacesAndComments(itemRest)
    }

    if (rest.isEmpty()) throw ParseError("Unexpected end of input, expected ')'")

    return items.foldRight(Value.Nil as Value) { item, acc ->
        Value.Cons(item, acc)
    } to rest.drop(1)
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

private val delimiters = setOf('(', ')', ' ', '\n', '\t', '"', ';', '\'', '`', ',')

private fun takeUntilDelimiter(input: String): String =
    input.takeWhile { it !in delimiters }
