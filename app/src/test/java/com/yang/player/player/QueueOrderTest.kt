package com.yang.player.player

import com.yang.player.model.PlayerState
import com.yang.player.model.Song
import org.junit.Assert.*
import org.junit.Test

class QueueOrderTest {
    private fun song(id: String) = Song(id, id, "Artist", "Album", 200000, "content://music/$id")
    private val songs = listOf(song("A"), song("B"), song("C"), song("D"))
    private fun state(index: Int) = PlayerState(currentSong = songs[index], queue = songs, currentIndex = index,
        currentPositionMs = 45000, isPlaying = true, durationMs = 200000)

    @Test fun `moving the current occurrence preserves song position and playback`() {
        val before = state(1)
        val after = QueueOrder.move(before, 1, 3)
        assertEquals(listOf("A", "C", "D", "B"), after.queue.map { it.id })
        assertEquals(3, after.currentIndex)
        assertSame(before.currentSong, after.currentSong)
        assertEquals(before.currentPositionMs, after.currentPositionMs)
        assertEquals(before.isPlaying, after.isPlaying)
    }

    @Test fun `crossing the current item in either direction adjusts its index`() {
        assertEquals(0, QueueOrder.move(state(1), 0, 3).currentIndex)
        assertEquals(2, QueueOrder.move(state(1), 3, 0).currentIndex)
        assertEquals(1, QueueOrder.move(state(1), 2, 3).currentIndex)
    }

    @Test fun `duplicate songs retain their own queue occurrences`() {
        val repeated = songs[0]
        val before = PlayerState(queue = listOf(repeated, songs[1], repeated, songs[2]), currentSong = repeated, currentIndex = 2)
        val after = QueueOrder.move(before, 0, 3)
        assertEquals(1, after.currentIndex)
        assertEquals(2, after.queue.count { it.id == "A" })
        assertEquals(0, QueueOrder.move(before, 2, 0).currentIndex)
    }

    @Test fun `reordering while paused does not start playback`() {
        val before = state(2).copy(isPlaying = false)
        assertFalse(QueueOrder.move(before, 2, 0).isPlaying)
    }

    @Test fun `invalid and no-op moves leave the original state intact`() {
        val before = state(1)
        assertSame(before, QueueOrder.move(before, -1, 1))
        assertSame(before, QueueOrder.move(before, 1, 99))
        assertSame(before, QueueOrder.move(before, 1, 1))
        assertEquals(PlayerState(), QueueOrder.move(PlayerState(), 0, 0))
    }

    @Test fun `every move preserves exactly the same queue contents and current occurrence`() {
        songs.indices.forEach { current ->
            songs.indices.forEach { from ->
                songs.indices.forEach { to ->
                    val before = state(current)
                    val after = QueueOrder.move(before, from, to)
                    assertEquals(before.queue.map { it.id }.sorted(), after.queue.map { it.id }.sorted())
                    assertSame(before.currentSong, after.queue[after.currentIndex])
                    assertEquals(before.currentPositionMs, after.currentPositionMs)
                }
            }
        }
    }
}
