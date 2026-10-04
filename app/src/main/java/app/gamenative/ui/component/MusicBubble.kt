package app.gamenative.ui.component

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.gamenative.PrefManager
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.PluviaGlassPanel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.utils.MusicManager
import app.gamenative.utils.hapticClickable
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import timber.log.Timber
import kotlin.math.roundToInt

@Composable
fun BoxScope.MusicBubble() {
    if (!PrefManager.musicPlayerEnabled) {
        SideEffect {
            Timber.tag("MusicBubble").d("Music player is disabled in settings")
        }
        return
    }

    val isPlaying by MusicManager.isPlaying
    val trackName by MusicManager.currentTrackName
    val playlist = MusicManager.playlist
    val currentTrackIndex by MusicManager.currentTrackIndex

    var isExpanded by remember { mutableStateOf(false) }
    var isPlaylistExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val looks = LocalLooksState.current
    val contentColor = glassContentColor()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .align(Alignment.BottomEnd)
            .padding(16.dp)
            .padding(bottom = 64.dp)
            .animateContentSize()
    ) {
        PluviaGlassPanel(
            cornerRadius = if (isExpanded) 24 else 12
        ) {
            if (!isExpanded) {
                // Collapsed Bubble
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .hapticClickable { isExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        contentDescription = "Music",
                        tint = looks.mainColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Expanded Player
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playlist Section (Left Side)
                    if (isPlaylistExpanded) {
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .height(220.dp)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Playlist",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(playlist) { index, file ->
                                    val isCurrent = index == currentTrackIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) looks.mainColor.copy(alpha = 0.1f) else Color.Transparent)
                                            .clickable { MusicManager.playAtIndex(index) }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CoilImage(
                                            imageModel = { file },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            imageOptions = ImageOptions(
                                                contentScale = ContentScale.Crop
                                            ),
                                            failure = {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = contentColor.copy(alpha = 0.3f),
                                                    modifier = Modifier.padding(4.dp)
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = file.nameWithoutExtension,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) looks.mainColor else contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(200.dp)
                                .background(contentColor.copy(alpha = 0.1f))
                        )
                    }

                    // Main Controls Section (Right Side)
                    Column(
                        modifier = Modifier
                            .width(240.dp)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isPlaylistExpanded = !isPlaylistExpanded },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaylistExpanded) Icons.AutoMirrored.Filled.PlaylistPlay else Icons.AutoMirrored.Filled.List,
                                        contentDescription = "Playlist",
                                        tint = if (isPlaylistExpanded) looks.mainColor else contentColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Music Player",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.6f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.1f))
                                    .hapticClickable {
                                        isExpanded = false
                                        isPlaylistExpanded = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Minimize",
                                    tint = contentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (playlist.isEmpty()) {
                            Text(
                                text = "No music found.\nPick a folder in Settings > Feel",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = trackName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Transport Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { MusicManager.previous() }) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = contentColor)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.1f))
                                        .hapticClickable { MusicManager.toggle() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle",
                                        tint = contentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = { MusicManager.next() }) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = contentColor)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            var volume by remember { mutableStateOf(PrefManager.musicVolume) }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                Slider(
                                    value = volume,
                                    onValueChange = {
                                        volume = it
                                        MusicManager.setVolume(it)
                                    },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = looks.mainColor,
                                        activeTrackColor = looks.mainColor,
                                        inactiveTrackColor = contentColor.copy(alpha = 0.2f)
                                    )
                                )
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
