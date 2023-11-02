package net.ultragrav.events.bukkit

import net.ultragrav.events.EventEmitter
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object BukkitEventEmitter : EventEmitter<Event, BukkitEventEmitter.BukkitEventListener<out Event>>(Event::class.java), Listener {
    private val processedEvents: MutableSet<Class<*>> = HashSet()
    private val processedEventsLock = ReentrantLock()

    private lateinit var registeredListeners: List<RegisteredListener>
    private lateinit var plugin: Plugin
    fun init(plugin: Plugin) {
        BukkitEventEmitter.plugin = plugin

        registeredListeners = listOf(
            listener(EventPriority.LOWEST),
            listener(EventPriority.LOW),
            listener(EventPriority.NORMAL),
            listener(EventPriority.HIGH),
            listener(EventPriority.HIGHEST),
            listener(EventPriority.MONITOR)
        )
    }

    class BukkitEventListener<T>(
        identifier: String,
        clazz: Class<T>,
        executor: (T) -> Unit
    ) : EventListener<T>(BukkitEventEmitter, identifier, clazz, executor) {
        internal var priority: EventPriority = EventPriority.NORMAL
        fun priority(priority: EventPriority): BukkitEventListener<T> {
            this.priority = priority
            return this
        }
    }

    private val callCache = mutableMapOf<Event, List<BukkitEventListener<*>>>()
    private fun listener(priority: EventPriority): RegisteredListener {
        return RegisteredListener(this, { _: Listener, event: Event ->
            val calls: List<BukkitEventListener<*>> = callCache[event] ?: getCalls(event).also {
                callCache[event] = it
            }
            calls.forEach {
                if (it.priority == priority) it.call(event)
            }
            if (priority == EventPriority.MONITOR) {
                callCache.remove(event)
            }
        }, priority, plugin, false)
    }

    override fun getCalls(event: Event): List<BukkitEventListener<*>> {
        return super.getCalls(event).map { it as BukkitEventListener<out Any> }
    }

    override fun <T : Event> createListener(
        identifier: String,
        clazz: Class<T>,
        listener: (T) -> Unit
    ): BukkitEventListener<out Event> {
        return BukkitEventListener(identifier, clazz, listener)
    }

    override fun <T : Event> on(
        clazz: Class<T>,
        identifier: String,
        listener: (T) -> Unit
    ): BukkitEventListener<out Event> {
        register(clazz)
        return super.on(clazz, identifier, listener)
    }

    private fun register(clazz: Class<out Event>) {
        if (!Event::class.java.isAssignableFrom(clazz)) return

        processedEventsLock.withLock {
            if (!processedEvents.add(clazz)) return
            processedEvents.add(clazz)
        }

        try {
            val method: Method = clazz.getMethod("getHandlerList")
            synchronized(this) {
                val list = method.invoke(null) as HandlerList
                val listeners = listOf(*list.registeredListeners)
                if (!listeners.contains(registeredListeners[0])) {
                    for (listener in registeredListeners) {
                        list.register(listener)
                    }
                }
            }
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        }
    }
}