package com.cinetrack

import org.junit.Assert.*
import org.junit.Test

class DeepLinkRoutesTest {
    @Test fun appLinksMapToTabs() {
        assertEquals("progress", DeepLinkRoutes.route("app", "/up-next", emptyList()))
        assertEquals("sync", DeepLinkRoutes.route("app", "/sync", emptyList()))
        assertNull(DeepLinkRoutes.route("app", "/nope", emptyList()))
    }

    @Test fun validDetailAndEpisodeLinksBuildRoutes() {
        assertEquals("detail/TV/1399", DeepLinkRoutes.route("detail", "/tv/1399", listOf("tv", "1399")))
        assertEquals("detail/MOVIE/550", DeepLinkRoutes.route("detail", "/MOVIE/550", listOf("MOVIE", "550")))
        assertEquals("episode/1399/0/2", DeepLinkRoutes.route("episode", "/1399/0/2", listOf("1399", "0", "2")))
    }

    @Test fun malformedLinksFromTheWebAreDropped() {
        assertNull(DeepLinkRoutes.route("detail", "/tv/abc", listOf("tv", "abc")))
        assertNull(DeepLinkRoutes.route("detail", "/person/5", listOf("person", "5")))
        assertNull(DeepLinkRoutes.route("detail", "/tv/-1", listOf("tv", "-1")))
        assertNull(DeepLinkRoutes.route("detail", "/tv", listOf("tv")))
        assertNull(DeepLinkRoutes.route("episode", "/1/x/2", listOf("1", "x", "2")))
        assertNull(DeepLinkRoutes.route("episode", "/1/1/0", listOf("1", "1", "0")))
        assertNull(DeepLinkRoutes.route("simkl", null, emptyList()))
    }
}
