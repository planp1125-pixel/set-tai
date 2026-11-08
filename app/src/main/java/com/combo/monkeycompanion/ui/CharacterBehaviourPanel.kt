package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.mind.Personality
import com.combo.monkeycompanion.mind.Traits

/** Native Material 3 behaviour controls scoped to one character ID.
 * No character activation is performed when editing another character.
 */
@Composable
internal fun CharacterBehaviourPanel(characterId: String, modifier: Modifier = Modifier) {
    val mind = remember(characterId) { MindHub.mindFor(characterId) }
    var traits by remember(characterId) {
        mutableStateOf(mind?.let { Personality.getCustomTraits(characterId) ?: it.traits })
    }
    if (mind == null || traits == null) {
        Text("Character behaviour is unavailable", modifier = modifier)
        return
    }
    fun persist(change: (Traits) -> Traits) {
        val previous = traits ?: return
        val updated = change(previous)
        Personality.setCustomTraits(characterId, updated)
        MindHub.flush()
        traits = updated
    }
    val current = traits ?: return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Behaviour & personality", style = MaterialTheme.typography.titleLarge)
        Text("Changes are saved for this character only.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BehaviourSlider("Playfulness", current.playfulness) { value -> persist { it.copy(playfulness = value) } }
        BehaviourSlider("Chattiness", current.chattiness) { value -> persist { it.copy(chattiness = value) } }
        BehaviourSlider("Curiosity", current.curiosity) { value -> persist { it.copy(curiosity = value) } }
        BehaviourSlider("Energy / activity", current.energy) { value -> persist { it.copy(laziness = (1f - value).coerceIn(0.05f, 0.95f)) } }
        BehaviourSlider("Boldness", current.boldness) { value -> persist { it.copy(boldness = value) } }
        BehaviourSlider("Drama", current.drama) { value -> persist { it.copy(drama = value) } }
        var confirmReset by remember(characterId) { mutableStateOf(false) }
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore character defaults")
        }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Restore default behaviour?") },
                text = { Text("This removes this character's custom behaviour settings.") },
                confirmButton = {
                    TextButton(onClick = {
                        Personality.clearCustomTraits(characterId)
                        MindHub.flush()
                        traits = mind.traits
                        confirmReset = false
                    }) { Text("Restore") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
            )
        }
    }
}

/** Slider changes are committed when the user finishes dragging, not on every pixel. */
@Composable
private fun BehaviourSlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    Column {
        Text("$label · ${(draft * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = draft.coerceIn(0f, 1f), onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) }, valueRange = 0f..1f)
    }
}
