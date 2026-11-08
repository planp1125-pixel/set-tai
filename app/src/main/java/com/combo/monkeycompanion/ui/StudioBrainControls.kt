package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioBrainControls(
    enabled: Boolean,
    status: String,
    onConfigure: () -> Unit,
    onToggle: () -> Unit,
    onModel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (enabled) "🧠 Give Brain: AI enabled" else "🧠 Give Brain: OFF — local mode", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onConfigure, modifier = Modifier.weight(1f)) { Text("Configure AI") }
                Button(onClick = onToggle, modifier = Modifier.weight(1f)) { Text(if (enabled) "Turn off" else "Turn on") }
            }
            OutlinedButton(onClick = onModel, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("AI face modeling") }
        }
    }
}
