package net.ultragrav.events

import org.bukkit.event.EventPriority

abstract class EventEmitter<E : Any>(private val clazz: Class<E>) {
    protected class EventListener<T>(
        val identifier: String,
        val clazz: Class<T>,
        val priority: EventPriority,
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
        priority: EventPriority = EventPriority.NORMAL,
        noinline listener: (T) -> Unit
    ) {
        on(T::class.java, identifier, priority, listener)
    }

    fun <T : E> on(
        clazz: Class<T>,
        identifier: String = "",
        priority: EventPriority = EventPriority.NORMAL,
        listener: (T) -> Unit
    ) {
        if (identifier != "" && byId.containsKey(identifier))
            throw IllegalArgumentException("Identifier $identifier is already in use")

        val eventListener = EventListener(identifier, clazz, priority, listener)

        val list = listeners.getOrPut(clazz) { mutableListOf() }
        list.add(eventListener)
        if (identifier != "") byId[identifier] = eventListener

        register(clazz)
    }

    fun off(identifier: String) {
        if (identifier == "") throw IllegalArgumentException("Cannot unregister the default identifier")

        val listener = byId.remove(identifier) ?: return
        listeners[listener.clazz]?.remove(listener)
    }

    fun call(event: E) {
        val calls: List<EventListener<out Any>> = getCalls(event)
        EventPriority.entries.forEach { priority ->
            calls.forEach { if (it.priority == priority) it.call(event) }
        }
    }

    protected fun getCalls(event: E): List<EventListener<out Any>> {
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

    protected open fun register(clazz: Class<out E>) {}
}