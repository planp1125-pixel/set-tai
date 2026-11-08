package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.mind.MindHub

/** Per-character memory controls. The diary object belongs to the requested ID. */
@Composable
internal fun CharacterMemoryPanel(characterId: String, modifier: Modifier = Modifier) {
    val mind = remember(characterId) { MindHub.mindFor(characterId) }
    if (mind == null) {
        Text("Character memory is unavailable", modifier = modifier)
        return
    }
    val diary = mind.diary
    var refresh by remember(characterId) { mutableIntStateOf(0) }
    var pendingRetention by remember(characterId) { mutableStateOf<Int?>(null) }
    var pendingMaximum by remember(characterId) { mutableStateOf<Int?>(null) }
    // Reading refresh makes changes to the non-Compose diary observable.
    @Suppress("UNUSED_VARIABLE") val observedRefresh = refresh
    fun save(change: () -> Unit) {
        change()
        MindHub.flush()
        refresh++
    }
    if (pendingMaximum != null || pendingRetention != null) {
        AlertDialog(
            onDismissRequest = { pendingMaximum = null; pendingRetention = null },
            title = { Text("Update diary limits?") },
            text = { Text("Reducing limits may permanently remove older unpinned memories.") },
            confirmButton = { TextButton(onClick = {
                pendingMaximum?.let { count -> save { diary.maxEntries = count; diary.enforceLimits(System.currentTimeMillis()) } }
                pendingRetention?.let { days -> save { diary.retentionDays = days; diary.enforceLimits(System.currentTimeMillis()) } }
                pendingMaximum = null
                pendingRetention = null
            }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { pendingMaximum = null; pendingRetention = null }) { Text("Cancel") } }
        )
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Memory & privacy", style = MaterialTheme.typography.titleLarge)
        Text("These settings apply only to this companion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Maximum diary entries", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Pause", 10 to "10", 30 to "30", 60 to "60", 150 to "150", 300 to "300").forEach { (count, label) ->
                FilterChip(selected = diary.maxEntries == count, onClick = {
                    if (count != diary.maxEntries && (count == 0 || (diary.maxEntries != 0 && count < diary.maxEntries))) {
                        pendingMaximum = count
                    } else save { diary.maxEntries = count; diary.enforceLimits(System.currentTimeMillis()) }
                }, label = { Text(label) })
            }
        }
        Text("Keep entries for", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Forever", 1 to "1 day", 7 to "7 days", 30 to "30 days").forEach { (days, label) ->
                FilterChip(selected = diary.retentionDays == days, onClick = {
                    if (days != diary.retentionDays && days != 0 && (diary.retentionDays == 0 || days < diary.retentionDays)) {
                        pendingRetention = days
                    } else save { diary.retentionDays = days; diary.enforceLimits(System.currentTimeMillis()) }
                }, label = { Text(label) })
            }
        }
        HorizontalDivider()
        MemorySwitch("Log places & visits", diary.logPlaces) { save { diary.logPlaces = it } }
        MemorySwitch("Log chats & games", diary.logChats) { save { diary.logChats = it } }
        MemorySwitch("Log naps & away traces", diary.logNight) { save { diary.logNight = it } }
        MemorySwitch("Log powers & world events", diary.logWorld) { save { diary.logWorld = it } }
        Text("Pinned memories and individual diary entries can be managed in the full diary.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemorySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
