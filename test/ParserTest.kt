import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*

class ParserTest {
    private fun parseString(input: String): Pair<Value, String> {
        return parse(input).fold(
            { error -> fail("Parse error: ${error.message}") },
            { it }
        )
    }

    private fun shouldFailParse(input: String) {
        assertTrue(parse(input).isLeft(), "Expected parse to fail for: $input")
    }

    @Nested
    inner class BasicTests {
        @Test
        fun `parse integers and floats`() {
            assertEquals(Value.Integer(42), parseString("42").first)
            assertEquals(Value.Integer(-10), parseString("-10").first)
            assertEquals(Value.Float(3.14), parseString("3.14").first)
        }

        @Test
        fun `parse booleans and nil`() {
            assertEquals(Value.Bool(true), parseString("true").first)
            assertEquals(Value.Bool(false), parseString("false").first)
            assertEquals(Value.Nil, parseString("nil").first)
        }

        @Test
        fun `parse strings with spaces`() {
            assertEquals(Value.Str("hello world"), parseString("\"hello world\"").first)
            assertEquals(Value.Str(""), parseString("\"\"").first)
        }

        @Test
        fun `parse symbols and operators`() {
            assertEquals(Value.Symbol("foo"), parseString("foo").first)
            assertEquals(Value.Builtin(SpecialForm.ADD), parseString("+").first)
            assertEquals(Value.Builtin(SpecialForm.SUB), parseString("-").first)
        }
    }

    @Nested
    inner class ListTests {
        @Test
        fun `parse simple lists`() {
            val (value, _) = parseString("(1 2 3)")
            val expected = Value.Cons(
                Value.Integer(1),
                Value.Cons(Value.Integer(2), Value.Cons(Value.Integer(3), Value.Nil))
            )
            assertEquals(expected, value)
        }

        @Test
        fun `parse empty list`() {
            assertEquals(Value.Nil, parseString("()").first)
        }

        @Test
        fun `parse nested lists`() {
            val (value, _) = parseString("((1 2) (3 4))")
            assertTrue(value is Value.Cons)
        }
    }

    @Nested
    inner class QuoteTests {
        @Test
        fun `parse quoted expressions`() {
            val (value, _) = parseString("'(1 2 3)")
            val inner = Value.Cons(
                Value.Integer(1),
                Value.Cons(Value.Integer(2), Value.Cons(Value.Integer(3), Value.Nil))
            )
            val expected = Value.Cons(Value.Builtin(SpecialForm.QUOTE), Value.Cons(inner, Value.Nil))
            assertEquals(expected, value)
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `unclosed list`() {
            shouldFailParse("(1 2 3")
        }

        @Test
        fun `empty input`() {
            shouldFailParse("")
        }

        @Test
        fun `quote without value`() {
            shouldFailParse("'")
        }
    }
}
