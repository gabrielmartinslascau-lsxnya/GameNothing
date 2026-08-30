package app.gamenative.di

import app.gamenative.PrefManager
import app.gamenative.enums.AppTheme
import app.gamenative.enums.FontType
import com.materialkolor.PaletteStyle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Referenced from https://github.com/fvilarino/App-Theme-Compose-Sample
 */

interface IAppTheme {
    val themeFlow: StateFlow<AppTheme>
    var currentTheme: AppTheme
    val paletteFlow: StateFlow<PaletteStyle>
    var currentPalette: PaletteStyle

    val mainColorFlow: StateFlow<String>
    var mainColor: String

    val wallpaperColorFlow: StateFlow<String>
    var wallpaperColor: String

    val maxRefreshRateFlow: StateFlow<Int>
    var maxRefreshRate: Int

    val jigglyAnimationsFlow: StateFlow<Boolean>
    var jigglyAnimations: Boolean

    val glassPanelsFlow: StateFlow<Boolean>
    var glassPanels: Boolean

    val trueGlassEnabledFlow: StateFlow<Boolean>
    var trueGlassEnabled: Boolean

    val glassTransparencyFlow: StateFlow<Float>
    var glassTransparency: Float

    val glassBlurIntensityFlow: StateFlow<Float>
    var glassBlurIntensity: Float

    val glassColorFlow: StateFlow<String>
    var glassColor: String

    val fontTypeFlow: StateFlow<FontType>
    var fontType: FontType

    val downloadBarStyleFlow: StateFlow<app.gamenative.enums.DownloadBarStyle>
    var downloadBarStyle: app.gamenative.enums.DownloadBarStyle
}

class AppThemeImpl : IAppTheme {

    override val themeFlow: MutableStateFlow<AppTheme> = MutableStateFlow(PrefManager.appTheme)
    override var currentTheme: AppTheme by AppThemeDelegate()

    override val paletteFlow: MutableStateFlow<PaletteStyle> = MutableStateFlow(PrefManager.appThemePalette)
    override var currentPalette: PaletteStyle by AppPaletteDelegate()

    override val mainColorFlow: MutableStateFlow<String> = MutableStateFlow(PrefManager.mainColor)
    override var mainColor: String by MainColorDelegate()

    override val wallpaperColorFlow: MutableStateFlow<String> = MutableStateFlow(PrefManager.wallpaperColor)
    override var wallpaperColor: String by WallpaperColorDelegate()

    override val maxRefreshRateFlow: MutableStateFlow<Int> = MutableStateFlow(PrefManager.maxRefreshRate)
    override var maxRefreshRate: Int by MaxRefreshRateDelegate()

    override val jigglyAnimationsFlow: MutableStateFlow<Boolean> = MutableStateFlow(PrefManager.jigglyAnimations)
    override var jigglyAnimations: Boolean by JigglyAnimationsDelegate()

    override val glassPanelsFlow: MutableStateFlow<Boolean> = MutableStateFlow(PrefManager.glassPanels)
    override var glassPanels: Boolean by GlassPanelsDelegate()

    override val trueGlassEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(PrefManager.trueGlassEnabled)
    override var trueGlassEnabled: Boolean by TrueGlassEnabledDelegate()

    override val glassTransparencyFlow: MutableStateFlow<Float> = MutableStateFlow(PrefManager.glassTransparency)
    override var glassTransparency: Float by GlassTransparencyDelegate()

    override val glassBlurIntensityFlow: MutableStateFlow<Float> = MutableStateFlow(PrefManager.glassBlurIntensity)
    override var glassBlurIntensity: Float by GlassBlurIntensityDelegate()

    override val glassColorFlow: MutableStateFlow<String> = MutableStateFlow(PrefManager.glassColor)
    override var glassColor: String by GlassColorDelegate()

    override val fontTypeFlow: MutableStateFlow<FontType> = MutableStateFlow(PrefManager.fontType)
    override var fontType: FontType by FontTypeDelegate()

