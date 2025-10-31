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
        when {
            evalExpr != null -> {
                try {
                    val (value, _) = parse(evalExpr!!)
                    val result = eval(value)
                    println(result.show())
                } catch (e: ParseError) {
                    echo("Parse error: ${e.message}", err = true)
                    throw ProgramResult(1)
                } catch (e: EvalError) {
                    echo("Eval error: ${e.message}", err = true)
                    throw ProgramResult(1)
                } catch (e: Exception) {
                    echo("Error: ${e.message}", err = true)
                    throw ProgramResult(1)
                }
            }
            file != null -> {
                try {
                    val content = File(file!!).readText()
                    var remaining = content

                    while (remaining.trim().isNotEmpty()) {
                        val (value, rest) = parse(remaining)
                        val result = eval(value)
                        println(result.show())
                        remaining = rest
                    }
                } catch (_: java.io.FileNotFoundException) {
                    echo("Error: File not found: $file", err = true)
                    throw ProgramResult(1)
                } catch (e: ParseError) {
                    echo("Parse error: ${e.message}", err = true)
                    throw ProgramResult(1)
                } catch (e: EvalError) {
                    echo("Eval error: ${e.message}", err = true)
                    throw ProgramResult(1)
                } catch (e: Exception) {
                    echo("Error: ${e.message}", err = true)
                    throw ProgramResult(1)
                }
            }
            else -> {
                echo("Error: Either provide a file path or use --eval", err = true)
                throw ProgramResult(1)
            }
        }
    }
}

fun configureCli(): Klisp {
    return Klisp().subcommands(
        ReplCommand(),
        RunCommand()
    )
}
