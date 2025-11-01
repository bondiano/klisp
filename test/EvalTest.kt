import org.junit.jupiter.api.Test
import com.bondiano.klisp.*

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*

class EvalTest {
    private fun createTestEnv(): Environment = Environment(ioAdapter = StringIoAdapter.outputOnly())

    private fun evalString(input: String, env: Environment = createTestEnv()): Value {
        val (value, _) = parse(input).fold(
            { error -> fail<Pair<Value, String>>("Parse error: ${error.message}") },
            { it }
        )
        return eval(value, env).fold(
            { error -> fail<Value>("Eval error: ${error.message}") },
            { it }
        )
    }

    private fun shouldFailEval(input: String, env: Environment = createTestEnv()) {
        val parseResult = parse(input)
        if (parseResult.isLeft()) return

        val (value, _) = parseResult.getOrNull()!!
        assertTrue(eval(value, env).isLeft(), "Expected evaluation to fail for: $input")
    }

    @Nested
    inner class ArithmeticTests {
        @Test
        fun `basic arithmetic operations`() {
            assertEquals(Value.Integer(6), evalString("(+ 1 2 3)"))
            assertEquals(Value.Integer(5), evalString("(- 10 5)"))
            assertEquals(Value.Integer(24), evalString("(* 2 3 4)"))
            assertEquals(Value.Float(5.0), evalString("(/ 10 2)"))
            assertEquals(Value.Integer(1), evalString("(% 10 3)"))
            assertEquals(Value.Float(8.0), evalString("(^ 2 3)"))
        }

        @Test
        fun `nested arithmetic`() {
            assertEquals(Value.Integer(14), evalString("(+ 2 (* 3 4))"))
            assertEquals(Value.Integer(20), evalString("(* (+ 2 3) 4)"))
        }

        @Test
        fun `division by zero`() {
            shouldFailEval("(/ 10 0)")
            shouldFailEval("(% 10 0)")
        }
    }

    @Nested
    inner class ComparisonTests {
        @Test
        fun `equality and comparisons`() {
            assertEquals(Value.Bool(true), evalString("(= 5 5)"))
            assertEquals(Value.Bool(false), evalString("(= 5 6)"))
            assertEquals(Value.Bool(true), evalString("(> 10 5)"))
            assertEquals(Value.Bool(true), evalString("(< 5 10)"))
        }

        @Test
        fun `mixed type comparisons`() {
            assertEquals(Value.Bool(true), evalString("(= 5 5.0)"))
            assertEquals(Value.Bool(true), evalString("(> 10.5 5)"))
        }
    }

    @Nested
    inner class StringTests {
        @Test
        fun `string concatenation`() {
            assertEquals(Value.Str("hello world"), evalString("(++ \"hello\" \" \" \"world\")"))
            assertEquals(Value.Str("answer: 42"), evalString("(++ \"answer: \" 42)"))
        }
    }

    @Nested
    inner class QuoteTests {
        @Test
        fun `quote prevents evaluation`() {
            val result = evalString("'(+ 1 2)")
            assertEquals(
                Value.Cons(
                    Value.Builtin(SpecialForm.ADD),
                    Value.Cons(Value.Integer(1), Value.Cons(Value.Integer(2), Value.Nil))
                ),
                result
            )
        }
    }

    @Nested
    inner class EnvironmentTests {
        @Test
        fun `define and lookup variables`() {
            val env = createTestEnv()
            evalString("(def x 42)", env)
            assertEquals(Value.Integer(42), evalString("x", env))
        }

        @Test
        fun `use variables in expressions`() {
            val env = createTestEnv()
            evalString("(def x 10)", env)
            evalString("(def y 20)", env)
            assertEquals(Value.Integer(30), evalString("(+ x y)", env))
        }

        @Test
        fun `set existing variables`() {
            val env = createTestEnv()
            evalString("(def x 10)", env)
            evalString("(set! x 42)", env)
            assertEquals(Value.Integer(42), evalString("x", env))
        }

        @Test
        fun `set! fails on undefined variable`() {
            shouldFailEval("(set! undefined 42)")
        }

        @Test
        fun `nested scopes`() {
            val parentEnv = createTestEnv()
            evalString("(def x 100)", parentEnv)

            val childEnv = parentEnv.createChild()
            evalString("(def y 200)", childEnv)

            assertEquals(Value.Integer(100), evalString("x", childEnv))
            assertEquals(Value.Integer(200), evalString("y", childEnv))
        }

        @Test
        fun `set! modifies parent scope`() {
            val parentEnv = createTestEnv()
            evalString("(def x 100)", parentEnv)

            val childEnv = parentEnv.createChild()
            evalString("(set! x 200)", childEnv)

            assertEquals(Value.Integer(200), evalString("x", parentEnv))
        }
    }

