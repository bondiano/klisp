import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import java.io.File

/**
 * Main CLI command for klisp
 */
class Klisp : CliktCommand(
    name = "klisp"
) {
    init {
        versionOption("1.0.0", names = setOf("--version", "-v"))
    }

    override fun help(context: Context) = "A Lisp interpreter written in Kotlin"

    override fun run() {
        // If no subcommand is provided, show help
        if (currentContext.invokedSubcommand == null) {
            echo(currentContext.command.getFormattedHelp())
        }
    }
}

/**
 * REPL subcommand
 */
class ReplCommand : CliktCommand(
    name = "repl"
) {
    override fun help(context: Context) = "Start the interactive REPL (Read-Eval-Print Loop)"

    override fun run() {
        Repl().start()
    }
}

/**
 * Run subcommand - executes a file
 */
class RunCommand : CliktCommand(
    name = "run"
) {
    override fun help(context: Context) = "Run a klisp file"

    private val file by argument().optional()
    private val evalExpr by option("--eval", "-e")

    override fun run() {
        val env = Environment(ioAdapter = StdioAdapter())

        when {
            evalExpr != null -> {
                parse(evalExpr!!).fold(
                    ifLeft = { error ->
                        echo("${errorType(error)}: ${error.message}", err = true)
                        throw ProgramResult(1)
                    },
                    ifRight = { (value, _) ->
                        eval(value, env).fold(
                            ifLeft = { error ->
                                echo("${errorType(error)}: ${error.message}", err = true)
                                throw ProgramResult(1)
                            },
                            ifRight = { result -> println(result.show()) }
                        )
                    }
                )
            }

            file != null -> runFile(file!!, env)

            else -> {
                echo("Error: Either provide a file path or use --eval", err = true)
                throw ProgramResult(1)
            }
        }
    }

    fun runFile(filePath: String, env: Environment) {
        try {
            val content = File(filePath).readText()
            var remaining = content

            while (remaining.trim().isNotEmpty()) {
                parse(remaining).fold(
                    ifLeft = { error ->
                        echo("${errorType(error)}: ${error.message}", err = true)
                        throw ProgramResult(1)
                    },
                    ifRight = { (value, rest) ->
                        eval(value, env).fold(
                            ifLeft = { error ->
                                echo("${errorType(error)}: ${error.message}", err = true)
                                throw ProgramResult(1)
                            },
                            ifRight = { result ->
                                println(result.show())
                                remaining = rest
                            }
                        )
                    }
                )
            }
        } catch (_: java.io.FileNotFoundException) {
            echo("File not found: $file", err = true)
            throw ProgramResult(1)
        }
    }
}

fun configureCli(): Klisp {
    return Klisp().subcommands(
        ReplCommand(),
        RunCommand()
    )
}

private fun errorType(error: KlispError): String = when (error) {
    is KlispError.ParseError -> "Parse error"
    is KlispError.EvalError -> "Eval error"
    is KlispError.RuntimeError -> "Runtime error"
}
