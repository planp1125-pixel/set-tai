package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Native Material 3 zoom controls; the existing StudioLiveStageView owns rendering and gestures. */
@Composable
internal fun StudioPreviewControls(stage: StudioLiveStageView, modifier: Modifier = Modifier) {
    var zoom by remember(stage) { mutableFloatStateOf(stage.zoomLevel) }
    val zooms = listOf(1f to "1× Full", 2f to "2× Upper", 3.5f to "3.5× Face", 5f to "5× Ultra")
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        zooms.forEach { (level, label) ->
            FilterChip(
                selected = abs(zoom - level) < 0.25f,
                onClick = {
                    stage.zoomLevel = level
                    zoom = level
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
            )
        }
        FilterChip(
            selected = false,
            onClick = {
                stage.resetView()
                zoom = stage.zoomLevel
            },
            label = { Text("Reset view") }
        )
    }
}
