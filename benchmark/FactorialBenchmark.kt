package com.bondiano.klisp.benchmark

import com.bondiano.klisp.*
import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
class FactorialBenchmark {

    private lateinit var env: Environment

    @Setup
    fun setup() {
        env = Environment(ioAdapter = StringIoAdapter())

        // Tail-recursive factorial using accumulator pattern
        // This should work with TCO and not overflow the stack
        val factorialCode = """
            (do
                (def factorial-helper (lambda (n acc)
                    (if (= n 0)
                        acc
                        (factorial-helper (- n 1) (* n acc)))))
                (def factorial (lambda (n)
                    (factorial-helper n 1)))
                factorial)
        """.trimIndent()

        val (parsed, _) = parse(factorialCode).getOrNull()
            ?: error("Failed to parse factorial code")

        eval(parsed, env).getOrNull()
            ?: error("Failed to define factorial function")
    }

    @Benchmark
    fun factorialSmall(bh: Blackhole) {
        val code = "(factorial 10)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun factorialMedium(bh: Blackhole) {
        val code = "(factorial 100)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun factorialLarge(bh: Blackhole) {
        val code = "(factorial 1000)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun mutualRecursion(bh: Blackhole) {
        val code = """
            (do
                (def is-even (lambda (n)
                    (if (= n 0)
                        true
                        (is-odd (- n 1)))))
                (def is-odd (lambda (n)
                    (if (= n 0)
                        false
                        (is-even (- n 1)))))
                (is-even 1000))
        """.trimIndent()

        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun fibonacciRecursive(bh: Blackhole) {
        val code = """
            (do
                (def fib (lambda (n)
                    (if (< n 2)
                        n
                        (+ (fib (- n 1)) (fib (- n 2))))))
                (fib 20))
        """.trimIndent()

        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun countdown(bh: Blackhole) {
        val code = """
            (do
                (def countdown (lambda (n)
                    (if (= n 0)
                        0
                        (countdown (- n 1)))))
                (countdown 5000))
        """.trimIndent()

        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }
}
