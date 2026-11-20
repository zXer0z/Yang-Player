package com.yang.player.lyrics

import java.util.Locale

enum class LyricsLanguage(val label: String) {
    SPANISH("Español"),
    ENGLISH("English");

    companion object {
        private val token = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?")
        private val spanishWords = """
            que del las los una unos unas pero para por porque cuando cuanto mientras aunque donde cómo como
            aquí ahí allá así todavía siempre nunca solo contigo conmigo nosotros ustedes ellos ellas ella eres
            estoy estaba hemos somos tiene tienes tengo quiero quieres quieren puede puedes puedo podría
            volver vuelve vuelves llega quieras sabes olvido olvidarte nuestro nuestra tus sus mis más mí
            tú él qué sé sí corazón amor noche mañana todo toda bien nada algo entre hasta desde quien sin estás
            ahora estos estas ese esa eso este esto fueron fue verso algún alguna ninguna también tiempo
        """.trim().split(Regex("\\s+")).toSet()
        private val englishWords = """
            the and of in on my your you you'll you're you've yours is are am was were been being be but
            with without when where why how what which who have has had do does did don't doesn't didn't cannot
            could couldn't would wouldn't should shouldn't can will won't shall know back come comes let
            it's i'll i'm that that's this these those they them their our ours from for into over
            forget about too only never still now every not nothing just there all again something
        """.trim().split(Regex("\\s+")).toSet()

        internal fun fromTag(tag: String?): LyricsLanguage? = when (tag?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')) {
            "es", "spa", "esp", "español", "spanish" -> SPANISH
            "en", "eng", "english" -> ENGLISH
            else -> null
        }

        internal fun detect(lines: List<LyricLine>): LyricsLanguage? {
            var spanish = 0
            var english = 0
            for (line in lines) {
                val text = line.text.lowercase(Locale.ROOT)
                if (text.any { it in "ñ¿¡" }) spanish += 2
                for (match in token.findAll(text)) {
                    val word = match.value.replace('’', '\'')
                    if (word in spanishWords) spanish++
                    if (word in englishWords) english++
                }
            }
            return when {
                spanish >= 6 && spanish >= english * 2 -> SPANISH
                english >= 6 && english >= spanish * 2 -> ENGLISH
                else -> null
            }
        }
    }
}
