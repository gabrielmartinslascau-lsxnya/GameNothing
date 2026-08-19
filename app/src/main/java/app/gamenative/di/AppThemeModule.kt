package app.gamenative.di

import app.gamenative.PrefManager
import app.gamenative.enums.AppTheme
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
}

@InstallIn(SingletonComponent::class)
@Module
class AppThemeModule {
    @Provides
    @Singleton
    fun provideAppTheme(): IAppTheme = AppThemeImpl()
}
