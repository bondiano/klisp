import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlin.math.pow

typealias EvalResult = Either<KlispError, Value>
typealias TrampolineResult = Either<KlispError, Trampoline<Value>>

fun eval(value: Value, env: Environment = Environment()): EvalResult = either {
    evalTrampoline(value, env).bind().run()
}

private fun evalTrampoline(value: Value, env: Environment): TrampolineResult = either {
    val expanded = expand(value, env).bind()
    evalExpandedTrampoline(expanded, env).bind()
}

/**
 * Evaluating in NON-tail positions
 * Immediately runs the trampoline to get the result
 * Use this when the result is needed immediately (e.g., function arguments, conditions)
 * For TAIL positions, use evalExpandedTrampoline directly
 */
private fun evalExpanded(value: Value, env: Environment): EvalResult = either {
    evalExpandedTrampoline(value, env).bind().run()
}

/**
 * Internal evaluation of expanded form returning Trampoline
 * This is where tail call optimization happens
 */
private fun evalExpandedTrampoline(value: Value, env: Environment): TrampolineResult = either {
    when (value) {
        is Value.Integer, is Value.Float, is Value.Str, is Value.Bool, Value.Nil ->
            done(value)

        is Value.Builtin -> done(value)
        is Value.Lambda -> done(value)
        is Value.Macro -> done(value)

        is Value.Symbol -> {
            val resolved = env.get(value.name)
                ?: raise(KlispError.EvalError("Undefined symbol: ${value.name}"))
            done(resolved)
        }

        is Value.Cons -> {
            if (value.head == Value.Nil && value.tail == Value.Nil) {
                done(Value.Nil)
            } else {
                val function = evalExpandedTrampoline(value.head, env).bind().run()
                val args = consToList(value.tail)

                when (function) {
                    is Value.Builtin -> applyBuiltinTrampoline(function.specialForm, args, env).bind()
                    is Value.Lambda -> applyLambdaTrampoline(function, args, env).bind()
                    else -> raise(KlispError.EvalError("Cannot apply non-function: ${function.toPrintingString()}"))
                }
            }
        }
    }
}

/**
 * Apply builtin special form with trampoline support
 * Forms with tail positions (IF, DO) use special trampoline versions
 */
private fun applyBuiltinTrampoline(
    form: SpecialForm,
    args: List<Value>,
    env: Environment
): TrampolineResult = either {
    when (form) {
        SpecialForm.ADD -> done(evalArithmetic(args, env, 0L, 0.0) { a, b -> a + b }.bind())
        SpecialForm.SUB -> done(evalSub(args, env).bind())
        SpecialForm.MUL -> done(evalArithmetic(args, env, 1L, 1.0) { a, b -> a * b }.bind())
        SpecialForm.DIV -> done(evalDiv(args, env).bind())
        SpecialForm.MOD -> done(evalMod(args, env).bind())
        SpecialForm.POW -> done(evalPow(args, env).bind())

        SpecialForm.EQ -> done(evalEq(args, env).bind())
        SpecialForm.GT -> done(evalComparison(args, env) { a, b -> a > b }.bind())
        SpecialForm.LT -> done(evalComparison(args, env) { a, b -> a < b }.bind())

        SpecialForm.STR_CONCAT -> done(evalStrConcat(args, env).bind())

        SpecialForm.QUOTE -> {
            ensure(args.size == 1) { KlispError.EvalError("quote expects 1 argument, got ${args.size}") }
            done(args[0])
        }

        SpecialForm.IF -> evalIfTrampoline(args, env).bind()
        SpecialForm.DO -> evalDoTrampoline(args, env).bind()

        SpecialForm.DEFINE -> done(evalDefine(args, env).bind())
        SpecialForm.SET -> done(evalSet(args, env).bind())
        SpecialForm.LAMBDA -> done(evalLambda(args, env).bind())
        SpecialForm.MACRO -> done(evalMacro(args).bind())
        SpecialForm.EXPAND_MACRO -> done(evalExpandMacro(args, env).bind())
        SpecialForm.EVAL -> done(evalEvalForm(args, env).bind())
        SpecialForm.RAISE -> done(evalRaise(args, env).bind())

        SpecialForm.CAR -> done(evalCar(args, env).bind())
        SpecialForm.CDR -> done(evalCdr(args, env).bind())
        SpecialForm.CONS -> done(evalConsForm(args, env).bind())

        SpecialForm.TYPE_OF -> done(evalTypeOf(args, env).bind())
        SpecialForm.SYMBOL -> done(evalSymbolForm(args, env).bind())

        SpecialForm.PRINT -> done(evalPrint(args, env).bind())
        SpecialForm.READ -> done(evalRead(env).bind())

        else -> raise(KlispError.EvalError("Special form not yet implemented: ${form.toPrintingString()}"))
    }
}

