import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ToPrintingStringTest {

    @Nested
    inner class FloatTests {
        @Test
        fun `should convert positive float to string`() {
            val result = Value.Float(3.14).toPrintingString()
            assertEquals("3.14", result)
        }

        @Test
        fun `should convert negative float to string`() {
            val result = Value.Float(-42.5).toPrintingString()
            assertEquals("-42.5", result)
        }

        @Test
        fun `should convert zero float to string`() {
            val result = Value.Float(0.0).toPrintingString()
            assertEquals("0.0", result)
        }

        @Test
        fun `should handle special float values`() {
            assertEquals("Infinity", Value.Float(Double.POSITIVE_INFINITY).toPrintingString())
            assertEquals("-Infinity", Value.Float(Double.NEGATIVE_INFINITY).toPrintingString())
            assertEquals("NaN", Value.Float(Double.NaN).toPrintingString())
        }
    }

    @Nested
    inner class IntegerTests {
        @ParameterizedTest
        @CsvSource(
            "42, 42",
            "-100, -100",
            "0, 0",
            "9223372036854775807, 9223372036854775807" // Long.MAX_VALUE
        )
        fun `should convert integer to string`(input: Long, expected: String) {
            val result = Value.Integer(input).toPrintingString()
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class StringTests {
        @Test
        fun `should return string text as-is`() {
            val result = Value.Str("hello world").toPrintingString()
            assertEquals("hello world", result)
        }

        @Test
        fun `should handle empty string`() {
            val result = Value.Str("").toPrintingString()
            assertEquals("", result)
        }

        @Test
        fun `should handle string with special characters`() {
            val result = Value.Str("Hello\nWorld\t!").toPrintingString()
            assertEquals("Hello\nWorld\t!", result)
        }
    }

    @Nested
    inner class BoolTests {
        @ParameterizedTest
        @CsvSource(
            "true, true",
            "false, false"
        )
        fun `should convert boolean to string`(input: Boolean, expected: String) {
            val result = Value.Bool(input).toPrintingString()
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class SymbolTests {
        @ParameterizedTest
        @CsvSource(
            "foo, foo",
            "+, +",
            "-, -",
            "*, *",
            "define, define",
            "lambda, lambda"
        )
        fun `should return symbol name`(input: String, expected: String) {
            val result = Value.Symbol(input).toPrintingString()
            assertEquals(expected, result)
        }
    }

    @Nested
    inner class LambdaTests {
        @Test
        fun `should format lambda with no parameters`() {
            val lambda = Value.Lambda(emptyList(), listOf(Value.Integer(1)))
            val result = lambda.toPrintingString()
            assertEquals("(lambda )", result)
        }

        @Test
        fun `should format lambda with single parameter`() {
            val lambda = Value.Lambda(
                parameters = listOf(Value.Symbol("x")),
                body = listOf(Value.Integer(1))
            )
            val result = lambda.toPrintingString()
            assertEquals("(lambda x)", result)
        }

        @Test
        fun `should format lambda with multiple parameters`() {
            val lambda = Value.Lambda(
                parameters = listOf(Value.Symbol("x"), Value.Symbol("y"), Value.Symbol("z")),
                body = listOf(Value.Integer(1))
            )
            val result = lambda.toPrintingString()
            assertEquals("(lambda x y z)", result)
        }
    }

    @Nested
    inner class NilTests {
        @Test
        fun `should return nil string`() {
            val result = Value.Nil.toPrintingString()
            assertEquals("nil", result)
        }
    }

    @Nested
    inner class ConsTests {
        @Test
        fun `should format cons with single element`() {
            val cons = Value.Cons(Value.Integer(1), Value.Nil)
            val result = cons.toPrintingString()
            assertEquals("(1)", result)
        }

        @Test
        fun `should format proper list with multiple elements`() {
            val cons = Value.Cons(
                Value.Integer(1),
                Value.Cons(Value.Integer(2), Value.Cons(Value.Integer(3), Value.Nil))
            )
            val result = cons.toPrintingString()
            assertEquals("(1 2 3)", result)
        }

        @Test
        fun `should format improper list`() {
            val cons = Value.Cons(Value.Integer(1), Value.Integer(2))
            val result = cons.toPrintingString()
            assertEquals("(1 2)", result)
        }

        @Test
        fun `should format nested cons structures`() {
            val inner = Value.Cons(Value.Integer(2), Value.Cons(Value.Integer(3), Value.Nil))
            val cons = Value.Cons(Value.Integer(1), Value.Cons(inner, Value.Nil))
            val result = cons.toPrintingString()
            assertEquals("(1 (2 3))", result)
        }

        @Test
        fun `should format list with mixed types`() {
            val cons = Value.Cons(
                Value.Symbol("foo"),
                Value.Cons(Value.Integer(42), Value.Cons(Value.Str("bar"), Value.Nil))
            )
            val result = cons.toPrintingString()
            assertEquals("(foo 42 bar)", result)
        }

        @Test
        fun `should format deeply nested lists`() {
            // ((1 2) (3 4))
            val first = Value.Cons(Value.Integer(1), Value.Cons(Value.Integer(2), Value.Nil))
            val second = Value.Cons(Value.Integer(3), Value.Cons(Value.Integer(4), Value.Nil))
            val cons = Value.Cons(first, Value.Cons(second, Value.Nil))
            val result = cons.toPrintingString()
            assertEquals("((1 2) (3 4))", result)
        }
    }

    @Nested
    inner class ComplexStructuresTests {
        @Test
        fun `should format lambda application`() {
            val lambda = Value.Lambda(
                parameters = listOf(Value.Symbol("x"), Value.Symbol("y")),
                body = listOf()
            )
            val cons = Value.Cons(
                lambda,
                Value.Cons(Value.Integer(1), Value.Cons(Value.Integer(2), Value.Nil))
            )
            val result = cons.toPrintingString()
            assertEquals("((lambda x y) 1 2)", result)
        }

        @Test
        fun `should format nested function calls`() {
            // (+ 1 (- 5 3))
            val innerExpr = Value.Cons(
                Value.Symbol("-"),
                Value.Cons(Value.Integer(5), Value.Cons(Value.Integer(3), Value.Nil))
            )
            val outerExpr = Value.Cons(
                Value.Symbol("+"),
                Value.Cons(Value.Integer(1), Value.Cons(innerExpr, Value.Nil))
            )
            val result = outerExpr.toPrintingString()
            assertEquals("(+ 1 (- 5 3))", result)
        }
    }
}
