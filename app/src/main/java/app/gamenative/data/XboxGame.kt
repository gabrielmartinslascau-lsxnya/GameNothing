package app.gamenative.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Xbox / PC Game Pass entity for Room database.
 * Represents an entitlement returned by the Xbox / Microsoft Store catalog & xodus-nexus service.
 */
@Entity(tableName = "xbox_games", indices = [Index("product_id")])
data class XboxGame(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("app_id")
    val appId: Int = 0,

    /** Microsoft Store 12-character Product ID (e.g. "9NBLGGH42THS") */
    @ColumnInfo("product_id")
    val productId: String,

    /** Resolved MSIXVC ContentId UUID when a PC package is selected/installed */
    @ColumnInfo(name = "content_id", defaultValue = "")
    val contentId: String = "",

    @ColumnInfo("title")
    val title: String = "",

    @ColumnInfo("is_installed")
    val isInstalled: Boolean = false,

    @ColumnInfo("install_path")
    val installPath: String = "",

    @ColumnInfo(name = "executable_path", defaultValue = "")
    val executablePath: String = "",

    @ColumnInfo("art_url")
    val artUrl: String = "",

    @ColumnInfo(name = "hero_url", defaultValue = "")
    val heroUrl: String = "",

    @ColumnInfo("purchased_date")
    val purchasedDate: String = "",

    @ColumnInfo(name = "developer", defaultValue = "")
    val developer: String = "",

    @ColumnInfo(name = "publisher", defaultValue = "")
    val publisher: String = "",

    @ColumnInfo(name = "release_date", defaultValue = "")
    val releaseDate: String = "",

    @ColumnInfo(name = "download_size", defaultValue = "0")
    val downloadSize: Long = 0,

    @ColumnInfo(name = "install_size", defaultValue = "0")
    val installSize: Long = 0,

    @ColumnInfo(name = "version_id", defaultValue = "")
    val versionId: String = "",

    /** "owned" or "gamepass" */
    @ColumnInfo(name = "access_type", defaultValue = "owned")
    val accessType: String = "owned",

    @ColumnInfo(name = "is_pc_supported", defaultValue = "1")
    val isPcSupported: Boolean = true,

    @ColumnInfo(name = "is_cloud_supported", defaultValue = "0")
    val isCloudSupported: Boolean = false,

    @ColumnInfo(name = "cloud_product_id", defaultValue = "")
    val cloudProductId: String = "",

    @ColumnInfo(name = "last_played", defaultValue = "0")
    val lastPlayed: Long = 0,

    @ColumnInfo(name = "play_time_minutes", defaultValue = "0")
    val playTimeMinutes: Long = 0,

    @ColumnInfo("product_json")
    val productJson: String = "",
)
