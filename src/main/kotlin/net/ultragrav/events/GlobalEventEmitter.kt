package net.ultragrav.events

/**
 * An event emitter that emits events for all classes.
 */
object GlobalEventEmitter : NormalEventEmitter<Any>(Any::class.java)