    @Nested
    inner class LambdaTests {
        @Test
        fun `create and call lambda`() {
            val env = createTestEnv()
            evalString("(def add (lambda (x y) (+ x y)))", env)
            assertEquals(Value.Integer(5), evalString("(add 2 3)", env))
        }

        @Test
        fun `lambda captures environment`() {
            val env = createTestEnv()
            evalString("(def x 10)", env)
            evalString("(def add-x (lambda (y) (+ x y)))", env)
            assertEquals(Value.Integer(15), evalString("(add-x 5)", env))
        }

        @Test
        fun `lambda closure updates with set!`() {
            val env = createTestEnv()
            evalString("(def x 10)", env)
            evalString("(def get-x (lambda () x))", env)
            assertEquals(Value.Integer(10), evalString("(get-x)", env))
            evalString("(set! x 20)", env)
            assertEquals(Value.Integer(20), evalString("(get-x)", env))
        }

        @Test
        fun `nested lambdas`() {
            val env = createTestEnv()
            evalString("(def make-adder (lambda (x) (lambda (y) (+ x y))))", env)
            evalString("(def add-5 (make-adder 5))", env)
            assertEquals(Value.Integer(8), evalString("(add-5 3)", env))
        }

        @Test
        fun `lambda wrong argument count`() {
            val env = createTestEnv()
            evalString("(def f (lambda (x y) (+ x y)))", env)
            shouldFailEval("(f 1)", env)
            shouldFailEval("(f 1 2 3)", env)
        }

        @Test
        fun `variadic lambda with no fixed params`() {
            val env = createTestEnv()
            evalString("(def all-args (lambda (. rest) rest))", env)

            assertEquals(Value.Nil, evalString("(all-args)", env))

            val result1 = evalString("(all-args 1)", env)
            assertEquals(Value.Cons(Value.Integer(1), Value.Nil), result1)

            val result2 = evalString("(all-args 1 2 3)", env)
            assertEquals(
                Value.Cons(Value.Integer(1),
                    Value.Cons(Value.Integer(2),
                        Value.Cons(Value.Integer(3), Value.Nil))),
                result2
            )
        }

        @Test
        fun `variadic lambda with fixed params`() {
            val env = createTestEnv()
            evalString("(def f (lambda (x y . rest) rest))", env)

            assertEquals(Value.Nil, evalString("(f 1 2)", env))

            val result = evalString("(f 1 2 3 4 5)", env)
            assertEquals(
                Value.Cons(Value.Integer(3),
                    Value.Cons(Value.Integer(4),
                        Value.Cons(Value.Integer(5), Value.Nil))),
                result
            )
        }

        @Test
        fun `variadic lambda not enough args`() {
            val env = createTestEnv()
            evalString("(def f (lambda (x y . rest) x))", env)
            shouldFailEval("(f 1)", env)  // Need at least 2 args
        }
    }

    @Nested
    inner class MacroTests {
        @Test
        fun `simple identity macro`() {
            val env = createTestEnv()
            evalString("(def id (macro (x) x))", env)
            assertEquals(Value.Integer(42), evalString("(id 42)", env))
        }

        @Test
        fun `macro transforms code`() {
            val env = createTestEnv()
            evalString("(def unless (macro (cond then else) (if cond else then)))", env)
            // (unless false 1 2) expands to (if false 2 1) => 1 (else branch)
            assertEquals(Value.Integer(1), evalString("(unless false 1 2)", env))
            assertEquals(Value.Integer(2), evalString("(unless true 1 2)", env))
        }

        @Test
        fun `expand-macro shows expansion`() {
            val env = createTestEnv()
            evalString("(def id (macro (x) x))", env)
            val result = evalString("(expand-macro (id 42))", env)
            assertEquals(Value.Integer(42), result)
        }

        @Test
        fun `macro with variadic params`() {
            val env = createTestEnv()
            // Macro that takes first arg and ignores rest
            evalString("(def first-arg (macro (x . rest) x))", env)
            assertEquals(Value.Integer(1), evalString("(first-arg 1 2 3)", env))
        }

        @Test
        fun `nested macro expansion`() {
            val env = createTestEnv()
            evalString("(def id (macro (x) x))", env)
            evalString("(def wrap (macro (x) (id x)))", env)
            assertEquals(Value.Integer(42), evalString("(wrap 42)", env))
        }
    }

    @Nested
    inner class EvalFormTests {
        @Test
        fun `eval quoted expression`() {
            val env = createTestEnv()
            assertEquals(Value.Integer(3), evalString("(eval '(+ 1 2))", env))
        }

        @Test
        fun `eval with variables`() {
            val env = createTestEnv()
            evalString("(def x 10)", env)
            evalString("(def code '(+ x 5))", env)
            assertEquals(Value.Integer(15), evalString("(eval code)", env))
        }

        @Test
        fun `eval constructs code dynamically`() {
            val env = createTestEnv()
            evalString("(def x 10)", env)
            assertEquals(Value.Integer(20), evalString("(eval '(+ x x))", env))
        }
    }

