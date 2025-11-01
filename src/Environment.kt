import arrow.core.Either
import arrow.core.left
import arrow.core.right

class Environment(
    private val parent: Environment? = null,
    private val ioAdapter: IoAdapter? = null
) {
    private val bindings = mutableMapOf<String, Value>()

    fun get(name: String): Value? = bindings[name] ?: parent?.get(name)

    fun define(name: String, value: Value) {
        bindings[name] = value
    }

    fun set(name: String, value: Value): Either<KlispError, Unit> {
        return when {
            bindings.containsKey(name) -> {
                bindings[name] = value
                Unit.right()
            }
            parent != null -> parent.set(name, value)
            else -> KlispError.EvalError("Undefined variable: $name").left()
        }
    }

    fun has(name: String): Boolean = bindings.containsKey(name) || parent?.has(name) == true

    fun createChild(): Environment = Environment(this, ioAdapter)

    fun getIoAdapter(): IoAdapter = ioAdapter ?: parent?.getIoAdapter()
        ?: throw IllegalStateException("No IoAdapter available in environment")
}

