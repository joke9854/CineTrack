package com.cinetrack.data.repository

import org.junit.Assert.*
import org.junit.Test

class BoundedLruCacheTest {
    @Test fun evictsLeastRecentlyReadEntry() {
        val cache = BoundedLruCache<String, String>(2)
        cache["a"] = "A"
        cache["b"] = "B"
        assertEquals("A", cache["a"])
        cache["c"] = "C"
        assertNull(cache["b"])
        assertEquals("A", cache["a"])
        assertEquals("C", cache["c"])
    }
    @Test fun replacingDoesNotEvictAnotherEntry() {
        val cache = BoundedLruCache<Int, String>(2)
        cache[1] = "old"
        cache[2] = "other"
        cache[1] = "new"
        assertEquals("new", cache[1])
        assertEquals("other", cache[2])
    }
}
