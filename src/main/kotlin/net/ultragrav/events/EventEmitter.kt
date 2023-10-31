package net.ultragrav.events

abstract class EventEmitter<E : Any>(private val clazz: Class<E>) {
    open class EventListener<T>(
        val identifier: String,
        val clazz: Class<T>,
        private val executor: (T) -> Unit
    ) {
        fun call(event: Any) {
            @Suppress("UNCHECKED_CAST")
            try {
                executor(event as T)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val listeners = mutableMapOf<Class<out E>, MutableList<EventListener<out E>>>()
    private val byId = mutableMapOf<String, EventListener<out E>>()

    inline fun <reified T : E> on(
        identifier: String = "",
        noinline listener: (T) -> Unit
    ) {
        on(T::class.java, identifier, listener)
    }

    open fun <T : E> on(
        clazz: Class<T>,
        identifier: String = "",
        listener: (T) -> Unit
    ) {
        addListener(EventListener(identifier, clazz, listener))
    }

    protected fun addListener(listener: EventListener<out E>) {
        if (listener.identifier != "" && byId.containsKey(listener.identifier))
            throw IllegalArgumentException("Identifier ${listener.identifier} is already in use")

        val list = listeners.getOrPut(clazz) { mutableListOf() }
        list.add(listener)
        if (listener.identifier != "") byId[listener.identifier] = listener
    }

    fun off(identifier: String) {
        if (identifier == "") throw IllegalArgumentException("Cannot unregister the default identifier")

        val listener = byId.remove(identifier) ?: return
        listeners[listener.clazz]?.remove(listener)
    }

    fun call(event: E) {
        val calls: List<EventListener<out Any>> = getCalls(event)
        calls.forEach { it.call(event) }
    }

    protected open fun getCalls(event: E): List<EventListener<out Any>> {
        // Get all calls corresponding to event's class and superclasses
        val calls = mutableListOf<EventListener<out Any>>()
        var clz: Class<out Any> = event::class.java
        while (clazz.isAssignableFrom(clz)) {
            val list = listeners[clz] ?: emptyList()
            calls.addAll(list)
            clz = clz.superclass
        }
        return calls
    }
}