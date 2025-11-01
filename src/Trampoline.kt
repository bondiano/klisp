sealed class Trampoline<out A> {
    /**
     * A finished computation - recursion has terminated
     */
    data class Done<A>(val value: A) : Trampoline<A>()

    /**
     * A deferred computation - one more step of recursion
     * The thunk is a zero-argument function that will be called
     * to continue the computation
     */
    data class More<A>(val thunk: () -> Trampoline<A>) : Trampoline<A>()

    fun run(): A {
        var current: Trampoline<A> = this
        while (true) {
            when (current) {
                is Done -> return current.value
                is More -> current = current.thunk()
            }
        }
    }

    fun <B> map(f: (A) -> B): Trampoline<B> = when (this) {
        is Done -> Done(f(value))
        is More -> More { thunk().map(f) }
    }

    fun <B> flatMap(f: (A) -> Trampoline<B>): Trampoline<B> = when (this) {
        is Done -> f(value)
        is More -> More { thunk().flatMap(f) }
    }
}

/**
 * Helper function to create a Done trampoline
 */
fun <A> done(value: A): Trampoline<A> = Trampoline.Done(value)

/**
 * Helper function to create a More trampoline
 */
fun <A> more(thunk: () -> Trampoline<A>): Trampoline<A> = Trampoline.More(thunk)
