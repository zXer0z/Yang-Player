package com.yang.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    @Test fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerYangVersion("1.10.0", "1.9.9"))
        assertTrue(isNewerYangVersion("2.0.0", "1.99.99"))
        assertFalse(isNewerYangVersion("1.0.13", "1.0.14"))
        assertFalse(isNewerYangVersion("1.0.14", "1.0.14"))
    }

    @Test fun rejectsMalformedAndPrereleaseTags() {
        for (tag in listOf("2.0.0-beta", "2.0", "2.0.0/../junk", "99999999999.0.0", "02.0.0")) {
            assertFalse(isNewerYangVersion(tag, "1.0.14"))
        }
    }
}