private fun evalEvalForm(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("eval expects exactly 1 argument, got ${args.size}") }

    val code = evalExpanded(args[0], env).bind()
    eval(code, env).bind()
}

private fun evalRaise(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("raise expects exactly 1 argument, got ${args.size}") }

    val errorValue = evalExpanded(args[0], env).bind()
    raise(KlispError.RuntimeError(errorValue.toPrintingString()))
}

/**
 * Eval IF with tail position optimization
 * Then and else branches are in tail position
 */
private fun evalIfTrampoline(args: List<Value>, env: Environment): TrampolineResult = either {
    ensure(args.size in 2..3) {
        KlispError.EvalError("if expects 2 or 3 arguments, got ${args.size}")
    }

    // Condition is NOT in tail position - evaluate immediately
    val isTruthy = when (val condition = evalExpanded(args[0], env).bind()) {
        is Value.Bool -> condition.value
        Value.Nil -> false
        else -> true
    }

    // Then/else branches ARE in the tail position
    if (isTruthy) {
        evalExpandedTrampoline(args[1], env).bind()
    } else if (args.size == 3) {
        evalExpandedTrampoline(args[2], env).bind()
    } else {
        done(Value.Nil)
    }
}

private fun evalLambda(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("lambda expects exactly 2 arguments, got ${args.size}") }

    // Parse parameter list: (x y . rest) or (x y) or (. rest)
    val (params, variadicParam) = when (val paramList = args[0]) {
        is Value.Cons -> parseParameters(paramList).bind()
        Value.Nil -> emptyList<Value.Symbol>() to null
        else -> raise(KlispError.EvalError("lambda expects parameter list, got ${paramList.toPrintingString()}"))
    }

    val body = args[1]
    Value.Lambda(params, variadicParam, body, env)
}

private fun parseParameters(paramList: Value.Cons): Either<KlispError.EvalError, Pair<List<Value.Symbol>, Value.Symbol?>> =
    either {
        val params = mutableListOf<Value.Symbol>()
        var current: Value = paramList

        while (current is Value.Cons) {
            when (val head = current.head) {
                is Value.Symbol -> {
                    if (head.name == ".") {
                        val tail = current.tail
                        ensure(tail is Value.Cons) { KlispError.EvalError("lambda expects symbol after '.'") }

                        val varName = tail.head
                        ensure(varName is Value.Symbol) {
                            KlispError.EvalError("variadic parameter must be symbol, got ${varName.toPrintingString()}")
                        }
                        ensure(varName.name != ".") {
                            KlispError.EvalError("variadic parameter name cannot be '.'")
                        }
                        ensure(tail.tail == Value.Nil) {
                            KlispError.EvalError("variadic parameter must be last in parameter list")
                        }

                        return@either params to varName
                    } else {
                        params.add(head)
                    }
                }

                else -> raise(KlispError.EvalError("lambda parameter must be symbol, got ${head.toPrintingString()}"))
            }
            current = current.tail
        }

        ensure(current == Value.Nil) { KlispError.EvalError("improper parameter list") }
        params to null
    }

private fun applyLambdaTrampoline(
    lambda: Value.Lambda,
    args: List<Value>,
    env: Environment
): TrampolineResult = either {
    val minArgs = lambda.parameters.size
    val hasVariadic = lambda.variadicParam != null

    // Validate argument count
    if (hasVariadic) {
        ensure(args.size >= minArgs) {
            KlispError.EvalError("lambda expects at least $minArgs arguments, got ${args.size}")
        }
    } else {
        ensure(args.size == minArgs) {
            KlispError.EvalError("lambda expects $minArgs arguments, got ${args.size}")
        }
    }

    val lambdaEnv = lambda.capturedEnv.createChild()

    lambda.parameters.take(minArgs).zip(args.take(minArgs)).forEach { (param, arg) ->
        val evaluatedArg = evalExpanded(arg, env).bind()
        lambdaEnv.define(param.name, evaluatedArg)
    }

    lambda.variadicParam?.let { varParam ->
        val restArgs = args.drop(minArgs)
        val restList = restArgs.foldRight(Value.Nil as Value) { arg, acc ->
            val evaluatedArg = evalExpanded(arg, env).bind()
            Value.Cons(evaluatedArg, acc)
        }
        lambdaEnv.define(varParam.name, restList)
    }

    // KEY: Return More to defer evaluation - this enables tail call optimization!
    // The Raise context is captured safely - the lambda executes later when run() is called,
    // but by then the either block has already returned the Either value with the More inside.
    // When the thunk executes, it creates its own either context via evalTrampoline.
    @Suppress("EscapedRaise")
    more { evalTrampoline(lambda.body, lambdaEnv).bind() }
}

