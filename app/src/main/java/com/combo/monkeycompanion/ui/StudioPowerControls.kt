package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.character.CustomPowerCatalog
import com.combo.monkeycompanion.character.FlightStyle

@Composable
internal fun StudioPowerControls(
    flight: FlightStyle, powers: Set<String>, onFlight: (FlightStyle) -> Unit,
    onAll: () -> Unit, onTop: () -> Unit, onToggle: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Flight & movement", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlightStyle.entries.forEach { FilterChip(selected = flight == it, onClick = { onFlight(it) }, label = { Text(it.label) }) }
        }
        Text("Superpowers (${powers.size}/${CustomPowerCatalog.OPTIONS.size})", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAll) { Text("Select all") }
            OutlinedButton(onClick = onTop) { Text("Top 4") }
        }
        CustomPowerCatalog.OPTIONS.forEach { option ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(option.label, modifier = Modifier.weight(1f))
                Checkbox(checked = option.id in powers,
                    onCheckedChange = { onToggle(option.id, it) },
                    enabled = option.id !in powers || powers.size > 1)
            }
        }
    }
}