    @Nested
    inner class RaiseTests {
        @Test
        fun `raise with string`() {
            val parseResult = parse("(raise \"custom error\")")
            assertTrue(parseResult.isRight())

            val (value, _) = parseResult.getOrNull()!!
            val evalResult = eval(value, createTestEnv())

            assertTrue(evalResult.isLeft())
            val error = evalResult.leftOrNull()
            assertTrue(error is KlispError.RuntimeError)
            assertEquals("custom error", error?.message)
        }

        @Test
        fun `raise with number`() {
            val result = eval(parse("(raise 404)").getOrNull()!!.first, createTestEnv())
            assertTrue(result.isLeft())
            assertTrue(result.leftOrNull() is KlispError.RuntimeError)
        }
    }

    @Nested
    inner class ListOperationsTests {
        @Test
        fun `car returns first element`() {
            assertEquals(Value.Integer(1), evalString("(car '(1 2 3))"))
        }

        @Test
        fun `cdr returns rest`() {
            val result = evalString("(cdr '(1 2 3))")
            assertEquals(
                Value.Cons(Value.Integer(2), Value.Cons(Value.Integer(3), Value.Nil)),
                result
            )
        }

        @Test
        fun `cons creates pair`() {
            val result = evalString("(cons 1 '(2 3))")
            assertEquals(
                Value.Cons(Value.Integer(1), Value.Cons(Value.Integer(2), Value.Cons(Value.Integer(3), Value.Nil))),
                result
            )
        }

        @Test
        fun `car and cdr composition`() {
            assertEquals(Value.Integer(2), evalString("(car (cdr '(1 2 3)))"))
        }
    }

    @Nested
    inner class DoTests {
        @Test
        fun `do executes sequentially`() {
            val env = createTestEnv()
            val result = evalString("(do (def x 10) (def y 20) (+ x y))", env)
            assertEquals(Value.Integer(30), result)
        }

        @Test
        fun `do returns last value`() {
            assertEquals(Value.Integer(3), evalString("(do 1 2 3)"))
        }

        @Test
        fun `do with side effects`() {
            val env = createTestEnv()
            evalString("(do (def x 1) (set! x 10) x)", env)
            assertEquals(Value.Integer(10), evalString("x", env))
        }
    }

    @Nested
    inner class MetaTests {
        @Test
        fun `type-of returns type names`() {
            assertEquals(Value.Str("integer"), evalString("(type-of 42)"))
            assertEquals(Value.Str("float"), evalString("(type-of 3.14)"))
            assertEquals(Value.Str("string"), evalString("(type-of \"hello\")"))
            assertEquals(Value.Str("boolean"), evalString("(type-of true)"))
            assertEquals(Value.Str("nil"), evalString("(type-of nil)"))
            assertEquals(Value.Str("list"), evalString("(type-of '(1 2 3))"))
        }

        @Test
        fun `symbol creates symbol from string`() {
            val env = createTestEnv()
            evalString("(def x 42)", env)
            evalString("(def name \"x\")", env)
            val sym = evalString("(symbol name)", env)
            assertEquals(Value.Symbol("x"), sym)
            assertEquals(Value.Integer(42), evalString("(eval (symbol name))", env))
        }
    }

    @Nested
    inner class PrintTests {
        @Test
        fun `print returns value`() {
            val io = StringIoAdapter.outputOnly()
            val env = Environment(ioAdapter = io)
            assertEquals(Value.Integer(42), evalString("(print 42)", env))
            assertEquals("42\n", io.getOutput())
        }

        @Test
        fun `print string`() {
            val io = StringIoAdapter.outputOnly()
            val env = Environment(ioAdapter = io)
            assertEquals(Value.Str("hello"), evalString("(print \"hello\")", env))
            assertEquals("hello\n", io.getOutput())
        }

        @Test
        fun `print multiple times`() {
            val io = StringIoAdapter.outputOnly()
            val env = Environment(ioAdapter = io)
            evalString("(print 1)", env)
            evalString("(print 2)", env)
            evalString("(print 3)", env)
            assertEquals("1\n2\n3\n", io.getOutput())
        }
    }

