package net.ultragrav.events.bukkit

import net.ultragrav.events.EventEmitter
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerEvent
import java.util.*

class PlayerEventEmitter private constructor() : EventEmitter<Event>(Event::class.java) {
    override fun <T : Event> on(clazz: Class<T>, identifier: String, listener: (T) -> Unit) {
        if (!ALLOWED_EVENTS.any { it.isAssignableFrom(clazz) })
            throw IllegalArgumentException("Class $clazz is not a world event")

        super.on(clazz, identifier, listener)
    }

    companion object {
        private val ALLOWED_EVENTS = listOf(
            PlayerEvent::class.java,
            BlockBreakEvent::class.java,
            BlockPlaceEvent::class.java
        )
        private val emitters = mutableMapOf<UUID, PlayerEventEmitter>()

        operator fun invoke(player: Player): PlayerEventEmitter {
            return emitters.getOrPut(player.uniqueId) { PlayerEventEmitter() }
        }

        init {
            BukkitEventEmitter.on<PlayerEvent> { emitters[it.player.uniqueId]?.call(it) }
            BukkitEventEmitter.on<BlockBreakEvent> { emitters[it.player.uniqueId]?.call(it) }
            BukkitEventEmitter.on<BlockPlaceEvent> { emitters[it.player.uniqueId]?.call(it) }
        }
    }
}