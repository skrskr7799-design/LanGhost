package com.langhost.data

/**
 * Scan-scoped registry for asynchronous platform callbacks.
 *
 * Closing the registry prevents late discovery events from registering new
 * callbacks after scan cleanup has started. Releasing is idempotent.
 */
internal class NsdCallbackRegistry<T : Any> {
    private val callbacks = LinkedHashSet<T>()
    private var closed = false

    @Synchronized
    fun track(callback: T): Boolean {
        if (closed) return false
        return callbacks.add(callback)
    }

    /**
     * Atomically records and starts a platform registration relative to close.
     * The registration call runs under the same monitor as closeAndSnapshot(),
     * so cleanup cannot unregister before a racing registration is actually made.
     */
    @Synchronized
    fun trackAndRun(callback: T, register: () -> Unit): Boolean {
        if (closed || !callbacks.add(callback)) return false
        register()
        return true
    }

    @Synchronized
    fun release(callback: T): Boolean = callbacks.remove(callback)

    @Synchronized
    fun closeAndSnapshot(): List<T> {
        closed = true
        return callbacks.toList()
    }

    @Synchronized
    fun isEmpty(): Boolean = callbacks.isEmpty()

    @Synchronized
    fun size(): Int = callbacks.size

    @Synchronized
    fun isClosed(): Boolean = closed
}
