import kotlin.math.pow

class EvalError(message: String) : Exception(message)

fun eval(value: Value): Value = when (value) {
    is Value.Integer, is Value.Float, is Value.Str, is Value.Bool, Value.Nil -> value
    is Value.Builtin -> value
    is Value.Lambda -> value

    is Value.Symbol -> throw EvalError("Undefined symbol: ${value.name}")

    is Value.Cons -> evalList(value)
}

private fun evalList(cons: Value.Cons): Value {
    if (cons.head == Value.Nil && cons.tail == Value.Nil) {
        return Value.Nil
    }

    val function = eval(cons.head)
    val args = consToList(cons.tail)

    return when (function) {
        is Value.Builtin -> applyBuiltin(function.specialForm, args)
        else -> throw EvalError("Cannot apply non-function: ${function.toPrintingString()}")
    }
}

private fun applyBuiltin(form: SpecialForm, args: List<Value>): Value = when (form) {
    // Arithmetic operations
    SpecialForm.ADD -> evalArithmetic(args, 0L, 0.0) { a, b -> a + b }
    SpecialForm.SUB -> evalSub(args)
    SpecialForm.MUL -> evalArithmetic(args, 1L, 1.0) { a, b -> a * b }
    SpecialForm.DIV -> evalDiv(args)
    SpecialForm.MOD -> evalMod(args)
    SpecialForm.POW -> evalPow(args)

    SpecialForm.EQ -> evalEq(args)
    SpecialForm.GT -> evalComparison(args) { a, b -> a > b }
    SpecialForm.LT -> evalComparison(args) { a, b -> a < b }

    SpecialForm.STR_CONCAT -> evalStrConcat(args)

    SpecialForm.QUOTE -> {
        if (args.size != 1) throw EvalError("quote expects 1 argument, got ${args.size}")
        args[0]
    }

    else -> throw EvalError("Special form not yet implemented: ${form.toPrintingString()}")
}

private fun evalArithmetic(
    args: List<Value>,
    intIdentity: Long,
    floatIdentity: Double,
    op: (Double, Double) -> Double
): Value {
    if (args.isEmpty()) {
        return Value.Integer(intIdentity)
    }

    val evaluated = args.map { eval(it) }
    val hasFloat = evaluated.any { it is Value.Float }

    return if (hasFloat) {
        val result = evaluated.fold(floatIdentity) { acc, value ->
            op(acc, toDouble(value))
        }
        Value.Float(result)
    } else {
        val result = evaluated.fold(intIdentity) { acc, value ->
            op(acc.toDouble(), toDouble(value)).toLong()
        }
        Value.Integer(result)
    }
}

private fun evalSub(args: List<Value>): Value {
    if (args.isEmpty()) throw EvalError("- expects at least 1 argument")

    val evaluated = args.map { eval(it) }
    val hasFloat = evaluated.any { it is Value.Float }

    return if (evaluated.size == 1) {
        if (hasFloat) {
            Value.Float(-toDouble(evaluated[0]))
        } else {
            Value.Integer(-toDouble(evaluated[0]).toLong())
        }
    } else {
        val first = toDouble(evaluated[0])
        val result = evaluated.drop(1).fold(first) { acc, value ->
            acc - toDouble(value)
        }
        if (hasFloat) Value.Float(result) else Value.Integer(result.toLong())
    }
}

private fun evalDiv(args: List<Value>): Value {
    if (args.isEmpty()) throw EvalError("/ expects at least 1 argument")

    val evaluated = args.map { eval(it) }

    return if (evaluated.size == 1) {
        val value = toDouble(evaluated[0])
        if (value == 0.0) throw EvalError("Division by zero")
        Value.Float(1.0 / value)
    } else {
        val first = toDouble(evaluated[0])
        val result = evaluated.drop(1).fold(first) { acc, value ->
            val divisor = toDouble(value)
            if (divisor == 0.0) throw EvalError("Division by zero")
            acc / divisor
        }
        Value.Float(result)
    }
}

private fun evalMod(args: List<Value>): Value {
    if (args.size != 2) throw EvalError("% expects exactly 2 arguments, got ${args.size}")

    val evaluated = args.map { eval(it) }
    val a = toLong(evaluated[0])
    val b = toLong(evaluated[1])

    if (b == 0L) throw EvalError("Modulo by zero")
    return Value.Integer(a % b)
}

private fun evalPow(args: List<Value>): Value {
    if (args.size != 2) throw EvalError("^ expects exactly 2 arguments, got ${args.size}")

    val evaluated = args.map { eval(it) }
    val base = toDouble(evaluated[0])
    val exponent = toDouble(evaluated[1])

    return Value.Float(base.pow(exponent))
}

private fun evalEq(args: List<Value>): Value {
    if (args.size < 2) throw EvalError("= expects at least 2 arguments, got ${args.size}")

    val evaluated = args.map { eval(it) }
    val first = evaluated[0]

    val allEqual = evaluated.drop(1).all { compareValues(first, it) }
    return Value.Bool(allEqual)
}

private fun evalComparison(args: List<Value>, cmp: (Double, Double) -> Boolean): Value {
    if (args.size < 2) throw EvalError("Comparison expects at least 2 arguments, got ${args.size}")

    val evaluated = args.map { eval(it) }

    for (i in 0 until evaluated.size - 1) {
        val a = toDouble(evaluated[i])
        val b = toDouble(evaluated[i + 1])
        if (!cmp(a, b)) {
            return Value.Bool(false)
        }
    }
    return Value.Bool(true)
}

private fun evalStrConcat(args: List<Value>): Value {
    val evaluated = args.map { eval(it) }
    val result = evaluated.joinToString("") { value ->
        when (value) {
            is Value.Str -> value.text
            is Value.Integer -> value.value.toString()
            is Value.Float -> value.value.toString()
            is Value.Bool -> value.value.toString()
            else -> value.toPrintingString()
        }
    }
    return Value.Str(result)
}

private fun consToList(value: Value): List<Value> {
    val result = mutableListOf<Value>()
    var current = value

    while (current is Value.Cons) {
        result.add(current.head)
        current = current.tail
    }

    if (current != Value.Nil) {
        throw EvalError("Improper list in function application")
    }

    return result
}

private fun toDouble(value: Value): Double = when (value) {
    is Value.Integer -> value.value.toDouble()
    is Value.Float -> value.value
    else -> throw EvalError("Expected number, got ${value.toPrintingString()}")
}

private fun toLong(value: Value): Long = when (value) {
    is Value.Integer -> value.value
    else -> throw EvalError("Expected integer, got ${value.toPrintingString()}")
}

private fun compareValues(a: Value, b: Value): Boolean = when (a) {
    is Value.Integer -> when (b) {
        is Value.Integer -> a.value == b.value
        is Value.Float -> a.value.toDouble() == b.value
        else -> false
    }
    is Value.Float -> when (b) {
        is Value.Integer -> a.value == b.value.toDouble()
        is Value.Float -> a.value == b.value
        else -> false
    }
    is Value.Str -> b is Value.Str && a.text == b.text
    is Value.Bool -> b is Value.Bool && a.value == b.value
    Value.Nil -> b == Value.Nil
    else -> false
}
