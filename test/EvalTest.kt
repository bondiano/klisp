import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*

class EvalTest {
    private fun evalString(input: String, env: Environment = Environment()): Value {
        val (value, _) = parse(input).fold(
            { error -> fail<Pair<Value, String>>("Parse error: ${error.message}") },
            { it }
        )
        return eval(value, env).fold(
            { error -> fail<Value>("Eval error: ${error.message}") },
            { it }
        )
    }

    private fun shouldFailEval(input: String, env: Environment = Environment()) {
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
            val env = Environment()
            evalString("(def x 42)", env)
            assertEquals(Value.Integer(42), evalString("x", env))
        }

        @Test
        fun `use variables in expressions`() {
            val env = Environment()
            evalString("(def x 10)", env)
            evalString("(def y 20)", env)
            assertEquals(Value.Integer(30), evalString("(+ x y)", env))
        }

        @Test
        fun `set existing variables`() {
            val env = Environment()
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
            val parentEnv = Environment()
            evalString("(def x 100)", parentEnv)

            val childEnv = parentEnv.createChild()
            evalString("(def y 200)", childEnv)

            assertEquals(Value.Integer(100), evalString("x", childEnv))
            assertEquals(Value.Integer(200), evalString("y", childEnv))
        }

        @Test
        fun `set! modifies parent scope`() {
            val parentEnv = Environment()
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
            val env = Environment()
            evalString("(def add (lambda (x y) (+ x y)))", env)
            assertEquals(Value.Integer(5), evalString("(add 2 3)", env))
        }

        @Test
        fun `lambda captures environment`() {
            val env = Environment()
            evalString("(def x 10)", env)
            evalString("(def add-x (lambda (y) (+ x y)))", env)
            assertEquals(Value.Integer(15), evalString("(add-x 5)", env))
        }

        @Test
        fun `lambda closure updates with set!`() {
            val env = Environment()
            evalString("(def x 10)", env)
            evalString("(def get-x (lambda () x))", env)
            assertEquals(Value.Integer(10), evalString("(get-x)", env))
            evalString("(set! x 20)", env)
            assertEquals(Value.Integer(20), evalString("(get-x)", env))
        }

        @Test
        fun `nested lambdas`() {
            val env = Environment()
            evalString("(def make-adder (lambda (x) (lambda (y) (+ x y))))", env)
            evalString("(def add-5 (make-adder 5))", env)
            assertEquals(Value.Integer(8), evalString("(add-5 3)", env))
        }

        @Test
        fun `lambda wrong argument count`() {
            val env = Environment()
            evalString("(def f (lambda (x y) (+ x y)))", env)
            shouldFailEval("(f 1)", env)
            shouldFailEval("(f 1 2 3)", env)
        }

        @Test
        fun `variadic lambda with no fixed params`() {
            val env = Environment()
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
            val env = Environment()
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
            val env = Environment()
            evalString("(def f (lambda (x y . rest) x))", env)
            shouldFailEval("(f 1)", env)  // Need at least 2 args
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
}
