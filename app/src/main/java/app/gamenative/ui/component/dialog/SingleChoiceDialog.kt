package app.gamenative.ui.component.dialog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.enums.AppTheme
import app.gamenative.ui.theme.BrokenGlassDialogSurface
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.glassContentColor

@Composable
fun SingleChoiceDialog(
    openDialog: Boolean,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    title: String,
    items: List<String>,
    currentItem: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!openDialog) {
        return
    }

    val looks = LocalLooksState.current
    if (looks.isGlassEnabled) {
        val textColor = glassContentColor()
        Dialog(onDismissRequest = onDismiss) {
            BrokenGlassDialogSurface(
                cornerRadius = 28,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = iconDescription,
                            tint = textColor,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = textColor,
                        modifier = if (icon != null) Modifier.align(Alignment.CenterHorizontally) else Modifier,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .selectableGroup()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        items.forEachIndexed { index, entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = index == currentItem,
                                        onClick = { onSelected(index) },
                                        role = Role.RadioButton,
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = index == currentItem,
                                    onClick = null,
                                )
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = textColor,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            content = { Text(text = stringResource(R.string.close)) },
                        )
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                icon?.let {
                    Icon(imageVector = it, contentDescription = iconDescription)
                }
            },
            title = { Text(text = title) },
            text = {
                Column(modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { index, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = index == currentItem,
                                    onClick = { onSelected(index) },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == currentItem,
                                onClick = null,
                            )
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    content = { Text(text = stringResource(R.string.close)) },
                )
            },
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview_SingleChoiceDialog() {
    val content = LocalContext.current
    PrefManager.init(content)

    val list = remember { AppTheme.entries }
    var theme by remember { mutableStateOf(AppTheme.NIGHT) }

    PluviaTheme {
        SingleChoiceDialog(
            openDialog = true,
            items = list.map { it.text },
            icon = Icons.Default.BrightnessMedium,
            title = "App Theme",
            currentItem = theme.ordinal,
            onSelected = { theme = list[it] },
            onDismiss = { },
        )
    }
}
