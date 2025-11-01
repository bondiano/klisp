package com.bondiano.klisp.benchmark

import com.bondiano.klisp.*
import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
class ArithmeticBenchmark {

    private lateinit var env: Environment

    @Setup
    fun setup() {
        env = Environment(ioAdapter = StringIoAdapter())
    }

    @Benchmark
    fun additionTwo(bh: Blackhole) {
        val code = "(+ 1 2)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun additionFive(bh: Blackhole) {
        val code = "(+ 1 2 3 4 5)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun additionTwenty(bh: Blackhole) {
        val code = "(+ 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun nestedArithmetic(bh: Blackhole) {
        val code = "(+ (* 2 3) (/ 10 2) (- 8 3))"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun integerOnly(bh: Blackhole) {
        val code = "(+ 1 2 3 4 5 6 7 8 9 10)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun mixedTypes(bh: Blackhole) {
        val code = "(+ 1 2.5 3 4.5 5)"
        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }

    @Benchmark
    fun complexComputation(bh: Blackhole) {
        val code = """
            (do
                (def compute (lambda (x y z)
                    (+ (* x x) (* y y) (* z z))))
                (def a 10)
                (def b 20)
                (def c 30)
                (+ (compute a b c) (compute 1 2 3) (- 100 50)))
        """.trimIndent()

        val (parsed, _) = parse(code).getOrNull()!!
        val result = eval(parsed, env).getOrNull()!!
        bh.consume(result)
    }
}
