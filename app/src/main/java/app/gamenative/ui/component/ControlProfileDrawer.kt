package app.gamenative.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.data.ControlProfile
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.PluviaGlassPanel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.utils.HapticManager

@Composable
fun ControlProfileDrawer(
    visible: Boolean,
    profiles: List<ControlProfile>,
    activeProfileName: String? = null,
    onDismiss: () -> Unit,
    onProfileSelected: (ControlProfile) -> Unit,
    onDeleteProfile: (ControlProfile) -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Semi-transparent background for the rest of the screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            var columns by remember { mutableIntStateOf(2) }
            val contentColor = glassContentColor()

            AnimatedVisibility(
                visible = true, // Control visibility via Dialog inclusion
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                PluviaGlassPanel(
                    cornerRadius = 0,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(400.dp) // Wider for better visibility
                ) {
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Control Profiles",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = contentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (profiles.isEmpty()) "No profiles saved" else "${profiles.size} profiles available",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = contentColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Column Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Columns: ", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                            IconButton(onClick = { columns = 1 }) {
                                Icon(Icons.Default.ViewStream, contentDescription = "1 Column", tint = if (columns == 1) MaterialTheme.colorScheme.primary else contentColor)
                            }
                            IconButton(onClick = { columns = 2 }) {
                                Icon(Icons.Default.ViewModule, contentDescription = "2 Columns", tint = if (columns == 2) MaterialTheme.colorScheme.primary else contentColor)
                            }
                            IconButton(onClick = { columns = 3 }) {
                                Icon(Icons.Default.GridOn, contentDescription = "3 Columns", tint = if (columns == 3) MaterialTheme.colorScheme.primary else contentColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(profiles) { profile ->
                                val isActive = profile.name == activeProfileName
                                ProfileCard(
                                    profile = profile,
                                    isActive = isActive,
                                    onClick = {
                                        HapticManager.trigger(HapticManager.HapticType.CLICK)
                                        onProfileSelected(profile)
                                    },
                                    onDelete = {
                                        HapticManager.trigger(HapticManager.HapticType.LONG)
                                        onDeleteProfile(profile)
                                    },
                                    contentColor = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ControlProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    contentColor: Color
) {
    val looks = LocalLooksState.current
    val borderColor = if (isActive) looks.mainColor else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isActive) Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) looks.mainColor.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            // Add a small delete button if not a system profile
            TextButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
