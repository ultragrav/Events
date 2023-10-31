package net.ultragrav.events.bukkit

import net.ultragrav.events.EventEmitter
import org.bukkit.World
import org.bukkit.event.Event
import org.bukkit.event.world.WorldEvent
import java.util.*

class WorldEventEmitter private constructor() : EventEmitter<Event>(Event::class.java) {
    override fun register(clazz: Class<out Event>) {
        if (!ALLOWED_EVENTS.any { it.isAssignableFrom(clazz) })
            throw IllegalArgumentException("Class $clazz is not a world event")
    }

    companion object {
        private val ALLOWED_EVENTS = listOf(
            WorldEvent::class.java
        )
        private val emitters = mutableMapOf<UUID, WorldEventEmitter>()

        operator fun invoke(world: World): WorldEventEmitter {
            return emitters.getOrPut(world.uid) { WorldEventEmitter() }
        }

        init {
            BukkitEventEmitter.on<WorldEvent> { emitters[it.world.uid]?.call(it) }
        }
    }
}