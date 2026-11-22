package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.gamenative.data.XboxGame
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Xbox / PC Game Pass games in the Room database.
 */
@Dao
interface XboxGameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<XboxGame>)

    @Query("SELECT * FROM xbox_games WHERE product_id = :productId")
    suspend fun getByProductId(productId: String): XboxGame?

    @Query("SELECT * FROM xbox_games WHERE app_id = :appId")
    suspend fun getByAppId(appId: Int): XboxGame?

    @Query("SELECT * FROM xbox_games WHERE is_installed = 1 ORDER BY title ASC")
    suspend fun getInstalledGames(): List<XboxGame>

    @Query("SELECT * FROM xbox_games ORDER BY title ASC")
    fun getAll(): Flow<List<XboxGame>>

    @Query("SELECT * FROM xbox_games ORDER BY title ASC")
    suspend fun getAllAsList(): List<XboxGame>

    @Query("SELECT * FROM xbox_games WHERE is_installed = 0")
    suspend fun getNonInstalledGames(): List<XboxGame>

    @Query(
        "UPDATE xbox_games SET is_installed = 1, install_path = :path, executable_path = :exe, install_size = :size, version_id = :versionId, content_id = :contentId WHERE product_id = :productId",
    )
    suspend fun markAsInstalled(
        productId: String,
        path: String,
        exe: String,
        size: Long,
        versionId: String,
        contentId: String,
    )

    @Query("UPDATE xbox_games SET is_installed = 0, install_path = '', executable_path = '', install_size = 0, version_id = '' WHERE product_id = :productId")
    suspend fun markAsUninstalled(productId: String)

    @Query("UPDATE xbox_games SET download_size = :size WHERE product_id = :productId")
    suspend fun updateDownloadSize(productId: String, size: Long)

    @Query("DELETE FROM xbox_games WHERE is_installed = 0")
    suspend fun deleteAllNonInstalledGames()

    @Query("SELECT * FROM xbox_games WHERE product_id IN (:productIds)")
    suspend fun getGamesByProductIds(productIds: List<String>): List<XboxGame>

    @Transaction
    suspend fun upsertPreservingInstallStatus(games: List<XboxGame>) {
        val productIds = games.map { it.productId }
        val existingGames = getGamesByProductIds(productIds)
        val existingMap = existingGames.associateBy { it.productId }

        val toInsert = games.map { newGame ->
            val existing = existingMap[newGame.productId]
            if (existing != null) {
                newGame.copy(
                    appId = existing.appId,
                    isInstalled = existing.isInstalled,
                    installPath = existing.installPath,
                    executablePath = existing.executablePath.ifEmpty { newGame.executablePath },
                    installSize = existing.installSize,
                    versionId = existing.versionId,
                    contentId = if (newGame.contentId.isNotEmpty()) newGame.contentId else existing.contentId,
                    lastPlayed = existing.lastPlayed,
                    playTimeMinutes = existing.playTimeMinutes,
                )
            } else {
                newGame
            }
        }
        insertAll(toInsert)
    }
}
