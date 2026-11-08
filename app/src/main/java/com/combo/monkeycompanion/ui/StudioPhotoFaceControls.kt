package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Photo controls intentionally delegate all mutations to CompanionStudioActivity. */
@Composable
internal fun StudioPhotoFaceControls(
    analysisSummary: String, suggestionPrompt: String, bodyDetectedClearly: Boolean,
    faceModes: List<String>, selectedFaceMode: String,
    offsetX: Float, offsetY: Float, zoom: Float,
    onPickPhoto: () -> Unit, onBodyTemplate: (String) -> Unit,
    onFaceMode: (String) -> Unit, onNudge: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit, onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) { Text("Pick photo from gallery (offline)") }
        Text(analysisSummary, style = MaterialTheme.typography.bodyMedium)
        if (suggestionPrompt.isNotBlank()) Text(suggestionPrompt, style = MaterialTheme.typography.bodySmall)
        Text(if (bodyDetectedClearly) "Body detected" else "Body detection needs review", style = MaterialTheme.typography.labelMedium)
        Text("Body template", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("slim_body" to "Slim", "mid_body" to "Mid-body", "athletic_body" to "Athletic", "bulky_body" to "Bulky", "superboy_body" to "Zoom", "ironboy_body" to "Ironboy", "ninja_body" to "Ninja").forEach { (id, label) ->
                OutlinedButton(onClick = { onBodyTemplate(id) }) { Text(label) }
            }
        }
        Text("Head and face rendering", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            faceModes.forEach { label ->
                FilterChip(selected = label == selectedFaceMode, onClick = { onFaceMode(label) }, label = { Text(label) })
            }
        }
        Text("Fine-tune face alignment", style = MaterialTheme.typography.titleSmall)
        Text("X: ${String.format(Locale.US, "%.0f", offsetX)}   Y: ${String.format(Locale.US, "%.0f", offsetY)}   Scale: ${String.format(Locale.US, "%.2f", zoom)}")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onNudge(-2f, 0f) }) { Text("←") }
            OutlinedButton(onClick = { onNudge(0f, -2f) }) { Text("↑") }
            OutlinedButton(onClick = { onNudge(0f, 2f) }) { Text("↓") }
            OutlinedButton(onClick = { onNudge(2f, 0f) }) { Text("→") }
            OutlinedButton(onClick = { onZoom(-0.05f) }) { Text("−") }
            OutlinedButton(onClick = { onZoom(0.05f) }) { Text("+") }
            TextButton(onClick = onReset) { Text("Reset") }
        }
    }
}
