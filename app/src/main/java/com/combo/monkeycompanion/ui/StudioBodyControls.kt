package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.character.AgeGroup
import com.combo.monkeycompanion.character.BodyBuild

@Composable
internal fun StudioBodyControls(build: BodyBuild, width: Float, shoulders: Float, age: AgeGroup,
    onBuild: (BodyBuild) -> Unit, onWidth: (Float) -> Unit, onShoulders: (Float) -> Unit, onAge: (AgeGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Body build", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyBuild.entries.forEach { item -> FilterChip(selected = build == item, onClick = { onBuild(item) }, label = { Text(item.label) }) }
        }
        Text("Body width", style = MaterialTheme.typography.labelLarge)
        Slider(value = width.coerceIn(0.7f, 1.55f), onValueChange = onWidth, valueRange = 0.7f..1.55f)
        Text("Shoulder width", style = MaterialTheme.typography.labelLarge)
        Slider(value = shoulders.coerceIn(0.75f, 1.5f), onValueChange = onShoulders, valueRange = 0.75f..1.5f)
        Text("Age proportions", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgeGroup.entries.forEach { item -> FilterChip(selected = age == item, onClick = { onAge(item) }, label = { Text(item.label) }) }
        }
    }
}
