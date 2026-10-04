package app.gamenative.utils

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.gamenative.PrefManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import timber.log.Timber
import java.io.File
import app.gamenative.PluviaApp
import kotlinx.coroutines.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import app.gamenative.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

object MusicManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val player: Player? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null
    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    val currentTrackName = mutableStateOf("No Track")
    val playlist = mutableStateListOf<File>()
    val currentTrackIndex = mutableIntStateOf(-1)

    fun init(context: Context) {
        if (controllerFuture != null) return
        Timber.tag("MusicManager").d("Initializing MusicManager with MediaController")

        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            setupController(controller, context)
        }, MoreExecutors.directExecutor())

        // Background Keep-Alive loop to prevent process freezing and ensure sync
        scope.launch {
            while (isActive) {
                delay(5000)
            }
        }
    }

    private fun setupController(controller: MediaController, context: Context) {
        try {
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.volume = PrefManager.musicVolume

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    PrefManager.musicIsPlaying = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.let {
                        currentTrackName.value = it.mediaMetadata.title?.toString() ?: "Unknown"
                        PrefManager.musicLastTrackUri = it.localConfiguration?.uri.toString()
                        PrefManager.musicLastTrackName = currentTrackName.value
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.tag("MusicManager").e(error, "Player error: ${error.message}")
                    next()
                }
            })

            // Restore state or load folder
            val folderPath = PrefManager.musicFolderPath
            if (folderPath.isNotEmpty()) {
                loadFolder(folderPath)
            }

            val lastUri = PrefManager.musicLastTrackUri
            if (lastUri.isNotEmpty()) {
                val file = File(lastUri.removePrefix("file://"))
                if (file.exists()) {
                    val index = playlist.indexOfFirst { it.absolutePath == file.absolutePath }
                    if (index != -1) {
                        playAtIndex(index, startPlaying = PrefManager.musicIsPlaying)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("MusicManager").e(e, "Failed to setup controller")
        }
    }

    fun loadFolder(path: String) {
        Timber.tag("MusicManager").d("Loading folder: $path")
        val folder = File(path)
        if (folder.exists() && folder.isDirectory) {
            val extensions = listOf("mp3", "flac", "wav", "m4a", "ogg")
            val files = folder.listFiles { file ->
                file.isFile && file.extension.lowercase() in extensions
            }?.sortedBy { it.name } ?: emptyList()

            Timber.tag("MusicManager").d("Found ${files.size} tracks")
            playlist.clear()
            playlist.addAll(files)

            val mediaItems = files.map { file ->
                MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setMediaId(file.absolutePath)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(file.nameWithoutExtension).build())
                    .build()
            }
            player?.setMediaItems(mediaItems)
            player?.prepare()
        }
    }

    fun playAtIndex(index: Int, startPlaying: Boolean = true) {
        if (index in playlist.indices) {
            currentTrackIndex.intValue = index
            player?.seekTo(index, 0)
            if (startPlaying) player?.play()
            currentTrackName.value = playlist[index].nameWithoutExtension
        }
    }

    fun next() {
        if (playlist.isEmpty()) return
        player?.seekToNextMediaItem()
        player?.play()
    }

    fun previous() {
        if (playlist.isEmpty()) return
        player?.seekToPreviousMediaItem()
        player?.play()
    }

    fun setVolume(volume: Float) {
        PrefManager.musicVolume = volume
        player?.volume = volume
    }

    fun toggle() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun stop() {
        player?.stop()
    }

    fun release() {
        scope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controllerFuture = null
    }
}