    override val downloadBarStyleFlow: MutableStateFlow<app.gamenative.enums.DownloadBarStyle> = MutableStateFlow(PrefManager.downloadBarStyle)
    override var downloadBarStyle: app.gamenative.enums.DownloadBarStyle by DownloadBarStyleDelegate()

    inner class AppThemeDelegate : ReadWriteProperty<Any, AppTheme> {
        override fun getValue(thisRef: Any, property: KProperty<*>): AppTheme = PrefManager.appTheme
        override fun setValue(thisRef: Any, property: KProperty<*>, value: AppTheme) {
            themeFlow.value = value
            PrefManager.appTheme = value
        }
    }

    inner class AppPaletteDelegate : ReadWriteProperty<Any, PaletteStyle> {
        override fun getValue(thisRef: Any, property: KProperty<*>): PaletteStyle = PrefManager.appThemePalette
        override fun setValue(thisRef: Any, property: KProperty<*>, value: PaletteStyle) {
            paletteFlow.value = value
            PrefManager.appThemePalette = value
        }
    }

    inner class MainColorDelegate : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String = PrefManager.mainColor
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
            mainColorFlow.value = value
            PrefManager.mainColor = value
        }
    }

    inner class WallpaperColorDelegate : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String = PrefManager.wallpaperColor
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
            wallpaperColorFlow.value = value
            PrefManager.wallpaperColor = value
        }
    }

    inner class MaxRefreshRateDelegate : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Int = PrefManager.maxRefreshRate
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
            maxRefreshRateFlow.value = value
            PrefManager.maxRefreshRate = value
        }
    }

    inner class JigglyAnimationsDelegate : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean = PrefManager.jigglyAnimations
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
            jigglyAnimationsFlow.value = value
            PrefManager.jigglyAnimations = value
        }
    }

    inner class GlassPanelsDelegate : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean = PrefManager.glassPanels
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
            glassPanelsFlow.value = value
            PrefManager.glassPanels = value
        }
    }

    inner class TrueGlassEnabledDelegate : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean = PrefManager.trueGlassEnabled
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
            trueGlassEnabledFlow.value = value
            PrefManager.trueGlassEnabled = value
        }
    }

    inner class GlassTransparencyDelegate : ReadWriteProperty<Any, Float> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Float = PrefManager.glassTransparency
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) {
            glassTransparencyFlow.value = value
            PrefManager.glassTransparency = value
        }
    }

    inner class GlassBlurIntensityDelegate : ReadWriteProperty<Any, Float> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Float = PrefManager.glassBlurIntensity
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) {
            glassBlurIntensityFlow.value = value
            PrefManager.glassBlurIntensity = value
        }
    }

    inner class GlassColorDelegate : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String = PrefManager.glassColor
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
            glassColorFlow.value = value
            PrefManager.glassColor = value
        }
    }

    inner class FontTypeDelegate : ReadWriteProperty<Any, FontType> {
        override fun getValue(thisRef: Any, property: KProperty<*>): FontType = PrefManager.fontType
        override fun setValue(thisRef: Any, property: KProperty<*>, value: FontType) {
            fontTypeFlow.value = value
            PrefManager.fontType = value
        }
    }

    inner class DownloadBarStyleDelegate : ReadWriteProperty<Any, app.gamenative.enums.DownloadBarStyle> {
        override fun getValue(thisRef: Any, property: KProperty<*>): app.gamenative.enums.DownloadBarStyle = PrefManager.downloadBarStyle
        override fun setValue(thisRef: Any, property: KProperty<*>, value: app.gamenative.enums.DownloadBarStyle) {
            downloadBarStyleFlow.value = value
            PrefManager.downloadBarStyle = value
        }
    }
}

@InstallIn(SingletonComponent::class)
@Module
class AppThemeModule {
    @Provides
    @Singleton
    fun provideAppTheme(): IAppTheme = AppThemeImpl()
}
