package app.gamenative.service

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.os.PowerManager
import androidx.media3.common.Player

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()

        // Critical: Prevent CPU sleep during skips on Nothing OS
        player.setWakeMode(C.WAKE_MODE_NETWORK)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    acquireWakeLock()
                } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_IDLE) {
                    releaseWakeLock()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameNothing:PlaybackWakeLock")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10000) // Timeout after 10s as safety
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        releaseWakeLock()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
