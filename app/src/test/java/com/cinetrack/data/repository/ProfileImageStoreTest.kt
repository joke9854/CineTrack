package com.cinetrack.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileImageStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun eachSaveGetsANewNameAndDropsTheOldFile() {
        val store = ProfileImageStore(folder.root)
        val first = store.write(ProfileImageKind.AVATAR, byteArrayOf(1, 2, 3), now = 1)
        val banner = store.write(ProfileImageKind.BANNER, byteArrayOf(9), now = 1)
        val second = store.write(ProfileImageKind.AVATAR, byteArrayOf(4, 5), now = 2)

        assertNotEquals(first, second)
        assertNull(store.path(first))
        assertArrayEquals(byteArrayOf(4, 5), store.read(second))
        // Another kind is left alone.
        assertArrayEquals(byteArrayOf(9), store.read(banner))
        assertEquals(setOf(second, banner), folder.root.list()!!.toSet())
    }

    @Test
    fun removingAKindDeletesItsFilesOnly() {
        val store = ProfileImageStore(folder.root)
        store.write(ProfileImageKind.AVATAR, byteArrayOf(1), now = 1)
        val banner = store.write(ProfileImageKind.BANNER, byteArrayOf(2), now = 1)

        store.removeExcept(ProfileImageKind.AVATAR, null)

        assertEquals(setOf(banner), folder.root.list()!!.toSet())
        assertNull(store.read(null))
    }
}
