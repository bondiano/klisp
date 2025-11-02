import com.bondiano.klisp.*

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.ArrayList

class InteropTest {
    private fun createTestEnv(): Environment = Environment(ioAdapter = StringIoAdapter.outputOnly())

    private fun evalString(input: String, env: Environment = createTestEnv()): Value {
        val (value, _) = parse(input).fold({ error -> fail("Parse error: ${error.message}") }, { it })
        return eval(value, env).fold({ error -> fail("Eval error: ${error.message}") }, { it })
    }

    private fun shouldFailEval(input: String, env: Environment = createTestEnv()) {
        val parseResult = parse(input)
        if (parseResult.isLeft()) return

        val (value, _) = parseResult.getOrNull()!!
        assertTrue(eval(value, env).isLeft(), "Expected evaluation to fail for: $input")
    }

    @Nested
    inner class NewTests {
        @Test
        fun `new creates ArrayList`() {
            val value = evalString("(new java.util.ArrayList)")
            assertTrue(value is Value.JavaObject)
            assertTrue((value as Value.JavaObject).obj is ArrayList<*>)
        }

        @Test
        fun `new creates String with argument`() {
            val value = evalString("(new java.lang.String \"hello\")")
            assertTrue(value is Value.JavaObject)
            // String is also stored as JavaObject when created via new
        }

        @Test
        fun `new creates StringBuilder`() {
            val value = evalString("(new java.lang.StringBuilder)")
            assertTrue(value is Value.JavaObject)
            assertTrue((value as Value.JavaObject).obj is StringBuilder)
        }

        @Test
        fun `new with invalid class name fails`() {
            shouldFailEval("(new NonExistentClass)")
        }
    }

    @Nested
    inner class DotMethodTests {
        @Test
        fun `call toString on integer`() {
            assertEquals(Value.Str("42"), evalString("(. toString 42)"))
        }

        @Test
        fun `call substring on string`() {
            assertEquals(Value.Str("el"), evalString("(. substring \"hello\" 1 3)"))
        }

        @Test
        fun `call toUpperCase on string`() {
            assertEquals(Value.Str("HELLO"), evalString("(. toUpperCase \"hello\")"))
        }

        @Test
        fun `call length on string`() {
            assertEquals(Value.Integer(5), evalString("(. length \"hello\")"))
        }

        @Test
        fun `syntactic sugar for method call`() {
            assertEquals(Value.Str("HELLO"), evalString("(.toUpperCase \"hello\")"))
        }

        @Test
        fun `syntactic sugar with arguments`() {
            assertEquals(Value.Str("el"), evalString("(.substring \"hello\" 1 3)"))
        }

        @Test
        fun `chained method calls`() {
            assertEquals(Value.Str("HEL"), evalString("(do (def s \"hello\") (.toUpperCase (.substring s 0 3)))"))
        }
    }

