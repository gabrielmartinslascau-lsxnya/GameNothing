package app.gamenative.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.ui.theme.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun LoopingVideoBackground(
    videoPath: String,
    modifier: Modifier = Modifier,
    dimAlpha: Float = 0.4f,
    dimAlphaProvider: () -> Float = { dimAlpha },
    autoExtractColor: Boolean = PrefManager.autoExtractWallpaperColor,
    onColorExtracted: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    LaunchedEffect(exoPlayer, videoPath) {
        val file = File(videoPath)
        if (videoPath.isNotEmpty() && file.exists()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(videoPath, autoExtractColor) {
        if (videoPath.isNotEmpty() && autoExtractColor) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(videoPath)
                    val frame = retriever.getFrameAtTime(1000000) // 1 second in
                    retriever.release()

                    if (frame != null) {
                        val palette = Palette.from(frame).generate()
                        val dominantColor = palette.getDominantColor(android.graphics.Color.BLACK)
                        val hex = String.format("#%06X", 0xFFFFFF and dominantColor)
                        withContext(Dispatchers.Main) {
                            onColorExtracted?.invoke(hex)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore extraction failures
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // BRIDGE: Inflate from XML to force surface_type="texture_view"
                // This allows the Broken Glass panels to sample moving video frames.
                val view = LayoutInflater.from(ctx).inflate(R.layout.pluvia_video_player, null) as PlayerView
                view.apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Dimming overlay (draw-phase only so slider drags never recompose PlayerView)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val alpha = dimAlphaProvider().coerceIn(0f, 0.9f)
                    if (alpha > 0f) {
                        drawRect(Color.Black.copy(alpha = alpha))
                    }
                }
        )
    }
}
