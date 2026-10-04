package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.ui.component.focusRing
import app.gamenative.ui.data.GameViewState
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.util.JoystickScrollHandler
import app.gamenative.utils.hapticClickable
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
internal fun Library360Carousel(
    state: LibraryState,
    onNavigate: (String) -> Unit,
    onSetGameViewState: (GameViewState) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onFocusedIndexChanged: (Int) -> Unit = {},
) {
    val items = state.appInfoList
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Games Found",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "Add game folders in Settings or Login to Steam",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    val isHeroMode = state.gameViewState != GameViewState.CAROUSEL
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        // 4. HORIZONTAL FADE OVERLAY
        val heroFadeAlpha by animateFloatAsState(
            targetValue = if (state.gameViewState == GameViewState.HERO_DETAILS) 1f else 0f,
            animationSpec = tween(600),
            label = "hero_fade"
        )

        if (heroFadeAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(heroFadeAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = if (isLandscape) {
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.98f))
                            } else {
                                listOf(Color.Black.copy(alpha = 0.95f), Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            }
                        )
                    )
                    .then(
                        if (isLandscape) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.98f)),
                                    startX = with(density) { (screenWidth.value * 0.2f).dp.toPx() },
                                    endX = with(density) { (screenWidth.value * 0.8f).dp.toPx() }
                                )
                            )
                        } else Modifier
                    )
            )
        }

        // 1. 360 BASE CAROUSEL
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isLandscape) 220.dp else 80.dp),
            beyondViewportPageCount = 5,
            userScrollEnabled = !isHeroMode
        ) { page ->
            val item = items[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

            val startScale = if (isLandscape) 0.55f else 0.75f
            val endScale = if (isLandscape) 1.0f else 1.15f

            val itemScale = lerp(
                start = startScale,
                stop = endScale,
                fraction = 1f - abs(pageOffset).coerceIn(0f, 1f)
            )

            val rotationY = pageOffset * -35f

            // HERO DETAILS ANIMATION
            val isCurrentPage = pagerState.currentPage == page

            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            LaunchedEffect(isFocused) {
                if (isFocused && !isCurrentPage) {
                    pagerState.animateScrollToPage(page)
                }
                if (isFocused) {
                    onFocusedIndexChanged(page)
                }
            }

            val heroShiftX by animateFloatAsState(
                targetValue = if (isHeroMode && isCurrentPage) {
                    if (isLandscape) -screenWidth.value * 0.35f else 0f
                } else 0f,
                animationSpec = tween(800),
                label = "hero_shift_x"
            )

            val heroShiftY by animateFloatAsState(
                targetValue = if (isHeroMode && isCurrentPage && !isLandscape) {
                    screenHeight.value * 0.45f // Move card to bottom in portrait
                } else 0f,
                animationSpec = tween(800),
                label = "hero_shift_y"
            )

            val heroScaleMultiplier by animateFloatAsState(
                targetValue = if (isHeroMode && isCurrentPage) {
                    if (isLandscape) 1.0f else 0.8f // Moderate scale in portrait
                } else 1f,
                animationSpec = tween(800),
                label = "hero_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.rotationY = if (isHeroMode) 0f else rotationY
                        this.alpha = if (isHeroMode && !isCurrentPage) 0f else 1f
                        cameraDistance = 16 * density.density
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(x = if (isCurrentPage) heroShiftX.dp else 0.dp, y = if (isCurrentPage) heroShiftY.dp else 0.dp)
                        .scale(if (isCurrentPage) itemScale * heroScaleMultiplier else itemScale)
                ) {
                    val cardWidth = if (isLandscape) 200.dp else 240.dp
                    val cardHeight = if (isLandscape) 300.dp else 340.dp

                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .then(
                                if (isCurrentPage && firstItemFocusRequester != null) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .focusable(interactionSource = interactionSource)
                            .focusRing(interactionSource, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .hapticClickable(
                                interactionSource = interactionSource,
                                onClick = {
                                    if (state.gameViewState == GameViewState.CAROUSEL) {
                                        onSetGameViewState(GameViewState.HERO_DETAILS)
                                    } else {
                                        onNavigate(item.appId)
                                    }
                                }
                            )
                    ) {
                        val visualModel = item.capsuleImageUrl.ifEmpty { item.clientIconUrl }
                        CoilImage(
                            imageModel = { visualModel },
                            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                            modifier = Modifier.fillMaxSize()
                        )

                        // Card Branding Logic
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // MIRRORED REFLECTION
                    if (!isHeroMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .height(140.dp)
                                .graphicsLayer {
                                    rotationX = 180f
                                    alpha = 0.22f
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black),
                                            startY = 0f,
                                            endY = size.height
                                        )
                                    )
                                }
                        ) {
                            val reflectionModel = item.capsuleImageUrl.ifEmpty { item.clientIconUrl }
                            CoilImage(
                                imageModel = { reflectionModel },
                                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Metadata & Trailer
        AnimatedVisibility(
            visible = state.gameViewState == GameViewState.HERO_DETAILS,
            enter = fadeIn(tween(1000, delayMillis = 400)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            val currentItem = items[pagerState.currentPage]
            val metadata = state.gameMetadata[currentItem.appId]
            val descriptionScrollState = rememberScrollState()

            // JOYSTICK SCROLL SUPPORT
            JoystickScrollHandler(scrollState = descriptionScrollState)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount > 50 && change.position.x < 100) {
                                onSetGameViewState(GameViewState.CAROUSEL)
                            }
                        }
                    }
            ) {
                // Close Button (Touch Exit)
                IconButton(
                    onClick = { onSetGameViewState(GameViewState.CAROUSEL) },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 80.dp)
                        .padding(start = 4.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Column(
                    modifier = Modifier
                        .width(if (isLandscape) 440.dp else screenWidth - 60.dp)
                        .align(if (isLandscape) Alignment.CenterEnd else Alignment.TopCenter)
                        .padding(
                            end = if (isLandscape) 80.dp else 0.dp,
                            top = if (isLandscape) 0.dp else 120.dp
                        ),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally
                ) {
                    // Trailer Player (Media3 ExoPlayer)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 240.dp else 220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        val trailerUrl = metadata?.trailerUrl

                        if (!trailerUrl.isNullOrEmpty()) {
                            val context = LocalContext.current
                            val trailerPlayer = remember(trailerUrl) {
                                ExoPlayer.Builder(context).build().apply {
                                    repeatMode = Player.REPEAT_MODE_ONE
                                    volume = 1f // Enable audio for trailers
                                    setMediaItem(MediaItem.fromUri(Uri.parse(trailerUrl)))
                                    prepare()
                                    playWhenReady = true
                                }
                            }

                            DisposableEffect(trailerPlayer) {
                                onDispose { trailerPlayer.release() }
                            }

                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        this.player = trailerPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                },
                                update = { view ->
                                    view.player = trailerPlayer
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("PREVIEW READY", color = Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = metadata?.name ?: currentItem.name,
                        style = if (isLandscape) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // SCROLLABLE DESCRIPTION
                    Box(modifier = Modifier.heightIn(max = if (isLandscape) 180.dp else 160.dp)) {
                        Column(modifier = Modifier.verticalScroll(descriptionScrollState)) {
                            Text(
                                text = metadata?.description ?: "Experience the next generation of handheld gaming. Optimized for Pluvia with true hardware-accelerated refraction and seamless video backgrounds.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 24.sp
                            )
                        }
                    }

                    if (metadata != null && metadata.developer.isNotEmpty()) {
                        Text(
                            text = "Developed by ${metadata.developer}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Interaction Hint (Now a clickable button)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .hapticClickable { onNavigate(currentItem.appId) }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("LAUNCH CONTAINER", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                }
            }
        }
    }
}
