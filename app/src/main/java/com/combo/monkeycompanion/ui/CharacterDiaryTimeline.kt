package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.mind.MindHub

/** Native Compose timeline for the selected character; never switches the active companion. */
@Composable
internal fun CharacterDiaryTimeline(characterId: String, modifier: Modifier = Modifier) {
    val mind = remember(characterId) { MindHub.mindFor(characterId) }
    var revision by remember(characterId) { mutableIntStateOf(0) }
    var pendingDelete by remember(characterId) { mutableStateOf<Long?>(null) }
    if (mind == null) {
        Text("Diary unavailable", modifier = modifier)
        return
    }
    val diary = mind.diary
    // Snapshot entries when refreshed; external diary mutations are not Compose state.
    val entries = remember(characterId, revision) {
        diary.pageEntries(characterId, System.currentTimeMillis(), 60)
    }
    fun commit(action: () -> Unit) {
        action()
        MindHub.flush()
        revision++
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this memory?") },
            text = { Text("This removes the entry from this companion's diary.") },
            confirmButton = {
                TextButton(onClick = {
                    val timestamp = pendingDelete
                    pendingDelete = null
                    if (timestamp != null) commit { diary.deleteEntry(timestamp) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Recent moments", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${diary.all.size} saved memories", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { revision++ }) { Text("Refresh") }
        }
        if (entries.isEmpty()) {
            Text("No diary entries yet.", style = MaterialTheme.typography.bodyMedium)
        }
        entries.forEach { (entry, whenText, body) ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(whenText, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { commit { diary.togglePin(entry.ts) } }) {
                            Text(if (entry.pinned) "Unpin" else "Pin")
                        }
                        TextButton(onClick = { pendingDelete = entry.ts }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
