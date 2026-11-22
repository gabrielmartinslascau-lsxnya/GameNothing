package app.gamenative.ui.component.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import app.gamenative.ui.component.settings.ContainerGlassDropdown
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.winlator.container.Container
import com.winlator.core.KeyValueSet

internal data class WinComponentEntry(
    val id: String,
    val value: Int,
)

internal fun parseWinComponents(wincomponents: String): List<WinComponentEntry> {
    val normalized = Container.normalizeWinComponents(wincomponents)
    val entries = mutableListOf<WinComponentEntry>()
    for (pair in KeyValueSet(normalized)) {
        val id = pair[0]
        val value = pair[1].toIntOrNull()?.coerceIn(0, 1) ?: 0
        entries += WinComponentEntry(id = id, value = value)
    }
    return entries
}

internal fun updateWinComponent(
    wincomponents: String,
    compId: String,
    newValue: Int,
): String {
    val clamped = newValue.coerceIn(0, 1).toString()
    val updated = linkedMapOf<String, String>()
    for (entry in parseWinComponents(wincomponents)) {
        updated[entry.id] = if (entry.id == compId) clamped else entry.value.toString()
    }
    if (compId !in updated) {
        updated[compId] = clamped
    }
    return Container.normalizeWinComponents(
        updated.entries.joinToString(",") { "${it.key}=${it.value}" },
    )
}

@Composable
fun WinComponentsTabContent(state: ContainerConfigState) {
    val config = state.config.value
    val components = parseWinComponents(config.wincomponents)
    val directXComponents = components.filter { it.id.startsWith("direct") }
    val generalComponents = components.filterNot { it.id.startsWith("direct") }

    if (directXComponents.isNotEmpty()) {
        ContainerGlassCard {
            SettingsGroup {
                directXComponents.forEachIndexed { index, component ->
                    val compId = component.id
                    val compNameRes = winComponentsItemTitleRes(compId)
                    if (index > 0) ContainerCardDivider()
                    key(compId) {
                        ContainerGlassDropdown(
                            colors = settingsTileColors(),
                            title = { Text(stringResource(id = compNameRes)) },
                            subtitle = { Text("DirectX") },
                            value = component.value,
                            items = state.winCompOpts,
                            onItemSelected = { selected ->
                                val latestConfig = state.config.value
                                state.config.value = latestConfig.copy(
                                    wincomponents = updateWinComponent(latestConfig.wincomponents, compId, selected),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (generalComponents.isNotEmpty()) {
        ContainerGlassCard {
            SettingsGroup {
                generalComponents.forEachIndexed { index, component ->
                    val compId = component.id
                    val compNameRes = winComponentsItemTitleRes(compId)
                    if (index > 0) ContainerCardDivider()
                    key(compId) {
                        ContainerGlassDropdown(
                            colors = settingsTileColors(),
                            title = { Text(stringResource(id = compNameRes)) },
                            subtitle = { Text("General") },
                            value = component.value,
                            items = state.winCompOpts,
                            onItemSelected = { selected ->
                                val latestConfig = state.config.value
                                state.config.value = latestConfig.copy(
                                    wincomponents = updateWinComponent(latestConfig.wincomponents, compId, selected),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
