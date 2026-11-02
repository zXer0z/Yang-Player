package com.yang.player.lyrics

data class LyricSegment(val startMs: Long, val endMs: Long, val from: Int, val until: Int)
data class LyricLine(val text: String, val startMs: Long? = null, val endMs: Long? = null, val segments: List<LyricSegment> = emptyList())
data class InstrumentalBreak(val startMs: Long, val endMs: Long, val nextLineIndex: Int)
data class LyricsDocument(val lines: List<LyricLine>) {
    val synchronized = lines.any { it.startMs != null }
    val enhanced = lines.any { it.segments.isNotEmpty() }
    // Only explicit cue ends or the end of a timed word can establish silence.
    // Plain LRC without such markers cannot tell us when the vocal actually ends.
    val instrumentalBreaks: List<InstrumentalBreak> = buildList {
        var vocalEnd = 0L
        lines.forEachIndexed { index, line ->
            val start = line.startMs ?: return@forEachIndexed
            if (start - vocalEnd >= 5000) add(InstrumentalBreak(vocalEnd, start, index))
            val wordEnd = line.segments.maxOfOrNull { it.endMs }
            val end = if (wordEnd != null) minOf(wordEnd, line.endMs ?: wordEnd) else line.endMs ?: start
            vocalEnd = maxOf(vocalEnd, end, start)
        }
    }

    fun instrumentalBreakAt(positionMs: Long): InstrumentalBreak? = instrumentalBreaks.firstOrNull {
        positionMs >= it.startMs && positionMs < it.endMs
    }
    fun activeIndex(positionMs: Long): Int {
        if (!synchronized) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if ((lines[middle].startMs ?: Long.MAX_VALUE) <= positionMs) {
                result = middle
                low = middle + 1
            } else high = middle - 1
        }
        return result.takeIf { it >= 0 && positionMs < (lines[it].endMs ?: Long.MAX_VALUE) } ?: -1
    }
}
