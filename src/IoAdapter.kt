package com.bondiano.klisp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Interface for abstracting input/output operations.
 * This allows using different I/O sources (console, network, browser, etc.)
 */
interface IoAdapter {
    /**
     * Read a line of input
     */
    fun readLine(): Either<KlispError, String>

    /**
     * Print a value to output
     */
    fun print(text: String): Either<KlispError, Unit>

    /**
     * Print a value followed by a newline
     */
    fun println(text: String): Either<KlispError, Unit> = either {
        print(text).bind()
        print("\n").bind()
    }

    /**
     * Read the contents of a file
     */
    fun readFile(path: String): Either<KlispError, String>
}

/**
 * Standard I/O adapter using `System.in` and `System.out`
 */
class StdioAdapter : IoAdapter {
    private val reader = BufferedReader(InputStreamReader(System.`in`))
    private val writer = PrintWriter(System.out, true)

    override fun readLine(): Either<KlispError, String> {
        return try {
            val line = reader.readLine()
            line?.right() ?: KlispError.RuntimeError("End of input reached").left()
        } catch (e: Exception) {
            KlispError.RuntimeError("Failed to read input: ${e.message}").left()
        }
    }

    override fun print(text: String): Either<KlispError, Unit> {
        return try {
            writer.print(text)
            writer.flush()
            Unit.right()
        } catch (e: Exception) {
            KlispError.RuntimeError("Failed to print: ${e.message}").left()
        }
    }

    override fun readFile(path: String): Either<KlispError, String> {
        return try {
            java.io.File(path).readText().right()
        } catch (e: Exception) {
            KlispError.RuntimeError("Failed to read file '$path': ${e.message}").left()
        }
    }
}

/**
 * String-based I/O adapter for testing
 * Provides input from a list of strings and collects output in a string buffer
 */
class StringIoAdapter(
    private val input: MutableList<String> = mutableListOf(), private val output: StringBuilder = StringBuilder()
) : IoAdapter {
    private var inputPosition = 0

    override fun readLine(): Either<KlispError, String> {
        return if (inputPosition < input.size) {
            val line = input[inputPosition]
            inputPosition++
            line.right()
        } else {
            KlispError.RuntimeError("No more input available").left()
        }
    }

    override fun print(text: String): Either<KlispError, Unit> {
        output.append(text)
        return Unit.right()
    }

    override fun readFile(path: String): Either<KlispError, String> {
        return KlispError.RuntimeError("File operations not supported in StringIoAdapter").left()
    }

    /**
     * Get accumulated output
     */
    fun getOutput(): String = output.toString()

    companion object {
        /**
         * Create an adapter with only output (no input)
         */
        fun outputOnly(): StringIoAdapter = StringIoAdapter(mutableListOf(), StringBuilder())

        /**
         * Create an adapter with input
         */
        fun withInput(vararg lines: String): StringIoAdapter = StringIoAdapter(lines.toMutableList(), StringBuilder())
    }
}