    @Nested
    inner class ReadTests {
        @Test
        fun `read integer`() {
            val io = StringIoAdapter.withInput("42")
            val env = Environment(ioAdapter = io)
            assertEquals(Value.Integer(42), evalString("(read)", env))
        }

        @Test
        fun `read string`() {
            val io = StringIoAdapter.withInput("\"hello\"")
            val env = Environment(ioAdapter = io)
            assertEquals(Value.Str("hello"), evalString("(read)", env))
        }

        @Test
        fun `read expression`() {
            val io = StringIoAdapter.withInput("(+ 1 2)")
            val env = Environment(ioAdapter = io)
            val expr = evalString("(read)", env)
            assertEquals(
                Value.Cons(
                    Value.Builtin(SpecialForm.ADD),
                    Value.Cons(Value.Integer(1), Value.Cons(Value.Integer(2), Value.Nil))
                ),
                expr
            )
        }

        @Test
        fun `read and eval`() {
            val io = StringIoAdapter.withInput("(+ 10 20)")
            val env = Environment(ioAdapter = io)
            val result = evalString("(eval (read))", env)
            assertEquals(Value.Integer(30), result)
        }

        @Test
        fun `read multiple times`() {
            val io = StringIoAdapter.withInput("1", "2", "3")
            val env = Environment(ioAdapter = io)
            assertEquals(Value.Integer(1), evalString("(read)", env))
            assertEquals(Value.Integer(2), evalString("(read)", env))
            assertEquals(Value.Integer(3), evalString("(read)", env))
        }

        @Test
        fun `read fails when no input`() {
            val io = StringIoAdapter.outputOnly()
            val env = Environment(ioAdapter = io)
            shouldFailEval("(read)", env)
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `undefined symbol`() {
            shouldFailEval("undefined-symbol")
        }

        @Test
        fun `wrong argument types`() {
            shouldFailEval("(+ 1 \"hello\")")
        }

        @Test
        fun `applying non-function`() {
            shouldFailEval("(42 1 2)")
        }

        @Test
        fun `define with non-symbol`() {
            shouldFailEval("(def 42 100)")
        }
    }

    @Nested
    inner class TailCallOptimizationTests {
        @Test
        fun `tail recursive factorial does not overflow`() {
            val env = createTestEnv()

            evalString("""
                (def factorial (lambda (n acc)
                    (if (= n 0)
                        acc
                        (factorial (- n 1) (* n acc)))))
            """.trimIndent(), env)

            // This would cause StackOverflowError without TCO
            val result = evalString("(factorial 1000 1)", env)
            assertTrue(result is Value.Integer || result is Value.Float)
        }

        @Test
        fun `mutual recursion even odd`() {
            val env = createTestEnv()

            evalString("""
                (def is-even (lambda (n)
                    (if (= n 0)
                        true
                        (is-odd (- n 1)))))
            """.trimIndent(), env)

            evalString("""
                (def is-odd (lambda (n)
                    (if (= n 0)
                        false
                        (is-even (- n 1)))))
            """.trimIndent(), env)

            // Deep mutual recursion should not overflow
            assertEquals(Value.Bool(true), evalString("(is-even 10000)", env))
            assertEquals(Value.Bool(false), evalString("(is-odd 10000)", env))
        }

        @Test
        fun `tail recursive sum`() {
            val env = createTestEnv()

            evalString("""
                (def sum (lambda (n acc)
                    (if (= n 0)
                        acc
                        (sum (- n 1) (+ n acc)))))
            """.trimIndent(), env)

            val result = evalString("(sum 5000 0)", env)
            assertEquals(Value.Integer(12502500), result)
        }

        @Test
        fun `nested if in tail position`() {
            val env = createTestEnv()

            evalString("""
                (def nested-if (lambda (n)
                    (if (> n 0)
                        (if (= n 1)
                            1
                            (nested-if (- n 1)))
                        0)))
            """.trimIndent(), env)

            val result = evalString("(nested-if 5000)", env)
            assertEquals(Value.Integer(1), result)
        }

        @Test
        fun `do with tail position`() {
            val env = createTestEnv()

            evalString("""
                (def countdown (lambda (n)
                    (do
                        (if (= n 0)
                            0
                            (countdown (- n 1))))))
            """.trimIndent(), env)

            // Should not overflow
            val result = evalString("(countdown 5000)", env)
            assertEquals(Value.Integer(0), result)
        }

        @Test
        fun `fibonacci with accumulators`() {
            val env = createTestEnv()

            evalString("""
                (def fib (lambda (n a b)
                    (if (= n 0)
                        a
                        (fib (- n 1) b (+ a b)))))
            """.trimIndent(), env)

            assertEquals(Value.Integer(0), evalString("(fib 0 0 1)", env))
            assertEquals(Value.Integer(1), evalString("(fib 1 0 1)", env))
            assertEquals(Value.Integer(1), evalString("(fib 2 0 1)", env))
            assertEquals(Value.Integer(2), evalString("(fib 3 0 1)", env))
            assertEquals(Value.Integer(5), evalString("(fib 5 0 1)", env))
            assertEquals(Value.Integer(55), evalString("(fib 10 0 1)", env))

            // Deep recursion should work
            val result = evalString("(fib 1000 0 1)", env)
            assertTrue(result is Value.Integer || result is Value.Float)
        }
    }
}
