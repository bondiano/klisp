package com.bondiano.klisp

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

typealias ExpandResult = Either<KlispError, Value>

fun expand(value: Value, env: Environment): ExpandResult = either {
    when (value) {
        is Value.Cons -> {
            if (value.head == Value.Nil && value.tail == Value.Nil) {
                return@either Value.Nil
            }

            if (value.head is Value.Symbol) {
                val maybeMacro = env.get(value.head.name)
                if (maybeMacro is Value.Macro) {
                    val args = value.tail.toList()
                    val expanded = expandMacro(maybeMacro, args).bind()
                    return@either expand(expanded, env).bind()
                }
            }

            val expandedHead = expand(value.head, env).bind()
            val expandedTail = expandList(value.tail, env).bind()
            cons(expandedHead, expandedTail)
        }

        else -> value
    }
}

private fun expandList(list: Value, env: Environment): ExpandResult = either {
    when (list) {
        Value.Nil -> Value.Nil
        is Value.Cons -> {
            val expandedHead = expand(list.head, env).bind()
            val expandedTail = expandList(list.tail, env).bind()
            cons(expandedHead, expandedTail)
        }

        else -> list
    }
}

private fun expandMacro(macro: Value.Macro, args: List<Value>): ExpandResult = either {
    val minArgs = macro.parameters.size

    if (macro.variadicParam != null) {
        ensure(args.size >= minArgs) {
            KlispError.EvalError("macro expects at least $minArgs arguments, got ${args.size}")
        }
    } else {
        ensure(args.size == minArgs) {
            KlispError.EvalError("macro expects $minArgs arguments, got ${args.size}")
        }
    }

    val substitutions = mutableMapOf<String, Value>()

    macro.parameters.zip(args).forEach { (param, arg) ->
        substitutions[param.name] = arg
    }

    macro.variadicParam?.let { varParam ->
        val restArgs = args.drop(minArgs)
        val restList = restArgs.foldRight(Value.Nil as Value) { arg, acc ->
            Value.Cons(arg, acc)
        }
        substitutions[varParam.name] = restList
    }

    substitute(macro.body, substitutions).bind()
}

private fun substitute(expr: Value, substitutions: Map<String, Value>): ExpandResult = either {
    when (expr) {
        is Value.Symbol -> substitutions[expr.name] ?: expr
        is Value.Cons -> {
            val newHead = substitute(expr.head, substitutions).bind()
            val newTail = substitute(expr.tail, substitutions).bind()
            cons(newHead, newTail)
        }

        else -> expr
    }
}

private fun cons(head: Value, tail: Value): Value = Value.Cons(head, tail)
