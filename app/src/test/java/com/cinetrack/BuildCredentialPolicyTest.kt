package com.cinetrack

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildCredentialPolicyTest {
    @Test fun personalKeysAreNeverBundled() {
        assertEquals("", BuildConfig.TMDB_API_TOKEN)
        assertEquals("", BuildConfig.MDBLIST_API_KEY)
    }
}
