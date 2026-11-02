package com.yang.player.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.yang.player.online.OnlinePlayback
import com.yang.player.audio.AudioEngineStore
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.common.Format
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yang.player.MainActivity
import com.yang.player.R

@UnstableApi
class YangAudioService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var checkpoint: PlaybackSessionStore
    private var listeningTracker: ServiceListeningTracker? = null
    private val checkpointHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val savePosition = object : Runnable {
        override fun run() {
            player?.let { if (it.isPlaying) checkpoint.savePosition(it) }
            checkpointHandler.postDelayed(this, 3000)
        }
    }

    companion object {
        const val CHANNEL_ID = "yang_music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Audio Attributes for Music playback & handle noisy (headphone disconnect)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Build ExoPlayer using YangRenderersFactory (FFmpeg preferred + 32-bit float audio sink)
        val renderersFactory = YangRenderersFactory(this)
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(OnlinePlayback.factory(this)))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = exoPlayer
        checkpoint = PlaybackSessionStore(this)
        checkpoint.load()?.let { saved ->
            exoPlayer.setMediaItems(saved.songs.map(PlaybackSessionStore::item), saved.index, saved.positionMs)
            exoPlayer.repeatMode = saved.repeat.takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL } ?: Player.REPEAT_MODE_OFF
            exoPlayer.shuffleModeEnabled = saved.shuffle
            exoPlayer.playWhenReady = false
        }
        listeningTracker = ServiceListeningTracker(this, exoPlayer)
        SleepTimerStore.attach(exoPlayer)
        val audioEngine = AudioEngineStore.get(this)
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioEngine.decoder(decoderName) }
            override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                audioEngine.source(format.sampleMimeType.orEmpty(), format.averageBitrate)
                val item = if (eventTime.windowIndex in 0 until eventTime.timeline.windowCount)
                    eventTime.timeline.getWindow(eventTime.windowIndex, androidx.media3.common.Timeline.Window()).mediaItem else null
                val source = android.os.Bundle(item?.mediaMetadata?.extras ?: android.os.Bundle())
                source.putString("sourceId", item?.mediaId.orEmpty())
                source.putString("sourceTitle", item?.mediaMetadata?.title?.toString().orEmpty())
                if (format.sampleRate > 0) source.putInt("sourceRate", format.sampleRate)
                val codec = when (format.sampleMimeType) {
                    "audio/flac" -> "FLAC"; "audio/mpeg" -> "MP3"; "audio/mp4a-latm" -> "AAC"
                    "audio/opus" -> "Opus"; "audio/vorbis" -> "Vorbis"; "audio/raw" -> "PCM"
                    else -> source.getString("sourceCodec", format.sampleMimeType.orEmpty())
                }
                source.putString("sourceCodec", codec)
                com.yang.player.audio.YinConnector.get(this@YangAudioService).updateSource(source)
            }
        })

        // 3. PendingIntent for opening MainActivity on notification click
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Initialize MediaSession
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        // 5. Player listener
        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    checkpoint.saveQueue(player)
                } else {
                    checkpoint.savePosition(player)
                }
            }
        })
        checkpointHandler.postDelayed(savePosition, 3000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        checkpointHandler.removeCallbacks(savePosition)
        listeningTracker?.close(); listeningTracker = null
        player?.let { SleepTimerStore.detach(it); checkpoint.saveQueue(it) }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            // Keep the original MediaItems and URIs intact without overriding
            return com.google.common.util.concurrent.Futures.immediateFuture(mediaItems)
        }
    }
}

