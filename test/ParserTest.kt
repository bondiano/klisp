import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParserTest {

    @Nested
    inner class SimpleListTests {
        @Test
        fun `should parse simple list with integers`() {
            val (value, rest) = parse("(1 2 3)")

            val expected = Value.Cons(
                Value.Integer(1),
                Value.Cons(
                    Value.Integer(2),
                    Value.Cons(Value.Integer(3), Value.Nil)
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse empty list`() {
            val (value, rest) = parse("()")

            assertEquals(Value.Nil, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse single element list`() {
            val (value, rest) = parse("(42)")

            val expected = Value.Cons(Value.Integer(42), Value.Nil)

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse list with remaining input`() {
            val (value, rest) = parse("(1 2) extra")

            val expected = Value.Cons(
                Value.Integer(1),
                Value.Cons(Value.Integer(2), Value.Nil)
            )

            assertEquals(expected, value)
            assertEquals(" extra", rest)
        }
    }

    @Nested
    inner class QuoteTests {
        @Test
        fun `should parse quoted list`() {
            val (value, rest) = parse("'(1 2 3)")

            val quotedList = Value.Cons(
                Value.Integer(1),
                Value.Cons(
                    Value.Integer(2),
                    Value.Cons(Value.Integer(3), Value.Nil)
                )
            )

            val expected = Value.Cons(
                Value.Builtin(SpecialForm.QUOTE),
                Value.Cons(quotedList, Value.Nil)
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse quoted atom`() {
            val (value, rest) = parse("'foo")

            val expected = Value.Cons(
                Value.Builtin(SpecialForm.QUOTE),
                Value.Cons(Value.Symbol("foo"), Value.Nil)
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse quoted integer`() {
            val (value, rest) = parse("'42")

            val expected = Value.Cons(
                Value.Builtin(SpecialForm.QUOTE),
                Value.Cons(Value.Integer(42), Value.Nil)
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse quoted empty list`() {
            val (value, rest) = parse("'()")

            val expected = Value.Cons(
                Value.Builtin(SpecialForm.QUOTE),
                Value.Cons(Value.Nil, Value.Nil)
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }
    }

    @Nested
    inner class AtomTests {
        @Test
        fun `should parse positive integer`() {
            val (value, rest) = parse("42")

            assertEquals(Value.Integer(42), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse negative integer`() {
            val (value, rest) = parse("-123")

            assertEquals(Value.Integer(-123), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse float`() {
            val (value, rest) = parse("3.14")

            assertEquals(Value.Float(3.14), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse negative float`() {
            val (value, rest) = parse("-2.5")

            assertEquals(Value.Float(-2.5), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse symbol`() {
            val (value, rest) = parse("foo")

            assertEquals(Value.Symbol("foo"), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse true boolean`() {
            val (value, rest) = parse("true")

            assertEquals(Value.Bool(true), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse false boolean`() {
            val (value, rest) = parse("false")

            assertEquals(Value.Bool(false), value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse nil`() {
            val (value, rest) = parse("nil")

            assertEquals(Value.Nil, value)
            assertEquals("", rest)
        }

        @ParameterizedTest
        @CsvSource(
            "+, ADD",
            "-, SUB",
            "*, MUL",
            "/, DIV",
            "if, IF",
            "lambda, LAMBDA",
            "define, DEFINE",
            "quote, QUOTE"
        )
        fun `should parse builtin special forms`(input: String, formName: String) {
            val (value, rest) = parse(input)

            assertTrue(value is Value.Builtin)
            assertEquals(SpecialForm.valueOf(formName), (value as Value.Builtin).specialForm)
            assertEquals("", rest)
        }
    }

    @Nested
    inner class NestedListTests {
        @Test
        fun `should parse nested lists`() {
            val (value, rest) = parse("((1 2) (3 4))")

            val firstList = Value.Cons(
                Value.Integer(1),
                Value.Cons(Value.Integer(2), Value.Nil)
            )
            val secondList = Value.Cons(
                Value.Integer(3),
                Value.Cons(Value.Integer(4), Value.Nil)
            )
            val expected = Value.Cons(firstList, Value.Cons(secondList, Value.Nil))

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse deeply nested lists`() {
            val (value, rest) = parse("(((1)))")

            val innermost = Value.Cons(Value.Integer(1), Value.Nil)
            val middle = Value.Cons(innermost, Value.Nil)
            val expected = Value.Cons(middle, Value.Nil)

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse mixed nested structure`() {
            val (value, rest) = parse("(1 (2 3) 4)")

            val innerList = Value.Cons(
                Value.Integer(2),
                Value.Cons(Value.Integer(3), Value.Nil)
            )
            val expected = Value.Cons(
                Value.Integer(1),
                Value.Cons(
                    innerList,
                    Value.Cons(Value.Integer(4), Value.Nil)
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }
    }

    @Nested
    inner class WhitespaceAndCommentsTests {
        @Test
        fun `should handle leading whitespace`() {
            val (value, rest) = parse("   42")

            assertEquals(Value.Integer(42), value)
            assertEquals("", rest)
        }

        @Test
        fun `should handle whitespace in list`() {
            val (value, rest) = parse("(  1   2   3  )")

            val expected = Value.Cons(
                Value.Integer(1),
                Value.Cons(
                    Value.Integer(2),
                    Value.Cons(Value.Integer(3), Value.Nil)
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should handle newlines`() {
            val (value, rest) = parse("(\n1\n2\n3\n)")

            val expected = Value.Cons(
                Value.Integer(1),
                Value.Cons(
                    Value.Integer(2),
                    Value.Cons(Value.Integer(3), Value.Nil)
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should skip comments`() {
            val (value, rest) = parse(";comment\n42")

            assertEquals(Value.Integer(42), value)
            assertEquals("", rest)
        }
    }

    @Nested
    inner class MixedTypesTests {
        @Test
        fun `should parse list with mixed types`() {
            val (value, rest) = parse("(foo 42 true)")

            val expected = Value.Cons(
                Value.Symbol("foo"),
                Value.Cons(
                    Value.Integer(42),
                    Value.Cons(Value.Bool(true), Value.Nil)
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse complex expression`() {
            val (value, rest) = parse("(+ 1 (* 2 3))")

            val innerExpr = Value.Cons(
                Value.Builtin(SpecialForm.MUL),
                Value.Cons(
                    Value.Integer(2),
                    Value.Cons(Value.Integer(3), Value.Nil)
                )
            )
            val expected = Value.Cons(
                Value.Builtin(SpecialForm.ADD),
                Value.Cons(
                    Value.Integer(1),
                    Value.Cons(innerExpr, Value.Nil)
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse lambda definition`() {
            val (value, rest) = parse("(lambda (x y) (+ x y))")

            val params = Value.Cons(
                Value.Symbol("x"),
                Value.Cons(Value.Symbol("y"), Value.Nil)
            )
            val body = Value.Cons(
                Value.Builtin(SpecialForm.ADD),
                Value.Cons(
                    Value.Symbol("x"),
                    Value.Cons(Value.Symbol("y"), Value.Nil)
                )
            )
            val expected = Value.Cons(
                Value.Builtin(SpecialForm.LAMBDA),
                Value.Cons(params, Value.Cons(body, Value.Nil))
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `should throw error on empty input`() {
            val exception = assertThrows<ParseError> {
                parse("")
            }

            assertEquals("Unexpected end of input", exception.message)
        }

        @Test
        fun `should throw error on whitespace only input`() {
            val exception = assertThrows<ParseError> {
                parse("   ")
            }

            assertEquals("Unexpected end of input", exception.message)
        }

        @Test
        fun `should throw error on unclosed list`() {
            assertThrows<ParseError> {
                parse("(1 2 3")
            }
        }

        @Test
        fun `should throw error on quote without value`() {
            val exception = assertThrows<ParseError> {
                parse("'")
            }

            assertEquals("Unexpected end of input", exception.message)
        }
    }

    @Nested
    inner class EdgeCasesTests {
        @Test
        fun `should parse multiple expressions and return first`() {
            val (value, rest) = parse("1 2 3")

            assertEquals(Value.Integer(1), value)
            assertEquals(" 2 3", rest)
        }

        @Test
        fun `should parse quoted quote`() {
            val (value, rest) = parse("''foo")

            val innerQuote = Value.Cons(
                Value.Builtin(SpecialForm.QUOTE),
                Value.Cons(Value.Symbol("foo"), Value.Nil)
            )
            val expected = Value.Cons(
                Value.Builtin(SpecialForm.QUOTE),
                Value.Cons(innerQuote, Value.Nil)
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }

        @Test
        fun `should parse list with special characters in symbols`() {
            val (value, rest) = parse("(+ - * /)")

            val expected = Value.Cons(
                Value.Builtin(SpecialForm.ADD),
                Value.Cons(
                    Value.Builtin(SpecialForm.SUB),
                    Value.Cons(
                        Value.Builtin(SpecialForm.MUL),
                        Value.Cons(Value.Builtin(SpecialForm.DIV), Value.Nil)
                    )
                )
            )

            assertEquals(expected, value)
            assertEquals("", rest)
        }
    }
}
