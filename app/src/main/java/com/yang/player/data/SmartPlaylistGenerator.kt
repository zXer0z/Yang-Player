package com.yang.player.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.yang.player.model.AudioFormat
import com.yang.player.model.Song

enum class SmartCategory(val label: String) {
    ALL("Todas"),
    USER("Mis Playlists"),
    MOOD("Emociones"),
    RHYTHM("Ritmo"),
    GENRE("Géneros")
}

data class UnifiedPlaylist(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val songs: List<Song>,
    val category: SmartCategory,
    val isSmart: Boolean,
    val gradientColors: List<Color>,
    val icon: ImageVector,
    val coverArtworks: List<String> = emptyList()
) {
    val totalDurationFormatted: String = run {
        val totalSec = songs.sumOf { it.durationMs / 1000 }.coerceAtLeast(0)
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        if (hours > 0) "${hours} h ${mins} min" else "${mins} min"
    }
}

object SmartPlaylistGenerator {

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it, ignoreCase = true) }

    private fun Song.textCorpus(): String =
        "$title $artist $album $albumArtist $genre $dataPath".lowercase()

    private fun Song.containsAny(vararg terms: String): Boolean =
        textCorpus().containsAny(*terms)

    private fun Song.matchScore(vararg terms: String): Int {
        val corpus = textCorpus()
        var score = 0
        for (term in terms) {
            if (genre.contains(term, ignoreCase = true)) score += 8
            if (artist.contains(term, ignoreCase = true)) score += 6
            if (title.contains(term, ignoreCase = true)) score += 5
            if (album.contains(term, ignoreCase = true)) score += 4
            if (corpus.contains(term)) score += 2
        }
        return score
    }

    fun generateSmartPlaylists(allSongs: List<Song>): List<UnifiedPlaylist> {
        if (allSongs.isEmpty()) return emptyList()

        val playlists = mutableListOf<UnifiedPlaylist>()

        // ---------------------------------------------------------
        // 1. EMOCIONES (Moods)
        // ---------------------------------------------------------

        // A. Euforia & Adrenalina
        val euphoriaTerms = arrayOf(
            "euphoria", "euphoric", "energy", "energetic", "hype", "pump", "power", "rave", "speed",
            "hard", "fire", "fuego", "fuerza", "adrenalina", "burn", "intense", "overdrive", "extreme",
            "turbo", "bounce", "drop", "bass", "festival", "anthem", "club", "party", "remix", "beat",
            "edm", "dance", "electronic", "electro", "house", "techno", "dubstep", "trance", "hardstyle",
            "metal", "heavy metal", "rock", "hard rock", "dnb", "drum and bass", "trap", "vicetone",
            "garrix", "calvin", "avicii", "guetta", "skrillex", "metallica", "ac/dc", "linkin", "prodigy"
        )
        val euphoriaScores = allSongs.map { song ->
            var s = song.matchScore(*euphoriaTerms)
            if (song.durationMs in 100_000..230_000) s += 2
            if (song.format == AudioFormat.FLAC || song.sampleRateHz >= 48000) s += 1
            song to s
        }
        val euphoriaTracks = euphoriaScores.filter { it.second >= 3 }
            .sortedByDescending { it.second }
            .map { it.first }
            .ifEmpty {
                euphoriaScores.sortedByDescending { it.second }.take(12).map { it.first }
            }
        if (euphoriaTracks.isNotEmpty()) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_mood_euphoria",
                    name = "Euforia",
                    subtitle = "${euphoriaTracks.size} canciones",
                    description = "Beats intensos y energía para motivarte.",
                    songs = euphoriaTracks,
                    category = SmartCategory.MOOD,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFFFF0844), Color(0xFFFFB199)),
                    icon = Icons.Rounded.Bolt,
                    coverArtworks = euphoriaTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // B. Alegría & Buenas Vibras
        val joyTerms = arrayOf(
            "happy", "happiness", "joy", "good", "vibes", "vibe", "sun", "sunny", "summer", "smile",
            "love", "feel good", "celebrate", "party", "dance", "groove", "fun", "bright", "cheer",
            "paradise", "golden", "alegria", "alegría", "feliz", "fiesta", "sonrisa", "vida", "sol",
            "verano", "playa", "bailar", "gozar", "brillo", "luz", "optimismo", "pop", "disco", "funk",
            "latin", "daft punk", "bruno mars", "pharrell", "dua lipa", "abba", "michael jackson"
        )
        val joyScores = allSongs.map { song ->
            var s = song.matchScore(*joyTerms)
            if (song.containsAny("sun", "summer", "love", "amor", "dance", "party", "feliz")) s += 4
            song to s
        }
        val joyTracks = joyScores.filter { it.second >= 3 }
            .sortedByDescending { it.second }
            .map { it.first }
            .ifEmpty {
                joyScores.sortedByDescending { it.second }.take(12).map { it.first }
            }
        if (joyTracks.isNotEmpty()) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_mood_joy",
                    name = "Alegría",
                    subtitle = "${joyTracks.size} canciones",
                    description = "Melodías positivas y ritmo para iluminar el día.",
                    songs = joyTracks,
                    category = SmartCategory.MOOD,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFFFF9900), Color(0xFFFF5E62)),
                    icon = Icons.Rounded.WbSunny,
                    coverArtworks = joyTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // C. Melancolía & Nostalgia
        val melancholyTerms = arrayOf(
            "sad", "sadness", "sorrow", "tears", "cry", "crying", "hurt", "broken", "heartbreak",
            "alone", "lonely", "miss", "missing", "goodbye", "farewell", "dark", "darkness", "memories",
            "shadow", "rain", "gray", "grey", "lost", "regret", "cold", "winter", "die", "dead",
            "empty", "triste", "tristeza", "llanto", "llorar", "dolor", "soledad", "solo", "adios",
            "adiós", "despedida", "nostalgia", "recuerdos", "lagrimas", "lágrimas", "olvido", "vacio",
            "vacío", "noche", "gris", "lluvia", "pena", "desamor", "silencio", "herida", "ballad",
            "acoustic", "slow", "blues", "adele", "sam smith", "lana del rey", "billie eilish", "radiohead"
        )
        val melancholyScores = allSongs.map { song ->
            var s = song.matchScore(*melancholyTerms)
            if (song.durationMs >= 240_000) s += 3
            song to s
        }
        val melancholyTracks = melancholyScores.filter { it.second >= 3 }
            .sortedByDescending { it.second }
            .map { it.first }
            .ifEmpty {
                melancholyScores.sortedByDescending { it.second }.take(12).map { it.first }
            }
        if (melancholyTracks.isNotEmpty()) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_mood_melancholy",
                    name = "Melancolía",
                    subtitle = "${melancholyTracks.size} canciones",
                    description = "Baladas íntimas y acordes acústicos.",
                    songs = melancholyTracks,
                    category = SmartCategory.MOOD,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
                    icon = Icons.Rounded.Cloud,
                    coverArtworks = melancholyTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // D. Calma & Paz Nocturna
        val calmTerms = arrayOf(
            "calm", "peace", "chill", "relax", "relaxing", "zen", "sleep", "dream", "dreaming",
            "moon", "star", "night", "midnight", "whisper", "serene", "soft", "breeze", "ambient",
            "meditation", "gentle", "smooth", "cozy", "paz", "calma", "dormir", "sueño", "noche",
            "luna", "estrellas", "suave", "silencio", "tranquilo", "descanso", "lofi", "lo-fi",
            "downtempo", "piano", "meditacion", "spa", "brian eno", "einaudi", "max richter", "tycho"
        )
        val calmScores = allSongs.map { song ->
            var s = song.matchScore(*calmTerms)
            if (song.durationMs >= 260_000) s += 3
            song to s
        }
        val calmTracks = calmScores.filter { it.second >= 3 }
            .sortedByDescending { it.second }
            .map { it.first }
            .ifEmpty {
                calmScores.sortedByDescending { it.second }.take(12).map { it.first }
            }
        if (calmTracks.isNotEmpty()) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_mood_calm",
                    name = "Calma",
                    subtitle = "${calmTracks.size} canciones",
                    description = "Sonidos relajantes y serenidad.",
                    songs = calmTracks,
                    category = SmartCategory.MOOD,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF134E5E), Color(0xFF71B280)),
                    icon = Icons.Rounded.NightsStay,
                    coverArtworks = calmTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // ---------------------------------------------------------
        // 2. RITMO Y ACTIVIDAD
        // ---------------------------------------------------------

        // A. Entrenamiento & Cardio
        val workoutTerms = arrayOf(
            "workout", "gym", "run", "running", "cardio", "fitness", "train", "training", "push",
            "strong", "power", "beast", "motivate", "motivation", "entrenar", "pesas", "fuerza",
            "correr", "fuerte", "hardstyle", "drop", "bass", "edm", "trap", "drum and bass", "metal"
        )
        val workoutTracks = allSongs.filter { song ->
            val score = song.matchScore(*workoutTerms)
            score >= 4 || (song.matchScore(*euphoriaTerms) >= 5 && song.durationMs in 90_000..240_000)
        }.distinctBy { it.id }
        if (workoutTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_rhythm_workout",
                    name = "Entrenamiento",
                    subtitle = "${workoutTracks.size} canciones",
                    description = "Ritmo y potencia para entrenar.",
                    songs = workoutTracks,
                    category = SmartCategory.RHYTHM,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFFEB3349), Color(0xFFF45C43)),
                    icon = Icons.Rounded.FitnessCenter,
                    coverArtworks = workoutTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // B. Enfoque & Flow
        val focusTerms = arrayOf(
            "focus", "study", "work", "flow", "code", "coding", "concentration", "deep",
            "instrumental", "synthwave", "retrowave", "post-rock", "enfoque", "concentracion",
            "estudio", "trabajo", "ambient", "lofi", "lo-fi"
        )
        val focusTracks = allSongs.filter { song ->
            song.matchScore(*focusTerms) >= 3 ||
                (song.genre.containsAny("lo-fi", "lofi", "ambient", "instrumental", "synthwave") ||
                song.title.containsAny("instrumental", "version", "mix", "flow", "study"))
        }.distinctBy { it.id }
        if (focusTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_rhythm_focus",
                    name = "Enfoque",
                    subtitle = "${focusTracks.size} canciones",
                    description = "Música continua sin distracciones.",
                    songs = focusTracks,
                    category = SmartCategory.RHYTHM,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF0052D4), Color(0xFF4364F7), Color(0xFF6FB1FC)),
                    icon = Icons.Rounded.Psychology,
                    coverArtworks = focusTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // C. Acústico & Café
        val acousticTerms = arrayOf(
            "acoustic", "unplugged", "stripped", "live", "piano", "guitar", "acustico",
            "acústica", "acústico", "guitarra", "desenchufado", "folk", "singer-songwriter"
        )
        val acousticTracks = allSongs.filter { song ->
            song.matchScore(*acousticTerms) >= 3 ||
                song.title.containsAny("acoustic", "acustico", "acústica", "unplugged", "en vivo")
        }.distinctBy { it.id }
        if (acousticTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_rhythm_acoustic",
                    name = "Acústico",
                    subtitle = "${acousticTracks.size} canciones",
                    description = "Guitarras acústicas y pureza vocal.",
                    songs = acousticTracks,
                    category = SmartCategory.RHYTHM,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                    icon = Icons.Rounded.MusicNote,
                    coverArtworks = acousticTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // ---------------------------------------------------------
        // 3. GÉNEROS
        // ---------------------------------------------------------

        // A. Electrónica & EDM
        val electronicTerms = arrayOf(
            "electronic", "edm", "dance", "house", "techno", "trance", "electro", "dubstep",
            "dnb", "drum and bass", "synthwave", "future bass", "club", "hardstyle"
        )
        val electronicTracks = allSongs.filter { song ->
            song.matchScore(*electronicTerms) >= 4 ||
                song.genre.containsAny("electronic", "dance", "house", "techno", "edm", "electro", "trance")
        }.distinctBy { it.id }
        if (electronicTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_genre_electronic",
                    name = "Electrónica",
                    subtitle = "${electronicTracks.size} canciones",
                    description = "Sintetizadores y música electrónica.",
                    songs = electronicTracks,
                    category = SmartCategory.GENRE,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)),
                    icon = Icons.Rounded.GraphicEq,
                    coverArtworks = electronicTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // B. Rock & Metal Legend
        val rockTerms = arrayOf(
            "rock", "metal", "punk", "alternative", "grunge", "hard rock", "heavy metal",
            "indie rock", "metallica", "ac/dc", "queen", "nirvana", "iron maiden", "slipknot", "linkin"
        )
        val rockTracks = allSongs.filter { song ->
            song.matchScore(*rockTerms) >= 4 ||
                song.genre.containsAny("rock", "metal", "alternative", "punk", "grunge")
        }.distinctBy { it.id }
        if (rockTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_genre_rock",
                    name = "Rock",
                    subtitle = "${rockTracks.size} canciones",
                    description = "Riffs de guitarra y energía pura.",
                    songs = rockTracks,
                    category = SmartCategory.GENRE,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF232526), Color(0xFF414345)),
                    icon = Icons.Rounded.Album,
                    coverArtworks = rockTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // C. Pop & Grandes Éxitos
        val popTerms = arrayOf(
            "pop", "dance pop", "synthpop", "indie pop", "hits", "greatest hits", "single"
        )
        val popTracks = allSongs.filter { song ->
            song.matchScore(*popTerms) >= 4 ||
                song.genre.containsAny("pop", "synthpop", "indie pop")
        }.distinctBy { it.id }
        if (popTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_genre_pop",
                    name = "Pop",
                    subtitle = "${popTracks.size} canciones",
                    description = "Grandes melodías e himnos pop.",
                    songs = popTracks,
                    category = SmartCategory.GENRE,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFFF857A6), Color(0xFFFF5858)),
                    icon = Icons.Rounded.Star,
                    coverArtworks = popTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // D. Urbano & Reggaeton
        val urbanTerms = arrayOf(
            "reggaeton", "trap", "rap", "hip hop", "hip-hop", "urbano", "dembow", "latin urban",
            "bad bunny", "daddy yankee", "balvin", "feid", "anuel", "ozuna", "rauw", "myke"
        )
        val urbanTracks = allSongs.filter { song ->
            song.matchScore(*urbanTerms) >= 4 ||
                song.genre.containsAny("reggaeton", "trap", "rap", "hip hop", "urban", "urbano")
        }.distinctBy { it.id }
        if (urbanTracks.size >= 2) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_genre_urban",
                    name = "Urbano",
                    subtitle = "${urbanTracks.size} canciones",
                    description = "Flow latino y ritmo urbano.",
                    songs = urbanTracks,
                    category = SmartCategory.GENRE,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFFFF8008), Color(0xFFFFC837)),
                    icon = Icons.Rounded.Radio,
                    coverArtworks = urbanTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        // E. Hi-Res Studio Masters
        val hiResTracks = allSongs.filter { song ->
            song.isHiRes || song.sampleRateHz > 48000 || song.bitDepth > 16 ||
                song.format == AudioFormat.FLAC || song.format == AudioFormat.DSD
        }.distinctBy { it.id }
        if (hiResTracks.isNotEmpty()) {
            playlists.add(
                UnifiedPlaylist(
                    id = "smart_genre_hires",
                    name = "Hi-Res Masters",
                    subtitle = "${hiResTracks.size} canciones",
                    description = "Fidelidad pura de estudio: 24-bit y DSD.",
                    songs = hiResTracks,
                    category = SmartCategory.GENRE,
                    isSmart = true,
                    gradientColors = listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)),
                    icon = Icons.Rounded.WorkspacePremium,
                    coverArtworks = hiResTracks.mapNotNull { it.artworkUri }.distinct().take(4)
                )
            )
        }

        return playlists
    }
}
