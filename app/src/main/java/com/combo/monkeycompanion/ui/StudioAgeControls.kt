package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.character.AgeGroup

/** Selects an age group; the Activity remains responsible for applying and saving the spec. */
@Composable
internal fun StudioAgeControls(
    selected: AgeGroup,
    onSelect: (AgeGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AgeGroup.entries.forEach { age ->
            FilterChip(
                selected = selected == age,
                onClick = { onSelect(age) },
                label = { Text(age.shortLabel) }
            )
        }
    }
}
