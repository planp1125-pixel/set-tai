package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compose controls for the existing native stage; no rendering logic is replaced. */
@Composable
internal fun StudioInspectionControls(
    stage: StudioLiveStageView,
    openFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var split by remember(stage) { mutableStateOf(stage.splitCompareMode) }
    var mesh by remember(stage) { mutableStateOf(stage.showFaceMesh) }
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = split,
            onClick = {
                stage.splitCompareMode = !stage.splitCompareMode
                if (stage.splitCompareMode && stage.zoomLevel < 2.2f) stage.zoomLevel = 3.0f
                split = stage.splitCompareMode
            },
            label = { Text("Split compare") }
        )
        FilterChip(
            selected = mesh,
            onClick = {
                stage.showFaceMesh = !stage.showFaceMesh
                if (stage.showFaceMesh) {
                    stage.moveMeshMode = true
                    if (!stage.splitCompareMode) {
                        stage.splitCompareMode = true
                        stage.zoomLevel = 3.0f
                    }
                }
                mesh = stage.showFaceMesh
                split = stage.splitCompareMode
            },
            label = { Text("68-point mesh") }
        )
        OutlinedButton(onClick = openFullscreen) { Text("Fullscreen editor") }
    }
}