private fun evalArithmetic(
    args: List<Value>,
    env: Environment,
    intIdentity: Long,
    floatIdentity: Double,
    op: (Double, Double) -> Double
): EvalResult = either {
    if (args.isEmpty()) return@either Value.Integer(intIdentity)

    val evaluated = args.map { evalExpanded(it, env).bind() }
    val hasFloat = evaluated.any { it is Value.Float }

    if (hasFloat) {
        val result = evaluated.fold(floatIdentity) { acc, value ->
            op(acc, toDoubleOrRaise(value).bind())
        }
        Value.Float(result)
    } else {
        val result = evaluated.fold(intIdentity) { acc, value ->
            op(acc.toDouble(), toDoubleOrRaise(value).bind()).toLong()
        }
        Value.Integer(result)
    }
}

private fun evalSub(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.isNotEmpty()) { KlispError.EvalError("- expects at least 1 argument") }

    val evaluated = args.map { evalExpanded(it, env).bind() }
    val hasFloat = evaluated.any { it is Value.Float }

    if (evaluated.size == 1) {
        val num = toDoubleOrRaise(evaluated[0]).bind()
        if (hasFloat) Value.Float(-num) else Value.Integer(-num.toLong())
    } else {
        val first = toDoubleOrRaise(evaluated[0]).bind()
        val result = evaluated.drop(1).fold(first) { acc, value ->
            acc - toDoubleOrRaise(value).bind()
        }
        if (hasFloat) Value.Float(result) else Value.Integer(result.toLong())
    }
}

private fun evalDiv(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.isNotEmpty()) { KlispError.EvalError("/ expects at least 1 argument") }

    val evaluated = args.map { evalExpanded(it, env).bind() }

    if (evaluated.size == 1) {
        val num = toDoubleOrRaise(evaluated[0]).bind()
        ensure(num != 0.0) { KlispError.EvalError("Division by zero") }
        Value.Float(1.0 / num)
    } else {
        val first = toDoubleOrRaise(evaluated[0]).bind()
        val result = evaluated.drop(1).fold(first) { acc, value ->
            val divisor = toDoubleOrRaise(value).bind()
            ensure(divisor != 0.0) { KlispError.EvalError("Division by zero") }
            acc / divisor
        }
        Value.Float(result)
    }
}

private fun evalMod(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("% expects exactly 2 arguments, got ${args.size}") }

    val evaluated = args.map { evalExpanded(it, env).bind() }
    val a = toLongOrRaise(evaluated[0]).bind()
    val b = toLongOrRaise(evaluated[1]).bind()

    ensure(b != 0L) { KlispError.EvalError("Modulo by zero") }
    Value.Integer(a % b)
}

private fun evalPow(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("^ expects exactly 2 arguments, got ${args.size}") }

    val evaluated = args.map { evalExpanded(it, env).bind() }
    val base = toDoubleOrRaise(evaluated[0]).bind()
    val exponent = toDoubleOrRaise(evaluated[1]).bind()

    Value.Float(base.pow(exponent))
}

private fun evalEq(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size >= 2) { KlispError.EvalError("= expects at least 2 arguments, got ${args.size}") }

    val evaluated = args.map { evalExpanded(it, env).bind() }
    val first = evaluated[0]

    val allEqual = evaluated.drop(1).all { compareValues(first, it) }
    Value.Bool(allEqual)
}

private fun evalComparison(args: List<Value>, env: Environment, cmp: (Double, Double) -> Boolean): EvalResult = either {
    ensure(args.size >= 2) { KlispError.EvalError("Comparison expects at least 2 arguments, got ${args.size}") }

    val evaluated = args.map { evalExpanded(it, env).bind() }

    for (i in 0 until evaluated.size - 1) {
        val a = toDoubleOrRaise(evaluated[i]).bind()
        val b = toDoubleOrRaise(evaluated[i + 1]).bind()
        if (!cmp(a, b)) {
            return@either Value.Bool(false)
        }
    }
    Value.Bool(true)
}

private fun evalStrConcat(args: List<Value>, env: Environment): EvalResult = either {
    val evaluated = args.map { evalExpanded(it, env).bind() }
    val result = evaluated.joinToString("") { value ->
        when (value) {
            is Value.Str -> value.text
            is Value.Integer -> value.value.toString()
            is Value.Float -> value.value.toString()
            is Value.Bool -> value.value.toString()
            else -> value.toPrintingString()
        }
    }
    Value.Str(result)
}

private fun evalMacro(args: List<Value>): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("macro expects exactly 2 arguments, got ${args.size}") }

    val (params, variadicParam) = when (val paramList = args[0]) {
        is Value.Cons -> parseParameters(paramList).bind()
        Value.Nil -> emptyList<Value.Symbol>() to null
        else -> raise(KlispError.EvalError("macro expects parameter list, got ${paramList.toPrintingString()}"))
    }

    val body = args[1]

    Value.Macro(params, variadicParam, body)
}

