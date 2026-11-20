package com.yang.player.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class LyricsParserTest {
    @Test fun `line timestamps support centiseconds milliseconds and hour-long tracks`() {
        val result = LyricsParser.parse("[ar:Artist]\n[00:01.25]One\n[62:03.456]Two", 4_000_000)!!
        assertEquals(1250L, result.lines[0].startMs)
        assertEquals(3_723_456L, result.lines[1].startMs)
        assertFalse(result.enhanced)
        assertEquals(-1, result.activeIndex(1249))
        assertEquals(0, result.activeIndex(1250))
    }

    @Test fun `repeated timestamps and offsets retain the same lyric`() {
        val result = LyricsParser.parse("[offset:250]\n[00:01.00][00:03.00]Repeat", 10_000)!!
        assertEquals(listOf(750L, 2750L), result.lines.map { it.startMs })
        assertEquals(listOf("Repeat", "Repeat"), result.lines.map { it.text })
    }

    @Test fun `paired word end and start tags preserve real gaps and remove voice tags`() {
        val result = LyricsParser.parse("[00:01.000]v1:<00:01.000>Hello <00:01.400><00:01.700>world<00:03.200>", 8000)!!
        assertTrue(result.enhanced)
        val line = result.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(2, line.segments.size)
        assertEquals(1400L, line.segments[0].endMs)
        assertEquals(1700L, line.segments[1].startMs)
        assertEquals(3200L, line.segments[1].endMs)
        assertEquals("world", line.text.substring(line.segments[1].from, line.segments[1].until))
    }

    @Test fun `syllables remain a single written word with separate timing`() {
        val result = LyricsParser.parse("[00:16.940]v1:<00:16.940>ilu<00:17.855><00:17.855>mi<00:18.572><00:18.572>na<00:19.961>", 30_000)!!
        val line = result.lines.single()
        assertEquals("ilumina", line.text)
        assertEquals(listOf("ilu", "mi", "na"), line.segments.map { line.text.substring(it.from, it.until) })
        assertEquals(1389L, line.segments.last().endMs - line.segments.last().startMs)
    }

    @Test fun `empty timed cues end the previous line during instrumental gaps`() {
        val result = LyricsParser.parse("[00:01]One\n[00:02]\n[00:05]Two", 9000)!!
        assertEquals(2000L, result.lines.first().endMs)
        assertEquals(-1, result.activeIndex(3000))
        assertEquals(1, result.activeIndex(5000))
    }

    @Test fun `plain lyrics do not invent synchronization`() {
        val result = LyricsParser.parse("First line\n\nSecond line")!!
        assertFalse(result.synchronized)
        assertFalse(result.enhanced)
        assertEquals(-1, result.activeIndex(5000))
        assertNull(LyricsParser.parse(" \n"))
    }

    @Test fun `SRT preserves cue end and line breaks`() {
        val result = LyricsParser.parse("1\n00:00:01,200 --> 00:00:03,000\nOne\nTwo\n\n2\n00:00:04,000 --> 00:00:05,000\nThree")!!
        assertEquals("One\nTwo", result.lines[0].text)
        assertEquals(1200L, result.lines[0].startMs)
        assertEquals(-1, result.activeIndex(3500))
    }

    @Test fun `TTML reads absolute word timing and XML entities`() {
        val result = LyricsParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:05.000" end="00:00:08.000"><span begin="00:00:05.000" end="00:00:06.000">You &amp; </span><span begin="00:00:06.000" end="00:00:08.000">me</span></p></div></body></tt>""")!!
        assertEquals("You & me", result.lines.single().text)
        assertEquals(5000L, result.lines.single().segments.first().startMs)
        assertEquals(8000L, result.lines.single().segments.last().endMs)
    }

    @Test fun `TTML relative offsets include parent timing`() {
        val result = LyricsParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div begin="5s"><p begin="1s" dur="4s"><span begin="500ms" dur="1s">Hello</span></p></div></body></tt>""")!!
        assertEquals(6000L, result.lines.single().startMs)
        assertEquals(6500L, result.lines.single().segments.single().startMs)
        assertEquals(7500L, result.lines.single().segments.single().endMs)
    }

    @Test fun `USLT decodes UTF16 text after its description`() {
        val bytes = byteArrayOf(1) + "eng".toByteArray() + "Description".toByteArray(Charsets.UTF_16) + byteArrayOf(0, 0) +
            "[00:01.00]Canción".toByteArray(Charsets.UTF_16)
        assertEquals("Canción", LyricsParser.parseId3Lyrics(bytes, false, 9000)!!.lines.single().text)
    }

    @Test fun `SYLT uses millisecond timestamps and word boundaries`() {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(3, 101, 110, 103, 2, 1, 0))
        listOf(1000 to "Hello ", 1500 to "world", 3000 to "\nNext").forEach { (time, text) ->
            output.write(text.toByteArray())
            output.write(0)
            output.write(byteArrayOf((time ushr 24).toByte(), (time ushr 16).toByte(), (time ushr 8).toByte(), time.toByte()))
        }
        val result = LyricsParser.parseId3Lyrics(output.toByteArray(), true, 9000)!!
        assertEquals(listOf("Hello world", "Next"), result.lines.map { it.text })
        assertEquals(1000L, result.lines.first().startMs)
        assertEquals(1500L, result.lines.first().segments.last().startMs)
        val unsupported = output.toByteArray().also { it[4] = 1 }
        assertNull(LyricsParser.parseId3Lyrics(unsupported, true, 9000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `external XML entities are rejected`() {
        LyricsParser.parse("""<!DOCTYPE tt [<!ENTITY x SYSTEM "file:///secret">]><tt xmlns="http://www.w3.org/ns/ttml"><body><p>&x;</p></body></tt>""")
    }
}
