package com.combo.monkeycompanion.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Material 3 navigation for the existing Studio editor sections.
 * The 60fps StudioLiveStageView remains a native Android View.
 */
@Composable
internal fun StudioMaterialTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf("Face", "Body", "Style", "Motion", "Behaviour", "Powers")
    ScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(labels.indices),
        modifier = modifier.fillMaxWidth(),
        edgePadding = androidx.compose.ui.unit.Dp(0f),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(label) },
            )
        }
    }
}
