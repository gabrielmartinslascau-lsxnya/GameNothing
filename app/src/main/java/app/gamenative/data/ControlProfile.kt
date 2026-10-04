package app.gamenative.data

import kotlinx.serialization.Serializable

@Serializable
data class ControlProfile(
    val name: String,
    val data: String, // JSON representation of the profile elements and bindings
    val lastUsed: Long = System.currentTimeMillis()
)
