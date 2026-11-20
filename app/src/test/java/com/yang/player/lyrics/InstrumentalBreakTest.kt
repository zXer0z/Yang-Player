package com.yang.player.lyrics

import org.junit.Assert.*
import org.junit.Test

class InstrumentalBreakTest {
    @Test fun `long intro counts down to the first vocal`() {
        val lyrics = LyricsParser.parse("[00:09]First line", 30_000)!!
        assertEquals(InstrumentalBreak(0, 9000, 0), lyrics.instrumentalBreaks.single())
        assertNull(lyrics.instrumentalBreakAt(9000))
    }

    @Test fun `enhanced final word establishes the actual start of the interlude`() {
        val lyrics = LyricsParser.parse("[00:01]<00:01>Hello<00:03>\n[00:15]<00:15>Again<00:17>", 30_000)!!
        assertEquals(InstrumentalBreak(3000, 15000, 1), lyrics.instrumentalBreaks.single())
        assertNull(lyrics.instrumentalBreakAt(2999))
        assertNotNull(lyrics.instrumentalBreakAt(3000))
        assertNull(lyrics.instrumentalBreakAt(15000))
    }

    @Test fun `simple LRC does not mistake a long sung line for an instrumental`() {
        val lyrics = LyricsParser.parse("[00:00]Long vocal\n[00:20]Next line", 30_000)!!
        assertTrue(lyrics.instrumentalBreaks.isEmpty())
    }

    @Test fun `empty timestamp cues allow interludes in ordinary LRC`() {
        val lyrics = LyricsParser.parse("[00:00]One\n[00:03]\n[00:15]Two", 30_000)!!
        assertEquals(InstrumentalBreak(3000, 15000, 1), lyrics.instrumentalBreaks.single())
    }

    @Test fun `held syllables and short breaths do not trigger dots`() {
        val lyrics = LyricsParser.parse("[00:00]<00:00>Held<00:12>\n[00:15]<00:15>Next<00:17>", 30_000)!!
        assertTrue(lyrics.instrumentalBreaks.isEmpty())
    }

    @Test fun `overlapping voices extend the vocal range before an interlude`() {
        val lyrics = LyricsDocument(listOf(
            LyricLine("Voice A", 0, 3000),
            LyricLine("Voice B", 2000, 9000),
            LyricLine("Next", 15000, 18000)
        ))
        assertEquals(InstrumentalBreak(9000, 15000, 2), lyrics.instrumentalBreaks.single())
    }

    @Test fun `plain text and the final outro have no invented countdown`() {
        assertTrue(LyricsParser.parse("Plain lyrics")!!.instrumentalBreaks.isEmpty())
        val lyrics = LyricsParser.parse("[00:00]<00:00>Final word<00:02>", 60_000)!!
        assertTrue(lyrics.instrumentalBreaks.isEmpty())
    }
}
