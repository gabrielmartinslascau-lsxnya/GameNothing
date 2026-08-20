package app.gamenative.ui.component.dialog.state

import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.graphics.Color
import app.gamenative.ui.enums.DialogType

data class MessageDialogState(
    val visible: Boolean,
    val type: DialogType = DialogType.NONE,
    val confirmBtnText: String = "Confirm",
    val dismissBtnText: String = "Dismiss",
    val actionBtnText: String? = null,
    val title: String? = null,
    val message: String? = null,
    val confirmBtnColor: Color? = null,
    val dismissBtnColor: Color? = null,
) {
    companion object {
        val Saver = mapSaver(
            save = { state ->
                mapOf(
                    "visible" to state.visible,
                    "type" to state.type,
                    "confirmBtnText" to state.confirmBtnText,
                    "dismissBtnText" to state.dismissBtnText,
                    "actionBtnText" to state.actionBtnText,
                    "title" to state.title,
                    "message" to state.message,
                    "confirmBtnColor" to state.confirmBtnColor?.value?.toLong(),
                    "dismissBtnColor" to state.dismissBtnColor?.value?.toLong(),
                )
            },
            restore = { savedMap ->
                MessageDialogState(
                    visible = savedMap["visible"] as Boolean,
                    type = savedMap["type"] as DialogType,
                    confirmBtnText = savedMap["confirmBtnText"] as String,
                    dismissBtnText = savedMap["dismissBtnText"] as String,
                    actionBtnText = savedMap["actionBtnText"] as String?,
                    title = savedMap["title"] as String?,
                    message = savedMap["message"] as String?,
                    confirmBtnColor = (savedMap["confirmBtnColor"] as? Long)?.let { Color(it.toULong()) },
                    dismissBtnColor = (savedMap["dismissBtnColor"] as? Long)?.let { Color(it.toULong()) },
                )
            },
        )
    }
}
