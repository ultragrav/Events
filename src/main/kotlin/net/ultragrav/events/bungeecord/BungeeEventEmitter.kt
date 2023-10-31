package net.ultragrav.events.bungeecord

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Event
import net.md_5.bungee.api.plugin.PluginManager
import net.md_5.bungee.event.EventBus
import net.md_5.bungee.event.EventPriority
import net.ultragrav.events.EventEmitter
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object BungeeEventEmitter : EventEmitter<Event>(Event::class.java) {
    private class CachedCall(val calls: List<BungeeEventListener<out Any>>, val maxPriority: Byte)

    private val callCache = mutableMapOf<Event, CachedCall>()

    private class Handler(val priority: Byte) {
        fun handle(event: Event) {
            val cachedCall: CachedCall = callCache[event] ?: getCalls(
                event
            ).let { CachedCall(it, processedEvents[event::class.java]!!.max()) }.also {
                callCache[event] = it
            }
            cachedCall.calls.forEach {
                if (it.priority == priority) it.call(event)
            }
            if (priority == cachedCall.maxPriority) {
                callCache.remove(event)
            }
        }
    }

    private val processedEvents: MutableMap<Class<*>, MutableSet<Byte>> = HashMap()
    private val processedEventsLock = ReentrantLock()

    private val registeredHandlers = mutableMapOf<Byte, Handler>()

    class BungeeEventListener<T>(
        identifier: String,
        clazz: Class<T>,
        val priority: Byte,
        executor: (T) -> Unit
    ) : EventListener<T>(identifier, clazz, executor)

    override fun <T : Event> on(clazz: Class<T>, identifier: String, listener: (T) -> Unit) {
        on(clazz, identifier, EventPriority.NORMAL, listener)
    }

    inline fun <reified T : Event> on(identifier: String, priority: Byte, noinline listener: (T) -> Unit) {
        on(T::class.java, identifier, priority, listener)
    }

    fun <T : Event> on(clazz: Class<T>, identifier: String, priority: Byte, listener: (T) -> Unit) {
        addListener(BungeeEventListener(identifier, clazz, priority, listener))

        register(clazz, priority)
    }

    override fun getCalls(event: Event): List<BungeeEventListener<out Any>> {
        return super.getCalls(event).map { it as BungeeEventListener<out Any> }
    }

    private fun register(clazz: Class<out Event>, priority: Byte) {
        if (Modifier.isAbstract(clazz.modifiers)) return

        processedEventsLock.withLock {
            if (!processedEvents.computeIfAbsent(clazz) { HashSet() }.add(priority)) return
            processedEvents[clazz]!!.add(priority)
        }

        register(clazz, priority)
    }

    private val eventBus: EventBus
    private val register: (Class<*>, Byte) -> Unit

    init {
        PluginManager::class.java.getDeclaredField("eventBus").let {
            it.isAccessible = true
            eventBus = it.get(ProxyServer.getInstance().pluginManager) as EventBus
        }

        @Suppress("UNCHECKED_CAST")
        val map = EventBus::class.java.getDeclaredField("byListenerAndPriority").let {
            it.isAccessible = true
            it.get(eventBus) as MutableMap<Class<*>, MutableMap<Byte, MutableMap<Any, Array<Method>>>>
        }
        val bake = EventBus::class.java.getDeclaredMethod("bakeHandlers", Class::class.java)

        register = { clazz, priority ->
            val handler = registeredHandlers.computeIfAbsent(priority) { Handler(priority) }

            map.computeIfAbsent(clazz) { HashMap() }
                .computeIfAbsent(priority) { HashMap() }[handler] =
                arrayOf(handler.javaClass.getDeclaredMethod("handle", Event::class.java).also {
                    it.isAccessible = true
                })

            bake.invoke(eventBus, clazz)
        }
    }
}