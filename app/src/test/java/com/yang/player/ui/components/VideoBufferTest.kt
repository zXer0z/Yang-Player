package com.yang.player.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBufferTest {
    @Test fun firstFrameDoesNotReplaceBufferedVideo() {
        assertFalse(hasVideoReserve(42_000, 42_000, 100_000))
        assertFalse(hasVideoReserve(42_000, 45_499, 100_000))
        assertTrue(hasVideoReserve(42_000, 45_500, 100_000))
    }

    @Test fun shortRemainderCanFinishWithoutAFullReserve() {
        assertFalse(hasVideoReserve(99_000, 99_300, 100_000))
        assertTrue(hasVideoReserve(99_000, 99_900, 100_000))
    }

    @Test fun unknownDurationStillRequiresReserveFromTheCurrentPosition() {
        assertFalse(hasVideoReserve(42_000, 41_000, -1))
        assertTrue(hasVideoReserve(42_000, 45_500, -1))
    }
}
