package net.ultragrav.events

/**
 * An event emitter that emits events for all classes.
 */
object GlobalEventEmitter : EventEmitter<Any, EventEmitter.EventListener<*>>(Any::class.java)