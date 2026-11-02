package com.yang.player.lyrics

object LyricEffects {
    fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    fun heldRange(line: LyricLine, segment: LyricSegment): IntRange? {
        if (segment.endMs - segment.startMs < 900) return null
        var from = segment.from.coerceIn(0, line.text.length)
        var until = segment.until.coerceIn(from, line.text.length)
        while (from < until && line.text[from].isWhitespace()) from++
        while (until > from && line.text[until - 1].isWhitespace()) until--
        return if (from < until) from until until else null
    }

    fun heldGlow(segment: LyricSegment, positionMs: Long): Float {
        val duration = segment.endMs - segment.startMs
        if (duration < 900) return 0f
        val elapsed = positionMs - segment.startMs
        return smooth((elapsed - 650).toFloat() / 350) * smooth((duration - elapsed).toFloat() / 450)
    }
}
