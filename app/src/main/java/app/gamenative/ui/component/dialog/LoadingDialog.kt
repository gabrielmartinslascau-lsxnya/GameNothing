package app.gamenative.ui.component.dialog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.gamenative.ui.theme.BrokenGlassDialogSurface
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.glassContentColor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * @param progress A value between 0 and 1 (inclusive), if the value is below 0 then the bar is
 * displayed as indeterminate
 */
@Composable
fun LoadingDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit = {},
    progress: Float,
    message: String = "Loading...",
) {
    when {
        visible -> {
            val looks = LocalLooksState.current
            Dialog(
                onDismissRequest = onDismissRequest,
            ) {
                BrokenGlassDialogSurface(
                    cornerRadius = 24,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val textColor = if (looks.isGlassEnabled) glassContentColor() else androidx.compose.ui.graphics.Color.Unspecified
                        Text(message, color = textColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (progress >= 0) {
                            Text(min(100, (progress * 100.0).roundToInt()).toString() + "%", color = textColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                            )
                        } else {
                            LinearProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview_LoadingDialog() {
    PluviaTheme {
        LoadingDialog(
            visible = true,
            progress = .75f,
        )
    }
}
