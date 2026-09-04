package app.gamenative.ui.data

import app.gamenative.enums.AppTheme
import app.gamenative.enums.FontType
import app.gamenative.enums.GlassMode
import app.gamenative.ui.enums.ConnectionState
import app.gamenative.ui.screen.PluviaScreen
import com.materialkolor.PaletteStyle

data class MainState(
    val appTheme: AppTheme = AppTheme.NIGHT,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val resettedScreen: PluviaScreen? = null,
    val currentScreen: PluviaScreen? = PluviaScreen.LoginUser,
    val hasLaunched: Boolean = false,
    val loadingDialogVisible: Boolean = false,
    val loadingDialogProgress: Float = 0F,
    val loadingDialogMessage: String = "Loading...",
    val annoyingDialogShown: Boolean = false,
    val hasCrashedLastStart: Boolean = false,
    val isSteamConnected: Boolean = false,
    val launchedAppId: String = "",
    val bootToContainer: Boolean = false,
    val testGraphics: Boolean = false,
    val diagnostics: Boolean = false,
    val showBootingSplash: Boolean = false,
    val bootingSplashText: String = "Booting...",
    val bootingSplashHeroImageUrl: String = "",

    val mainColor: String = "#9C27B0",
    val wallpaperColor: String = "#121416",
    val maxRefreshRate: Int = 60,
    val refreshRateEnabled: Boolean = false,

    val jigglyAnimations: Boolean = true,
    val glassPanels: Boolean = false,
    val glassTransparency: Float = 0.3f,
    val glassBlurIntensity: Float = 16f,
    val blurIntensiveness: Float = 16f,
    val liquidDistortion: Float = 0.5f,
    val glassMode: GlassMode = GlassMode.STANDARD,
    val glassColor: String = "#FFFFFF",
    val fontType: FontType = FontType.NDOT,
    val downloadBarStyle: app.gamenative.enums.DownloadBarStyle = app.gamenative.enums.DownloadBarStyle.NOTHING,

    val videoBackgroundPath: String = "",
    val videoBackgroundDimAlpha: Float = 0.4f,

    // Connection state for background reconnection
    // Default to DISCONNECTED - service will start and set to CONNECTING
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionMessage: String? = null,
    val connectionTimeoutSeconds: Int = 0,
)