private fun evalExpandMacro(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("expand-macro expects exactly 1 argument, got ${args.size}") }

    expand(args[0], env).bind()
}

private fun evalDefine(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("define expects exactly 2 arguments, got ${args.size}") }

    val name = when (val nameValue = args[0]) {
        is Value.Symbol -> nameValue.name
        else -> raise(KlispError.EvalError("define expects symbol as first argument, got ${nameValue.toPrintingString()}"))
    }

    val value = evalExpanded(args[1], env).bind()
    env.define(name, value)
    value
}

private fun evalSet(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("set! expects exactly 2 arguments, got ${args.size}") }

    val name = when (val nameValue = args[0]) {
        is Value.Symbol -> nameValue.name
        else -> raise(KlispError.EvalError("set! expects symbol as first argument, got ${nameValue.toPrintingString()}"))
    }

    val value = evalExpanded(args[1], env).bind()
    env.set(name, value).bind()
    value
}

private fun evalCar(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("car expects exactly 1 argument, got ${args.size}") }

    when (val list = evalExpanded(args[0], env).bind()) {
        is Value.Cons -> list.head
        Value.Nil -> raise(KlispError.EvalError("car of empty list"))
        else -> raise(KlispError.EvalError("car expects list, got ${list.toPrintingString()}"))
    }
}

private fun evalCdr(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("cdr expects exactly 1 argument, got ${args.size}") }

    when (val list = evalExpanded(args[0], env).bind()) {
        is Value.Cons -> list.tail
        Value.Nil -> raise(KlispError.EvalError("cdr of empty list"))
        else -> raise(KlispError.EvalError("cdr expects list, got ${list.toPrintingString()}"))
    }
}

private fun evalConsForm(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 2) { KlispError.EvalError("cons expects exactly 2 arguments, got ${args.size}") }

    val head = evalExpanded(args[0], env).bind()
    val tail = evalExpanded(args[1], env).bind()

    Value.Cons(head, tail)
}

private fun evalDoTrampoline(args: List<Value>, env: Environment): TrampolineResult = either {
    ensure(args.isNotEmpty()) { KlispError.EvalError("do expects at least 1 argument") }

    // All expressions except the last are NOT in the tail position - evaluate immediately
    args.dropLast(1).forEach { expr ->
        evalExpanded(expr, env).bind()
    }

    evalExpandedTrampoline(args.last(), env).bind()
}

private fun evalTypeOf(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("type-of expects exactly 1 argument, got ${args.size}") }

    val value = evalExpanded(args[0], env).bind()
    val typeName = when (value) {
        is Value.Integer -> "integer"
        is Value.Float -> "float"
        is Value.Str -> "string"
        is Value.Bool -> "boolean"
        is Value.Symbol -> "symbol"
        is Value.Lambda -> "lambda"
        is Value.Macro -> "macro"
        is Value.Builtin -> "builtin"
        is Value.Cons -> "list"
        Value.Nil -> "nil"
    }
    Value.Str(typeName)
}

private fun evalSymbolForm(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("symbol expects exactly 1 argument, got ${args.size}") }

    when (val value = evalExpanded(args[0], env).bind()) {
        is Value.Str -> Value.Symbol(value.text)
        else -> raise(KlispError.EvalError("symbol expects string, got ${value.toPrintingString()}"))
    }
}

private fun evalPrint(args: List<Value>, env: Environment): EvalResult = either {
    ensure(args.size == 1) { KlispError.EvalError("print expects exactly 1 argument, got ${args.size}") }

    val value = evalExpanded(args[0], env).bind()
    val io = env.getIoAdapter()
    io.println(value.toPrintingString()).bind()
    value
}

private fun evalRead(env: Environment): EvalResult = either {
    val io = env.getIoAdapter()
    val line = io.readLine().bind()

    val (parsedValue, _) = parse(line).bind()
    parsedValue
}

private fun consToList(value: Value): List<Value> {
    val result = mutableListOf<Value>()
    var current = value

    while (current is Value.Cons) {
        result.add(current.head)
        current = current.tail
    }

    require(current == Value.Nil) { "Improper list in function application" }
    return result
}

private fun toDoubleOrRaise(value: Value): Either<KlispError.EvalError, Double> = either {
    when (value) {
        is Value.Integer -> value.value.toDouble()
        is Value.Float -> value.value
        else -> raise(KlispError.EvalError("Expected number, got ${value.toPrintingString()}"))
    }
}

private fun toLongOrRaise(value: Value): Either<KlispError.EvalError, Long> = either {
    when (value) {
        is Value.Integer -> value.value
        else -> raise(KlispError.EvalError("Expected integer, got ${value.toPrintingString()}"))
    }
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
