package com.cinetrack.data.repository

/** Access order and reads are synchronized too: reading mutates LRU order. */
internal class BoundedLruCache<K, V>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val entries = object : LinkedHashMap<K, V>(capacity, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
    }
    @Synchronized operator fun get(key: K): V? = entries[key]
    @Synchronized operator fun set(key: K, value: V) { entries[key] = value }
}
