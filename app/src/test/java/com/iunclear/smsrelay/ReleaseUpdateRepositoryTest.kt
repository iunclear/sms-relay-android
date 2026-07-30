package com.iunclear.smsrelay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateRepositoryTest {
    @Test
    fun detectsNewerSemanticVersions() {
        assertTrue(ReleaseUpdateRepository.isVersionNewer("1.2.0", "1.1.9"))
        assertTrue(ReleaseUpdateRepository.isVersionNewer("1.10.0", "1.9.9"))
        assertTrue(ReleaseUpdateRepository.isVersionNewer("2.0", "1.99.99"))
    }

    @Test
    fun ignoresCurrentOrOlderVersions() {
        assertFalse(ReleaseUpdateRepository.isVersionNewer("1.2.0", "1.2.0"))
        assertFalse(ReleaseUpdateRepository.isVersionNewer("1.1.9", "1.2.0"))
        assertFalse(ReleaseUpdateRepository.isVersionNewer("1.2.0-beta", "1.2.0"))
    }
}