    @Nested
    inner class ArrayListTests {
        @Test
        fun `create ArrayList and add elements`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "hello")
                    (.add list "world")
                    (.size list))
            """.trimIndent()
            assertEquals(Value.Integer(2), evalString(code))
        }

        @Test
        fun `create ArrayList and get element`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "hello")
                    (.add list "world")
                    (.get list 0))
            """.trimIndent()
            assertEquals(Value.Str("hello"), evalString(code))
        }

        @Test
        fun `ArrayList isEmpty`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.isEmpty list))
            """.trimIndent()
            assertEquals(Value.Bool(true), evalString(code))
        }
    }

    @Nested
    inner class TypeOfTests {
        @Test
        fun `type-of java object`() {
            assertEquals(Value.Str("java-object"), evalString("(type-of (new java.util.ArrayList))"))
        }
    }

    @Nested
    inner class ConversionTests {
        @Test
        fun `valueToJava converts primitives`() {
            assertEquals(42L, valueToJava(Value.Integer(42)))
            assertEquals(3.14, valueToJava(Value.Float(3.14)))
            assertEquals("hello", valueToJava(Value.Str("hello")))
            assertEquals(true, valueToJava(Value.Bool(true)))
            assertEquals(null, valueToJava(Value.Nil))
        }

        @Test
        fun `javaToValue converts primitives`() {
            assertEquals(Value.Integer(42), javaToValue(42))
            assertEquals(Value.Integer(42), javaToValue(42L))
            assertEquals(Value.Float(3.14), javaToValue(3.14))
            assertEquals(Value.Str("hello"), javaToValue("hello"))
            assertEquals(Value.Bool(true), javaToValue(true))
            assertEquals(Value.Nil, javaToValue(null))
        }

        @Test
        fun `javaToValue wraps ArrayList as JavaObject`() {
            val list = ArrayList<String>()
            val result = javaToValue(list)
            assertTrue(result is Value.JavaObject)
            assertEquals(list, (result as Value.JavaObject).obj)
        }
    }

    @Nested
    inner class IntegrationTests {
        @Test
        fun `working with numbers through Java`() {
            val code = """
                (do
                    (def x 42)
                    (.toString x))
            """.trimIndent()
            assertEquals(Value.Str("42"), evalString(code))
        }

        @Test
        fun `process list items with Java methods`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "alice")
                    (.add list "bob")
                    (.toUpperCase (.get list 0)))
            """.trimIndent()
            assertEquals(Value.Str("ALICE"), evalString(code))
        }
    }

    // ========== Kotlin-specific Interop Tests ==========

    @Nested
    inner class KotlinPairTests {
        @Test
        fun `create Pair and access components`() {
            val code = """
                (do
                    (def pair (new kotlin.Pair "key" "value"))
                    (.component1 pair))
            """.trimIndent()
            assertEquals(Value.Str("key"), evalString(code))
        }

        @Test
        fun `access second component of Pair`() {
            val code = """
                (do
                    (def pair (new kotlin.Pair "first" "second"))
                    (.component2 pair))
            """.trimIndent()
            assertEquals(Value.Str("second"), evalString(code))
        }

        @Test
        fun `create Pair with mixed types`() {
            val code = """
                (do
                    (def pair (new kotlin.Pair "name" 42))
                    (.component2 pair))
            """.trimIndent()
            assertEquals(Value.Integer(42), evalString(code))
        }

        @Test
        fun `destructure Pair with lambda`() {
            val code = """
                (do
                    (def pair (new kotlin.Pair "hello" "world"))
                    (def first (.component1 pair))
                    (def second (.component2 pair))
                    (.toUpperCase (++ first " " second)))
            """.trimIndent()
            assertEquals(Value.Str("HELLO WORLD"), evalString(code))
        }
    }

    @Nested
    inner class KotlinTripleTests {
        @Test
        fun `create Triple and access first component`() {
            val code = """
                (do
                    (def triple (new kotlin.Triple 1 2 3))
                    (.component1 triple))
            """.trimIndent()
            assertEquals(Value.Integer(1), evalString(code))
        }

        @Test
        fun `access all Triple components`() {
            val code = """
                (do
                    (def triple (new kotlin.Triple "a" "b" "c"))
                    (def c1 (.component1 triple))
                    (def c2 (.component2 triple))
                    (def c3 (.component3 triple))
                    (++ c1 c2 c3))
            """.trimIndent()
            assertEquals(Value.Str("abc"), evalString(code))
        }

        @Test
        fun `Triple with mixed types`() {
            val code = """
                (do
                    (def triple (new kotlin.Triple "name" 25 true))
                    (.component3 triple))
            """.trimIndent()
            assertEquals(Value.Bool(true), evalString(code))
        }

        @Test
        fun `compute sum using Triple`() {
            val code = """
                (do
                    (def triple (new kotlin.Triple 10 20 30))
                    (+ (.component1 triple) (.component2 triple) (.component3 triple)))
            """.trimIndent()
            assertEquals(Value.Integer(60), evalString(code))
        }
    }

    @Nested
    inner class KotlinStdlibTests {
        @Test
        fun `use kotlin collections with standard methods`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "kotlin")
                    (.add list "java")
                    (.add list "scala")
                    (.size list))
            """.trimIndent()
            assertEquals(Value.Integer(3), evalString(code))
        }

        @Test
        fun `combine Kotlin Pair with list operations`() {
            val code = """
                (do
                    (def pair (new kotlin.Pair "first" "second"))
                    (def list (new java.util.ArrayList))
                    (.add list (.component1 pair))
                    (.add list (.component2 pair))
                    (.get list 1))
            """.trimIndent()
            assertEquals(Value.Str("second"), evalString(code))
        }

        @Test
        fun `use toString on Kotlin objects`() {
            val code = """
                (do
                    (def pair (new kotlin.Pair "a" "b"))
                    (.toString pair))
            """.trimIndent()
            assertEquals(Value.Str("(a, b)"), evalString(code))
        }

        @Test
        fun `use toString on Triple`() {
            val code = """
                (do
                    (def triple (new kotlin.Triple 1 2 3))
                    (.toString triple))
            """.trimIndent()
            assertEquals(Value.Str("(1, 2, 3)"), evalString(code))
        }
    }

    @Nested
    inner class ExtensionFunctionTests {
        @Test
        fun `call reversed on string`() {
            assertEquals(Value.Str("olleh"), evalString("(.reversed \"hello\")"))
        }

        @Test
        fun `call first on ArrayList`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "first")
                    (.add list "second")
                    (.first list))
            """.trimIndent()
            assertEquals(Value.Str("first"), evalString(code))
        }

        @Test
        fun `call last on ArrayList`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "alpha")
                    (.add list "beta")
                    (.add list "gamma")
                    (.last list))
            """.trimIndent()
            assertEquals(Value.Str("gamma"), evalString(code))
        }

        @Test
        fun `call drop on string`() {
            assertEquals(Value.Str("lo"), evalString("(.drop \"hello\" 3)"))
        }

        @Test
        fun `call take on string`() {
            assertEquals(Value.Str("hel"), evalString("(.take \"hello\" 3)"))
        }

        @Test
        fun `call count on ArrayList`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list 1)
                    (.add list 2)
                    (.add list 3)
                    (.count list))
            """.trimIndent()
            assertEquals(Value.Integer(3), evalString(code))
        }

        @Test
        fun `call reversed on ArrayList`() {
            val code = """
                (do
                    (def list (new java.util.ArrayList))
                    (.add list "a")
                    (.add list "b")
                    (.add list "c")
                    (.first (.reversed list)))
            """.trimIndent()
            assertEquals(Value.Str("c"), evalString(code))
        }

        @Test
        fun `chain extension functions`() {
            // take 5: "hello world" -> "hello"
            // drop 1: "hello" -> "ello"
            // reversed: "ello" -> "olle"
            val code = """
                (.reversed (.drop (.take "hello world" 5) 1))
            """.trimIndent()
            assertEquals(Value.Str("olle"), evalString(code))
        }
    }

}
