package com.yang.player.lyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object LyricsParser {
    private val lineTime = Regex("\\[(\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?)\\]")
    private val wordTime = Regex("<(\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?)>")
    private val metadata = Regex("^\\[(?:ar|al|ti|by|re|ve|length|offset):.*]$", RegexOption.IGNORE_CASE)

    fun parse(input: String, durationMs: Long = 0): LyricsDocument? {
        val text = input.trim().removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return null
        val lines = when {
            Regex("<(?:\\w+:)?tt(?:\\s|>)").containsMatchIn(text) -> parseTtml(text)
            lineTime.containsMatchIn(text) -> parseLrc(text)
            text.contains("-->") -> parseSrt(text)
            else -> text.lines().filter { it.isNotBlank() && !metadata.matches(it.trim()) }.map { LyricLine(it.trim()) }
        }
        return normalize(lines, durationMs)
    }

    private fun lrcTime(value: String): Long? {
        val match = Regex("(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?").matchEntire(value) ?: return null
        val seconds = match.groupValues[2].toIntOrNull() ?: return null
        if (seconds >= 60) return null
        return (match.groupValues[1].toLong() * 60 + seconds) * 1000 +
            match.groupValues[3].padEnd(3, '0').take(3).toLong()
    }

    private fun parseLrc(text: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lines().flatMap { raw ->
            val stamps = lineTime.findAll(raw).toList()
            if (stamps.isEmpty() || stamps.first().range.first != 0) return@flatMap emptyList()
            var cursor = 0
            val prefix = stamps.takeWhile {
                val adjacent = it.range.first == cursor
                if (adjacent) cursor = it.range.last + 1
                adjacent
            }
            val contentStart = prefix.lastOrNull()?.range?.last?.plus(1) ?: return@flatMap emptyList()
            val content = raw.substring(contentStart)
            val words = wordTime.findAll(content).toList()
            val clean = StringBuilder()
            val segments = mutableListOf<LyricSegment>()
            val firstStamp = lrcTime(prefix.first().groupValues[1]) ?: return@flatMap emptyList()
            if (words.isEmpty()) clean.append(content) else {
                if (words.first().range.first > 0) {
                    clean.append(content.substring(0, words.first().range.first))
                    if (clean.isNotBlank()) segments.add(LyricSegment(firstStamp, lrcTime(words.first().groupValues[1]) ?: firstStamp, 0, clean.length))
                }
                words.forEachIndexed { index, mark ->
                    val part = content.substring(mark.range.last + 1, words.getOrNull(index + 1)?.range?.first ?: content.length)
                    val from = clean.length
                    clean.append(part)
                    if (part.isNotEmpty()) {
                        val start = lrcTime(mark.groupValues[1]) ?: firstStamp
                        segments.add(LyricSegment(start, words.getOrNull(index + 1)?.let { lrcTime(it.groupValues[1]) } ?: -1, from, clean.length))
                    }
                }
            }
            prefix.mapNotNull { stamp ->
                val start = lrcTime(stamp.groupValues[1]) ?: return@mapNotNull null
                val delta = start - firstStamp - offset
                LyricLine(clean.toString(), (start - offset).coerceAtLeast(0), segments = segments.map {
                    it.copy(startMs = (it.startMs + delta).coerceAtLeast(0), endMs = if (it.endMs >= 0) (it.endMs + delta).coerceAtLeast(0) else -1)
                })
            }
        }
    }

    private fun clock(value: String): Long? {
        val time = value.trim().replace(',', '.')
        Regex("([\\d.]+)(ms|h|m|s)").matchEntire(time)?.let {
            val factor = when (it.groupValues[2]) { "h" -> 3600000; "m" -> 60000; "ms" -> 1; else -> 1000 }
            return (it.groupValues[1].toDoubleOrNull()?.times(factor))?.toLong()
        }
        val parts = time.split(':')
        if (parts.size !in 2..3) return null
        val values = parts.map { it.toDoubleOrNull() ?: return null }
        return (values.fold(0.0) { total, part -> total * 60 + part } * 1000).toLong()
    }

    private fun parseSrt(text: String): List<LyricLine> = text.split(Regex("\n\\s*\n")).mapNotNull { block ->
        val lines = block.lines()
        val timeIndex = lines.indexOfFirst { it.contains("-->") }
        if (timeIndex < 0) return@mapNotNull null
        val stamps = lines[timeIndex].split("-->")
        val start = clock(stamps[0]) ?: return@mapNotNull null
        val end = clock(stamps.getOrNull(1)?.trim()?.substringBefore(' ') ?: "") ?: return@mapNotNull null
        LyricLine(lines.drop(timeIndex + 1).joinToString("\n").replace(Regex("<[^>]+>"), ""), start, end)
    }

    private fun parseTtml(text: String): List<LyricLine> {
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "XML no admitido" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        fun resolve(value: String, parent: Long): Long? = clock(value)?.let {
            // Apple-style files commonly repeat absolute clock times inside p;
            // offset expressions (e.g. 250ms) remain relative to their parent.
            if (value.contains(':') && it >= parent) it else parent + it
        }
        fun startOf(node: Element): Long {
            val parent = (node.parentNode as? Element)?.let(::startOf) ?: 0L
            return resolve(node.getAttribute("begin"), parent) ?: parent
        }
        return (0 until paragraphs.length).map { index ->
            val paragraph = paragraphs.item(index) as Element
            val start = startOf(paragraph)
            val parentStart = (paragraph.parentNode as? Element)?.let(::startOf) ?: 0L
            val end = resolve(paragraph.getAttribute("end"), parentStart)
                ?: clock(paragraph.getAttribute("dur"))?.plus(start)
            val content = StringBuilder()
            val segments = mutableListOf<LyricSegment>()
            fun walk(node: Node, begin: Long, finish: Long?, timed: Boolean) {
                if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                    val part = node.nodeValue.replace(Regex("\\s+"), " ")
                    val from = content.length
                    content.append(part)
                    if (timed && part.isNotBlank()) segments.add(LyricSegment(begin, finish ?: -1, from, content.length))
                } else if (node is Element) {
                    if (node.localName == "br") { content.append('\n'); return }
                    val ownBegin = resolve(node.getAttribute("begin"), begin)
                    val ownEnd = resolve(node.getAttribute("end"), begin)
                        ?: clock(node.getAttribute("dur"))?.plus(ownBegin ?: begin) ?: finish
                    for (i in 0 until node.childNodes.length) walk(node.childNodes.item(i), ownBegin ?: begin, ownEnd, timed || ownBegin != null)
                }
            }
            for (i in 0 until paragraph.childNodes.length) walk(paragraph.childNodes.item(i), start, end, false)
            LyricLine(content.toString(), start, end, segments)
        }
    }

    private fun normalize(input: List<LyricLine>, durationMs: Long): LyricsDocument? {
        if (input.isEmpty()) return null
        val sorted = if (input.any { it.startMs != null }) input.sortedBy { it.startMs ?: 0 } else input
        val lines = sorted.mapIndexedNotNull { index, line ->
            val firstText = line.text.indexOfFirst { !it.isWhitespace() }
            if (firstText < 0) return@mapIndexedNotNull null
            val voicePrefix = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE).find(line.text.substring(firstText))?.value?.length ?: 0
            val leading = firstText + voicePrefix
            val clean = line.text.substring(leading).trimEnd()
            if (clean.isBlank()) return@mapIndexedNotNull null
            val next = sorted.drop(index + 1).firstOrNull { (it.startMs ?: -1) > (line.startMs ?: -1) }?.startMs
            val end = line.endMs ?: next ?: if (line.startMs != null) maxOf(durationMs, line.startMs + 5000) else null
            val orderedSegments = line.segments.sortedBy { it.startMs }
            val segments = orderedSegments.mapIndexedNotNull { segmentIndex, segment ->
                val from = (segment.from - leading).coerceIn(0, clean.length)
                val until = (segment.until - leading).coerceIn(0, clean.length)
                if (from >= until) return@mapIndexedNotNull null
                val finish = segment.endMs.takeIf { it >= segment.startMs }
                    ?: orderedSegments.getOrNull(segmentIndex + 1)?.startMs ?: end ?: (segment.startMs + 1000)
                segment.copy(endMs = maxOf(segment.startMs, finish), from = from, until = until)
            }
            line.copy(text = clean, endMs = end, segments = segments)
        }
        return lines.takeIf { it.isNotEmpty() }?.let(::LyricsDocument)
    }

    fun parseId3Lyrics(data: ByteArray, synchronized: Boolean, durationMs: Long): LyricsDocument? {
        if (data.size < if (synchronized) 7 else 4) return null
        val encoding = data[0].toInt() and 255
        if (encoding !in 0..3) return null
        var charset = when (encoding) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
        val step = if (encoding == 1 || encoding == 2) 2 else 1
        fun decode(from: Int, until: Int): String {
            if (encoding == 1 && until - from >= 2) {
                if (data[from] == 0xFF.toByte() && data[from + 1] == 0xFE.toByte()) charset = Charsets.UTF_16LE
                if (data[from] == 0xFE.toByte() && data[from + 1] == 0xFF.toByte()) charset = Charsets.UTF_16BE
            }
            return String(data, from, until - from, charset).removePrefix("\uFEFF").trimEnd('\u0000')
        }
        fun terminator(from: Int): Int {
            var at = from
            while (at + step <= data.size) {
                if (data[at] == 0.toByte() && (step == 1 || data[at + 1] == 0.toByte())) return at
                at += step
            }
            return data.size
        }
        var at = if (synchronized) 6 else 4
        val descriptionEnd = terminator(at)
        decode(at, descriptionEnd) // Captures UTF-16 byte order for subsequent strings.
        at = descriptionEnd + step
        if (at >= data.size) return null
        if (!synchronized) return parse(decode(at, data.size), durationMs)
        if (data[4].toInt() != 2) return null // MPEG-frame timestamps need a frame index, not a guessed conversion.
        val chunks = mutableListOf<Pair<Long, String>>()
        while (at < data.size) {
            val end = terminator(at)
            if (end + step + 4 > data.size) break
            val part = decode(at, end)
            at = end + step
            var time = 0L
            repeat(4) { time = (time shl 8) or (data[at++].toLong() and 255) }
            chunks.add(time to part)
        }
        if (chunks.isEmpty()) return null
        val lines = mutableListOf<LyricLine>()
        val content = StringBuilder()
        val segments = mutableListOf<LyricSegment>()
        var start = chunks.first().first
        fun flush() {
            if (content.isNotEmpty()) lines.add(LyricLine(content.toString(), start, segments = segments.toList()))
            content.clear()
            segments.clear()
        }
        chunks.forEachIndexed { index, (time, raw) ->
            val pieces = raw.replace("\r", "").split('\n')
            pieces.forEachIndexed { pieceIndex, part ->
                if (pieceIndex > 0) flush()
                if (part.isNotEmpty()) {
                    if (content.isEmpty()) start = time
                    val from = content.length
                    content.append(part)
                    segments.add(LyricSegment(time, chunks.getOrNull(index + 1)?.first ?: -1, from, content.length))
                }
            }
        }
        flush()
        return normalize(lines, durationMs)
    }
}
