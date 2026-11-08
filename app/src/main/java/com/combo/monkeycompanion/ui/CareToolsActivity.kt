package com.combo.monkeycompanion.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.combo.monkeycompanion.agent.ToolStore
import com.combo.monkeycompanion.brain.CareTools

/** v10.38.23: Care tools (water, tablet, eye break, …) on their own Material 3 page, opened from Home. */
class CareToolsActivity : androidx.activity.ComponentActivity() {
    private var rev by mutableIntStateOf(0)
    private var timeFor by mutableStateOf<CareTools.Tool?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(r: Int) {
        SettaiScreen("Care tools", onBack = { finish() }, subtitle = "Your character reminds you, in its own way.", rev = r) {
            for (tool in CareTools.Tool.entries) {
                val st = ToolStore.get(this@CareToolsActivity, tool)
                SCard("${tool.icon}  ${tool.label}") {
                    SSwitch(if (st.on) "On · ${CareTools.describe(tool, st)}" else "Off", st.on) { on ->
                        ToolStore.put(this@CareToolsActivity, tool, CareTools.toggle(tool, st, on, System.currentTimeMillis())); rev++
                    }
                    when (tool.kind) {
                        CareTools.Kind.INTERVAL -> {
                            val opts = listOf(30, 60, 90, 120)
                            SChoice("How often", opts.map { "Every $it min" }, opts.indexOf(st.value).coerceAtLeast(0)) { i ->
                                ToolStore.put(this@CareToolsActivity, tool, st.copy(value = opts[i], lastFiredMs = System.currentTimeMillis())); rev++
                            }
                        }
                        CareTools.Kind.DAILY -> SSecondary("Time: %02d:%02d".format(st.value / 60, st.value % 60)) { timeFor = tool }
                        CareTools.Kind.ONCE -> {
                            val opts = listOf(5, 10, 15, 30, 60)
                            SChoice("In", opts.map { "$it min" }, opts.indexOf(st.value).coerceAtLeast(0)) { i ->
                                ToolStore.put(this@CareToolsActivity, tool, st.copy(value = opts[i])); rev++
                            }
                        }
                    }
                }
            }
        }
        timeFor?.let { tool ->
            val st = ToolStore.get(this, tool)
            val tp = rememberTimePickerState(initialHour = st.value / 60, initialMinute = st.value % 60, is24Hour = false)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { timeFor = null },
                title = { Text("${tool.icon} ${tool.label} time") },
                text = { TimePicker(state = tp) },
                confirmButton = { TextButton(onClick = { ToolStore.put(this, tool, st.copy(value = tp.hour * 60 + tp.minute)); timeFor = null; rev++ }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { timeFor = null }) { Text("Cancel") } }
            )
        }
    }
}
