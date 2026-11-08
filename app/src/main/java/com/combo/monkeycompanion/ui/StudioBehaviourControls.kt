package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioBehaviourControls(playfulness: Float, chattiness: Float, curiosity: Float, energy: Float, boldness: Float,
    onPlayfulness: (Float) -> Unit, onChattiness: (Float) -> Unit, onCuriosity: (Float) -> Unit,
    onEnergy: (Float) -> Unit, onBoldness: (Float) -> Unit, onDiary: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        trait("Playfulness", playfulness, onPlayfulness)
        trait("Chattiness", chattiness, onChattiness)
        trait("Curiosity", curiosity, onCuriosity)
        trait("Energy", energy, onEnergy)
        trait("Boldness", boldness, onBoldness)
        OutlinedButton(onClick = onDiary, modifier = Modifier.fillMaxWidth()) { Text("Diary and pinned memories") }
    }
}

@Composable
private fun trait(label: String, value: Float, update: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Slider(value = value.coerceIn(0f, 1f), onValueChange = update, valueRange = 0f..1f)
}
