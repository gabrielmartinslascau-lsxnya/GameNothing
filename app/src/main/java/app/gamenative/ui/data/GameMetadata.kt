package app.gamenative.ui.data

import kotlinx.serialization.Serializable

@Serializable
data class GameMetadata(
    val appId: String,
    val name: String,
    val description: String = "",
    val trailerUrl: String? = null,
    val developer: String = "",
    val releaseDate: String = "",
)
