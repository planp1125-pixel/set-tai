package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Material 3 controls for the existing Studio stage's animation demos.
 * The stage retains all animation timing, rendering and state ownership.
 */
@Composable
internal fun StudioDemoControls(stage: StudioLiveStageView, modifier: Modifier = Modifier) {
    var selected by remember(stage) { mutableStateOf(stage.demoMode) }
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StudioLiveStageView.DemoMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = {
                    stage.demoMode = mode
                    selected = mode
                },
                label = { Text(mode.label) }
            )
        }
    }
}
