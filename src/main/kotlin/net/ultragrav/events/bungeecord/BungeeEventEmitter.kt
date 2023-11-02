package net.ultragrav.events.bungeecord

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Event
import net.md_5.bungee.api.plugin.PluginManager
import net.md_5.bungee.event.EventBus
import net.ultragrav.events.EventEmitter
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object BungeeEventEmitter : EventEmitter<Event, BungeeEventEmitter.BungeeEventListener<*>>(Event::class.java) {
    private class CachedCall(val calls: List<BungeeEventListener<*>>, val maxPriority: Byte)

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
        private val bungeeClazz: Class<out Event>,
        executor: (T) -> Unit
    ) : EventListener<T>(BungeeEventEmitter, identifier, bungeeClazz, executor) {
        internal var priority: Byte = 0
        fun priority(priority: Byte): BungeeEventListener<T> {
            this.priority = priority
            register(bungeeClazz, priority)
            return this
        }
    }

    override fun getCalls(event: Event): List<BungeeEventListener<*>> {
        return super.getCalls(event).map { it as BungeeEventListener }
    }

    override fun <T : Event> createListener(
        identifier: String,
        clazz: Class<T>,
        listener: (T) -> Unit
    ): BungeeEventListener<T> {
        return BungeeEventListener(identifier, clazz, listener)
    }

    private fun register(clazz: Class<out Event>, priority: Byte) {
        if (Modifier.isAbstract(clazz.modifiers)) return

        processedEventsLock.withLock {
            if (!processedEvents.computeIfAbsent(clazz) { HashSet() }.add(priority)) return
            processedEvents[clazz]!!.add(priority)
        }

        internalRegister(clazz, priority)
    }

    private val eventBus: EventBus
    private val internalRegister: (Class<*>, Byte) -> Unit

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
        val bake = EventBus::class.java.getDeclaredMethod("bakeHandlers", Class::class.java).also {
            it.isAccessible = true
        }

        internalRegister = { clazz, priority ->
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