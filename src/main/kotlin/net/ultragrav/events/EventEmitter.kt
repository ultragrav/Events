package net.ultragrav.events

typealias NormalEventEmitter<E> = EventEmitter<E, EventEmitter.EventListener<*, *>>
abstract class EventEmitter<E : Any, L : EventEmitter.EventListener<*, *>>(private val eventClazz: Class<E>) {
    class DefaultEventListener<T>(
        emitter: EventEmitter<*, *>,
        identifier: String,
        clazz: Class<*>,
        executor: (T) -> Unit
    ) : EventListener<T, DefaultEventListener<T>>(emitter, identifier, clazz, executor)

    open class EventListener<T, S : EventListener<T, S>>(
        private val emitter: EventEmitter<*, *>,
        val identifier: String,
        val clazz: Class<*>,
        private val executor: (T) -> Unit
    ) {
        private var once = false
        @Suppress("UNCHECKED_CAST")
        fun once(): S {
            once = true
            return this as S
        }

        internal fun call(event: Any) {
            @Suppress("UNCHECKED_CAST")
            try {
                executor(event as T)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (once) emitter.off(this)
        }
    }

    private val listeners = mutableMapOf<Class<*>, MutableList<EventListener<*, *>>>()
    private val byId = mutableMapOf<String, EventListener<*, *>>()

    inline fun <reified T : E> on(
        identifier: String = "",
        noinline listener: (T) -> Unit
    ): L {
        return on(T::class.java, identifier, listener)
    }

    open fun <T : E> on(
        clazz: Class<T>,
        identifier: String = "",
        listener: (T) -> Unit
    ): L {
        val eventListener = createListener(identifier, clazz, listener)
        addListener(eventListener)
        return eventListener
    }

    protected open fun <T : E> createListener(
        identifier: String,
        clazz: Class<T>,
        listener: (T) -> Unit
    ): L {
        @Suppress("UNCHECKED_CAST")
        return DefaultEventListener(this, identifier, clazz, listener) as L
    }

    private fun addListener(listener: EventListener<*, *>) {
        if (listener.identifier != "" && byId.containsKey(listener.identifier))
            throw IllegalArgumentException("Identifier ${listener.identifier} is already in use")

        val list = listeners.getOrPut(listener.clazz) { mutableListOf() }
        list.add(listener)
        if (listener.identifier != "") byId[listener.identifier] = listener
    }

    fun off(identifier: String) {
        if (identifier == "") throw IllegalArgumentException("Cannot unregister the default identifier")

        val listener = byId.remove(identifier) ?: return
        listeners[listener.clazz]?.remove(listener)
    }

    fun off(listener: EventListener<*, *>) {
        if (listener.identifier != "") byId.remove(listener.identifier)
        listeners[listener.clazz]?.remove(listener)
    }

    fun call(event: E) {
        val calls: List<EventListener<*, *>> = getCalls(event)
        calls.forEach { it.call(event) }
    }

    protected open fun getCalls(event: E): List<EventListener<*, *>> {
        // Get all calls corresponding to event's class and superclasses
        val calls = mutableListOf<EventListener<*, *>>()
        var clz: Class<out Any>? = event::class.java
        while (clz != null && eventClazz.isAssignableFrom(clz)) {
            val list = listeners[clz] ?: emptyList()
            calls.addAll(list)
            clz = clz.superclass
        }
        return calls
    }
}