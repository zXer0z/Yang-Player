package com.yang.player.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricEffectsTest {
    @Test fun `held glow selects only the syllable and excludes the rest of the phrase`() {
        val line = LyricLine("Solo contigo ahora")
        val segment = LyricSegment(1000, 3000, 10, 12)
        val range = LyricEffects.heldRange(line, segment)!!
        assertEquals("go", line.text.substring(range))
        assertFalse(range.contains(5))
        assertFalse(range.contains(13))
    }

    @Test fun `whitespace never becomes part of a glowing span`() {
        val line = LyricLine("  naaa  ")
        val range = LyricEffects.heldRange(line, LyricSegment(0, 2000, 0, 8))!!
        assertEquals("naaa", line.text.substring(range))
        assertNull(LyricEffects.heldRange(LyricLine("   "), LyricSegment(0, 2000, 0, 3)))
    }

    @Test fun `held effects fade in after the reveal and fade out before the syllable ends`() {
        val segment = LyricSegment(1000, 4000, 0, 2)
        assertEquals(0f, LyricEffects.heldGlow(segment, 1000), 0.0001f)
        assertEquals(0f, LyricEffects.heldGlow(segment, 1650), 0.0001f)
        assertTrue(LyricEffects.heldGlow(segment, 1700) < LyricEffects.heldGlow(segment, 1900))
        assertTrue(LyricEffects.heldGlow(segment, 3600) > LyricEffects.heldGlow(segment, 3950))
        assertEquals(0f, LyricEffects.heldGlow(segment, 4000), 0.0001f)
        assertEquals(0f, LyricEffects.heldGlow(segment, 4500), 0.0001f)
    }

    @Test fun `ordinary short words have no held glow`() {
        val segment = LyricSegment(1000, 1500, 0, 4)
        assertNull(LyricEffects.heldRange(LyricLine("Word"), segment))
        assertEquals(0f, LyricEffects.heldGlow(segment, 1200), 0.0001f)
    }
}
