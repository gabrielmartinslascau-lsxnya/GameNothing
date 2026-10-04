package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.settings.SettingsTextField

@Composable
fun SaveProfileDialog(
    visible: Boolean,
    existingProfileNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Save Control Profile") },
        text = {
            Column {
                Text("Enter a unique name for this control profile:")
                Spacer(modifier = Modifier.height(16.dp))
                SettingsTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = when {
                            it.isBlank() -> "Name cannot be empty"
                            existingProfileNames.any { existing -> existing.equals(it, ignoreCase = true) } -> "Profile with this name already exists"
                            else -> null
                        }
                    },
                    title = { Text("Profile Name") }
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank() && error == null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
