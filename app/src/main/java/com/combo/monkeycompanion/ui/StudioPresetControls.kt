package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioPresetControls(selected: List<Boolean>, onSelect: (Int) -> Unit) {
    val labels = listOf("35yr Mid-Body", "Bulky / Heavy", "Woman (Long Hair)", "5yr Girl", "5yr Boy", "Slim / Lean")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (column in 0..1) {
                    val index = row * 2 + column
                    FilterChip(
                        selected = selected.getOrElse(index) { false },
                        onClick = { onSelect(index) },
                        label = { Text(labels[index], maxLines = 2) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
