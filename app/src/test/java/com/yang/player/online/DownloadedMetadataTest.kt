package com.yang.player.online

import com.yang.player.data.MetadataMatch
import com.yang.player.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedMetadataTest {
    private val song = Song(
        id = "yt:sample",
        title = "Me Rehúso",
        artist = "Danny Ocean • 80 M de visualizaciones",
        album = "",
        durationMs = 205_000,
        mediaUri = "yangyoutube://watch/sample"
    )
    private val match = MetadataMatch("catalog-1", "Me Rehuso", "Danny Ocean", "54+1", 206_000)

    @Test fun ignoresAccentsAndViewCount() {
        assertTrue(matchesDownloadedTrack(song, match))
    }

    @Test fun acceptsAnOfficialTitleWithArtistPrefix() {
        val video = song.copy(title = "Danny Ocean - Me Rehúso (Video Oficial)", artist = "Label")
        assertTrue(matchesDownloadedTrack(video, match))
    }

    @Test fun avoidsIncorrectArtistOrDuration() {
        assertFalse(matchesDownloadedTrack(song, match.copy(artist = "Other Artist")))
        assertFalse(matchesDownloadedTrack(song, match.copy(durationMs = 210_000)))
    }

    @Test fun stripsEmojiAndPromotionalSuffixes() {
        assertEquals("En la luna y en Marte", cleanDownloadTitle("🌙 En la luna y en Marte (Audio oficial) - Letra 🚀"))
        assertEquals("Rumberos", cleanDownloadArtist("🔥 Rumberos - Topic • 3 M de visualizaciones"))
    }

    @Test fun keepsEditionIdentifiers() {
        for (variant in listOf("Mix", "Radio Edit", "Deluxe Edition")) {
            assertEquals("La luna ($variant)", cleanDownloadTitle("La luna ($variant) [Video oficial] ✨"))
        }
    }

    @Test fun keepsArtistAppendedToTitleAsAMatch() {
        val source = song.copy(title = "Me Rehúso Danny Ocean audio oficial 🔥")
        assertTrue(matchesDownloadedTrack(source, match))
    }

    @Test fun byteRangesCoverEveryByteExactlyOnce() {
        assertEquals(listOf(0L..2L, 3L..5L, 6L..9L), audioDownloadRanges(10))
    }
}
