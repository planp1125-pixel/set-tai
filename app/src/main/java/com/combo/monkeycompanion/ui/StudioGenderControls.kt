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
import com.combo.monkeycompanion.character.CompanionGender

/** Editor-only control: persistence and preview updates stay with CompanionStudioActivity. */
@Composable
internal fun StudioGenderControls(
    selected: CompanionGender,
    onSelect: (CompanionGender) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            CompanionGender.GIRL_CHILD,
            CompanionGender.BOY_CHILD,
            CompanionGender.FEMALE,
            CompanionGender.MALE
        ).forEach { gender ->
            FilterChip(
                selected = selected == gender,
                onClick = { onSelect(gender) },
                label = { Text(gender.shortLabel) }
            )
        }
    }
}
