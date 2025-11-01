package com.bondiano.klisp

sealed interface KlispError {
    val message: String

    data class ParseError(override val message: String) : KlispError
    data class EvalError(override val message: String) : KlispError
    data class RuntimeError(override val message: String) : KlispError
}
