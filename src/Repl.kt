import org.jline.reader.LineReaderBuilder
import org.jline.reader.EndOfFileException
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.TerminalBuilder
import java.nio.file.Paths

class Repl {
    private val historyFile = getHistoryFile()

    fun start() {
        val terminal = TerminalBuilder.builder()
            .system(true)
            .build()

        val history = DefaultHistory()

        val reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .history(history)
            .variable("history-file", historyFile)
            .build()

        try {
            history.load()
        } catch (_: Exception) {
            // It's ok if a history file doesn't exist on the first run
        }

        printBanner()

        var lineNumber = 1

        try {
            while (true) {
                val prompt = "klisp[$lineNumber]❯ "

                try {
                    val input = reader.readLine(prompt)

                    if (input.trim().isEmpty()) {
                        continue
                    }

                    when (input.trim()) {
                        "(exit)", "exit", ":quit", ":exit" -> {
                            println("Goodbye!")
                            break
                        }
                        ":help" -> {
                            printHelp()
                            continue
                        }
                        ":history" -> {
                            printHistory(history)
                            continue
                        }
                    }

                    try {
                        val (value, _) = parse(input)
                        val result = value.show()
                        println("=> $result")
                        lineNumber++
                    } catch (e: ParseError) {
                        println("Parse error: ${e.message}")
                    } catch (e: Exception) {
                        println("Error: ${e.message}")
                    }
                } catch (_: UserInterruptException) {
                    println()
                    continue
                } catch (_: EndOfFileException) {
                    println()
                    println("Goodbye!")
                    break
                }
            }
        } finally {
            try {
                history.save()
            } catch (e: Exception) {
                println("Warning: Could not save history: ${e.message}")
            }
        }
    }

    private fun getHistoryFile(): java.nio.file.Path {
        val historyEnv = System.getenv("KLISP_REPL_HISTORY")
        if (historyEnv != null) {
            return Paths.get(historyEnv)
        }

        val homeDir = System.getProperty("user.home")
        return Paths.get(homeDir, ".klisp_history")
    }

    private fun printBanner() {
        println("klisp REPL v1.0.0")
        println("Type expressions to evaluate them.")
        println("Commands: :help :quit :history")
        println("Press Ctrl+D to exit, Ctrl+C to cancel input")
        println()
    }

    private fun printHelp() {
        println()
        println("━".repeat(50))
        println("  klisp REPL")
        println("━".repeat(50))
        println()
        println("  Commands")
        println("    :help        Show this help")
        println("    :quit        Exit the REPL")
        println("    :history     Show command history")
        println()
        println("  Navigation")
        println("    ↑/↓          Browse history")
        println("    Ctrl+R       Search history")
        println("    Ctrl+C       Interrupt/cancel input")
        println("    Ctrl+D       Exit REPL")
        println()
        println("━".repeat(50))
        println()
    }

    private fun printHistory(history: DefaultHistory) {
        val separator = "─".repeat(60)

        println()
        println(separator)
        println("  REPL History")
        println(separator)

        if (history.size() == 0) {
            println("  No history entries yet")
        } else {
            val total = history.size()
            val start = maxOf(0, total - 20)

            if (start > 0) {
                println("  ... $start entries omitted")
            }

            history.forEach { entry ->
                val index = entry.index()
                if (index >= start) {
                    println("  ${String.format("%3d", index + 1)}: ${entry.line()}")
                }
            }

            if (total > 20) {
                println()
                println("  ℹ Showing last 20 of $total entries")
            }
        }

        println(separator)
        println()
    }
}
