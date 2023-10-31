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

object BukkitEventEmitter : EventEmitter<Event>(Event::class.java), Listener {
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
        val priority: EventPriority,
        executor: (T) -> Unit
    ) : EventListener<T>(identifier, clazz, executor)

    override fun <T : Event> on(clazz: Class<T>, identifier: String, listener: (T) -> Unit) {
        on(clazz, identifier, EventPriority.NORMAL, listener)
    }

    inline fun <reified T : Event> on(identifier: String, priority: EventPriority, noinline listener: (T) -> Unit) {
        on(T::class.java, identifier, priority, listener)
    }

    fun <T : Event> on(clazz: Class<T>, identifier: String, priority: EventPriority, listener: (T) -> Unit) {
        addListener(BukkitEventListener(identifier, clazz, priority, listener))

        register(clazz)
    }

    private val callCache = mutableMapOf<Event, List<BukkitEventListener<out Any>>>()
    private fun listener(priority: EventPriority): RegisteredListener {
        return RegisteredListener(this, { _: Listener, event: Event ->
            val calls: List<BukkitEventListener<out Any>> = callCache[event] ?: getCalls(event).also {
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

    override fun getCalls(event: Event): List<BukkitEventListener<out Any>> {
        return super.getCalls(event).map { it as BukkitEventListener<out Any> }
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