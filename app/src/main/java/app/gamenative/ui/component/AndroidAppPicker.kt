package app.gamenative.ui.component

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.gamenative.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean = false
)

@Composable
fun AndroidAppPicker(
    onAppsSelected: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(true) }

    val filteredApps = remember(allApps, searchQuery) {
        allApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    // Standard heuristic for launcher apps: must have a launch intent
                    val launchIntent = pm.getLaunchIntentForPackage(info.packageName)

                    val systemExcludeList = listOf(
                        "com.android.launcher", "com.google.android.launcher",
                        "com.android.systemui", "com.google.android.packageinstaller"
                    )

                    launchIntent != null && !systemExcludeList.any { info.packageName.contains(it) }
                }
                .map { info ->
                    AppInfo(
                        name = info.loadLabel(pm).toString(),
                        packageName = info.packageName,
                        icon = info.loadIcon(pm),
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }.sortedBy { it.name }
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(filteredApps) { app ->
                            val isSelected = selectedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                    .clickable {
                                        if (isSelected) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = app.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedPackages.add(app.packageName)
                                        else selectedPackages.remove(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAppsSelected(selectedPackages.toList()) },
                enabled = selectedPackages.isNotEmpty()
            ) {
                Text("Add Selected (${selectedPackages.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
