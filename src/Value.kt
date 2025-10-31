enum class SpecialForm {
    ADD, SUB, MUL, DIV, MOD, EQ, GT, LT, POW, STR_CONCAT,
    IF, SET, PRINT, READ, LAMBDA, DO, LOAD, RAISE,
    MACRO, EXPAND_MACRO, QUOTE, DEFINE, SYMBOL,
    CAR, CDR, CONS, TYPE_OF, EVAL;

    companion object {
        fun fromString(value: String): SpecialForm? = when (value) {
            "+" -> ADD
            "-" -> SUB
            "*" -> MUL
            "/" -> DIV
            "%" -> MOD
            "=" -> EQ
            ">" -> GT
            "<" -> LT
            "^" -> POW
            "++" -> STR_CONCAT
            "if" -> IF
            "set!" -> SET
            "print" -> PRINT
            "read" -> READ
            "lambda" -> LAMBDA
            "do" -> DO
            "load" -> LOAD
            "raise" -> RAISE
            "macro" -> MACRO
            "expand-macro" -> EXPAND_MACRO
            "quote" -> QUOTE
            "def" -> DEFINE
            "symbol" -> SYMBOL
            "car" -> CAR
            "cdr" -> CDR
            "cons" -> CONS
            "type-of" -> TYPE_OF
            "eval" -> EVAL
            else -> null
        }
    }
}

fun SpecialForm.toPrintingString(): String = when (this) {
    SpecialForm.ADD -> "+"
    SpecialForm.SUB -> "-"
    SpecialForm.MUL -> "*"
    SpecialForm.DIV -> "/"
    SpecialForm.MOD -> "%"
    SpecialForm.EQ -> "="
    SpecialForm.GT -> ">"
    SpecialForm.LT -> "<"
    SpecialForm.POW -> "^"
    SpecialForm.STR_CONCAT -> "++"
    SpecialForm.IF -> "if"
    SpecialForm.SET -> "set!"
    SpecialForm.PRINT -> "print"
    SpecialForm.READ -> "read"
    SpecialForm.LAMBDA -> "lambda"
    SpecialForm.DO -> "do"
    SpecialForm.LOAD -> "load"
    SpecialForm.RAISE -> "raise"
    SpecialForm.MACRO -> "macro"
    SpecialForm.EXPAND_MACRO -> "expand-macro"
    SpecialForm.QUOTE -> "quote"
    SpecialForm.DEFINE -> "def"
    SpecialForm.SYMBOL -> "symbol"
    SpecialForm.CAR -> "car"
    SpecialForm.CDR -> "cdr"
    SpecialForm.CONS -> "cons"
    SpecialForm.TYPE_OF -> "type-of"
    SpecialForm.EVAL -> "eval"
}

sealed class Value {
    data class Float(val value: Double) : Value()
    data class Integer(val value: Long) : Value()
    data class Str(val text: String) : Value()
    data class Bool(val value: Boolean) : Value()
    data class Symbol(val name: String) : Value()
    data class Lambda(val parameters: List<Symbol>, val body: List<Value>) : Value()
    data class Builtin(val specialForm: SpecialForm) : Value()
    data class Cons(val head: Value, val tail: Value) : Value()
    object Nil : Value()

    companion object {
        fun fromString(value: String): Value = when {
            value == "nil" -> Nil
            value.toLongOrNull() != null -> Integer(value.toLong())
            value.toDoubleOrNull() != null -> Float(value.toDouble())
            value.equals("true", ignoreCase = true) -> Bool(true)
            value.equals("false", ignoreCase = true) -> Bool(false)
            SpecialForm.fromString(value) != null -> Builtin(SpecialForm.fromString(value)!!)
            else -> Symbol(value)
        }
    }
}

/**
 *  Returns a string representation of the object for print a form.
 */
fun Value.toPrintingString(): String = when (this) {
    is Value.Float -> value.toString()
    is Value.Integer -> value.toString()
    is Value.Str -> text
    is Value.Bool -> value.toString()
    is Value.Symbol -> name
    is Value.Builtin -> specialForm.toPrintingString()
    is Value.Lambda -> "(lambda ${parameters.joinToString(" ") { it.toPrintingString() }})"
    is Value.Cons -> "(${toPrintingStringRecursive()})"
    is Value.Nil -> "nil"
}

private fun Value.Cons.toPrintingStringRecursive(): String = when (tail) {
    Value.Nil -> head.toPrintingString()
    is Value.Cons -> "${head.toPrintingString()} ${tail.toPrintingStringRecursive()}"
    else -> "${head.toPrintingString()} ${tail.toPrintingString()}"
}

/**
 *  Returns a string representation of the object for showing in the REPL.
 */
fun Value.show(): String = when (this) {
    is Value.Str -> "\"$text\""
    else -> toPrintingString()
}

