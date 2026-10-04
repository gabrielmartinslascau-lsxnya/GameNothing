package app.gamenative.ui.screen.library.appscreen

import android.content.Context
import androidx.compose.runtime.Composable
import app.gamenative.data.LibraryItem
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.GameDisplayInfo
import com.winlator.container.ContainerData

class AndroidAppScreen : BaseAppScreen() {
    @Composable
    override fun getGameDisplayInfo(context: Context, libraryItem: LibraryItem): GameDisplayInfo {
        return GameDisplayInfo(
            name = libraryItem.name,
            developer = "Android",
            releaseDate = 0,
            heroImageUrl = libraryItem.heroImageUrl,
            iconUrl = libraryItem.iconHash,
            gameId = libraryItem.gameId,
            appId = libraryItem.appId
        )
    }

    override fun isInstalled(context: Context, libraryItem: LibraryItem): Boolean = true

    override fun isValidToDownload(context: Context, libraryItem: LibraryItem): Boolean = false

    override fun isDownloading(context: Context, libraryItem: LibraryItem): Boolean = false

    override fun getDownloadProgress(context: Context, libraryItem: LibraryItem): Float = 0f

    override fun onDownloadInstallClick(context: Context, libraryItem: LibraryItem, onClickPlay: (Boolean) -> Unit) {
        onClickPlay(false)
    }

    override fun onPauseResumeClick(context: Context, libraryItem: LibraryItem) {}

    override fun onDeleteDownloadClick(context: Context, libraryItem: LibraryItem) {}

    override fun onUpdateClick(context: Context, libraryItem: LibraryItem) {}

    override fun getExportFileExtension(): String = ".android"

    override fun getInstallPath(context: Context, libraryItem: LibraryItem): String? = null

    override fun loadContainerData(context: Context, libraryItem: LibraryItem): ContainerData = ContainerData()

    override fun saveContainerConfig(context: Context, libraryItem: LibraryItem, config: ContainerData) {}

    override fun supportsContainerConfig(): Boolean = false

    @Composable
    override fun getResetContainerOption(context: Context, libraryItem: LibraryItem): AppMenuOption? = null
}
