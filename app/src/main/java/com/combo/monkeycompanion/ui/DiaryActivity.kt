package com.combo.monkeycompanion.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.Switch
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.mind.Drives
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.mind.Personality
import com.combo.monkeycompanion.mind.Traits
import java.util.Locale

/**
 * Compact horizontal bar chart for Settai's 6 internal drives + focus-respect level.
 * Used both on the home screen mood card and inside [DiaryActivity].
 */
class MoodBarsView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    var drives: Drives = Drives()
        set(v) { field = v; invalidate() }
    var focus: Float = 0f
        set(v) { field = v; invalidate() }

    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40); textSize = 12f * d
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C); textSize = 11.5f * d; textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0xF3, 0xE5, 0xD0) }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast((240 * d).toInt())
        val h = (7 * 22 * d + 8 * d).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(c: Canvas) {
        val rows = listOf(
            Triple("⚡ Energy", drives.energy, com.combo.monkeycompanion.theme.Th.rgb(0x43, 0xA0, 0x47)),
            Triple("🧭 Curiosity", drives.curiosity, com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x88, 0xE5)),
            Triple("💛 Social", drives.social, com.combo.monkeycompanion.theme.Th.rgb(0xFB, 0x8C, 0x00)),
            Triple("😈 Boredom", drives.boredom, com.combo.monkeycompanion.theme.Th.rgb(0x8E, 0x24, 0xAA)),
            Triple("😌 Content", drives.contentment, com.combo.monkeycompanion.theme.Th.rgb(0x00, 0x89, 0x7B)),
            Triple("😤 Grumpy", drives.annoyance, com.combo.monkeycompanion.theme.Th.rgb(0xE5, 0x39, 0x35)),
            Triple("🤫 Focus respect", focus, com.combo.monkeycompanion.theme.Th.rgb(0x54, 0x6E, 0x7A)),
        )
        val rowH = 22f * d
        val labelW = 112f * d
        val numW = 36f * d
        val barL = labelW
        val barR = (width - numW - 4f * d).coerceAtLeast(barL + 40f * d)
        val barH = 10f * d
        rows.forEachIndexed { i, (name, v, col) ->
            val cy = (i + 0.5f) * rowH + 2f * d
            c.drawText(name, 2f * d, cy - (labelP.descent() + labelP.ascent()) / 2f, labelP)
            r.set(barL, cy - barH / 2f, barR, cy + barH / 2f)
            c.drawRoundRect(r, barH / 2f, barH / 2f, trackP)
            val fw = ((barR - barL) * v.coerceIn(0f, 1f)).coerceAtLeast(if (v > 0.01f) barH else 0f)
            if (fw > 0f) {
                fillP.color = col
                r.set(barL, cy - barH / 2f, barL + fw, cy + barH / 2f)
                c.drawRoundRect(r, barH / 2f, barH / 2f, fillP)
            }
            c.drawText("%d%%".format(Locale.ROOT, (v.coerceIn(0f, 1f) * 100).toInt()), width - 2f * d, cy - (valP.descent() + valP.ascent()) / 2f, valP)
        }
    }
}

/**
 * v10.38.14: Diary as one Material 3 screen. Character chips on top, then 4 tabs:
 * Overview (what it's thinking, mood bars, favourites), Behaviour, Memory (limits & privacy) and Diary (moments).
 * Everything is saved for the chosen character only (it never switches your active companion).
 */
class DiaryActivity : androidx.activity.ComponentActivity() {
    private var whoId by androidx.compose.runtime.mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        MindHub.init(this)
        whoId = (CharacterRegistry.byId(intent?.getStringExtra("character")) ?: CharacterRegistry.current).id
        setContent {
            val dark = com.combo.monkeycompanion.theme.ThemeSkin.isDark(this)
            androidx.compose.material3.MaterialTheme(colorScheme = SettaiDesignTokens.scheme(dark)) { DiaryScreen() }
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun DiaryScreen() {
        val who: Character = CharacterRegistry.byId(whoId) ?: CharacterRegistry.current
        var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val tabs = listOf("Overview", "Behaviour", "Memory", "Diary")
        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { androidx.compose.material3.Text("${who.name}'s diary") },
                    navigationIcon = {
                        androidx.compose.material3.TextButton(onClick = { finish() }) {
                            androidx.compose.material3.Text("←", fontSize = 22.sp)
                        }
                    }
                )
            }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                // Which character
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (c in CharacterRegistry.allWithCustom) {
                        androidx.compose.material3.FilterChip(
                            selected = c.id == who.id,
                            onClick = { whoId = c.id },
                            label = { androidx.compose.material3.Text(c.name) }
                        )
                    }
                }
                androidx.compose.material3.TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, t ->
                        androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i },
                            text = { androidx.compose.material3.Text(t) })
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    androidx.compose.runtime.key(who.id) {
                        when (tab) {
                            0 -> OverviewTab(who)
                            1 -> Panel { CharacterBehaviourPanel(who.id) }
                            2 -> Panel { CharacterMemoryPanel(who.id) }
                            else -> DiaryTab(who)
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Panel(content: @androidx.compose.runtime.Composable () -> Unit) {
        androidx.compose.material3.Card(Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SettaiDesignTokens.cornerCardDp.dp)) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }

    @androidx.compose.runtime.Composable
    private fun OverviewTab(who: Character) {
        val m = MindHub.mindFor(who.id)
        if (m == null) { androidx.compose.material3.Text("No diary for ${who.name} yet."); return }
        val now = System.currentTimeMillis()
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx -> CharacterPreviewView(ctx, who) },
                    modifier = Modifier.size(width = 86.dp, height = 96.dp)
                )
                androidx.compose.material3.Text("💭 ${m.thinking(now)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp).weight(1f))
            }
        }
        Panel {
            androidx.compose.material3.Text("Mood right now", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            val dr = m.drives
            val rows = listOf(
                Triple("⚡ Energy", dr.energy, 0xFF43A047), Triple("🧭 Curiosity", dr.curiosity, 0xFF1E88E5),
                Triple("💛 Social", dr.social, 0xFFFB8C00), Triple("😈 Boredom", dr.boredom, 0xFF8E24AA),
                Triple("😌 Content", dr.contentment, 0xFF00897B), Triple("😤 Grumpy", dr.annoyance, 0xFFE53935),
                Triple("🤫 Focus respect", m.focus(now), 0xFF546E7A)
            )
            for ((name, v, col) in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(name, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(120.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { v.coerceIn(0f, 1f) },
                        color = androidx.compose.ui.graphics.Color(col),
                        modifier = Modifier.weight(1f).height(8.dp)
                    )
                    androidx.compose.material3.Text("${(v.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(44.dp).padding(start = 8.dp))
                }
            }
        }
        val fav = m.personality.fav
        Panel {
            androidx.compose.material3.Text("Favourites", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
            val lines = listOf(
                "Hangout" to fav.corner.label, "Nap spot" to fav.napSpot.label,
                "Loves" to "${fav.favApp.label} apps", "Dislikes" to "${fav.hatedApp.label} apps",
                "Snack" to fav.snack
            ) + (if (fav.power.isNotBlank()) listOf("Power" to fav.power) else emptyList())
            for ((k, v) in lines) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    androidx.compose.material3.Text(k, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp))
                    androidx.compose.material3.Text(v)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DiaryTab(who: Character) {
        val m = MindHub.mindFor(who.id)
        if (m == null) { androidx.compose.material3.Text("No diary for ${who.name} yet."); return }
        val diary = m.diary
        var rev by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var adding by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        var clearing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(onClick = { note = ""; adding = true }, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text("Add memory")
            }
            androidx.compose.material3.OutlinedButton(onClick = { clearing = true }, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text("Clear unpinned")
            }
        }
        androidx.compose.material3.Text(
            "Pinned memories and rules are never deleted. Example: \"Always call me Appa and cheer me on before meetings.\"",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.runtime.key(rev) { CharacterDiaryTimeline(who.id) }
        if (adding) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { adding = false },
                title = { androidx.compose.material3.Text("Pin a memory or rule for ${who.name}") },
                text = {
                    androidx.compose.material3.OutlinedTextField(value = note, onValueChange = { note = it },
                        placeholder = { androidx.compose.material3.Text("E.g. Always call me Appa") }, minLines = 2)
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val t = note.trim()
                        if (t.isNotEmpty()) {
                            diary.addCustomNote(t, System.currentTimeMillis(), pinned = true)
                            MindHub.flush(); rev++
                            Toast.makeText(this, "Pinned to ${who.name}'s diary", Toast.LENGTH_SHORT).show()
                        }
                        adding = false
                    }) { androidx.compose.material3.Text("Save & pin") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { adding = false }) { androidx.compose.material3.Text("Cancel") } }
            )
        }
        if (clearing) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { clearing = false },
                title = { androidx.compose.material3.Text("Clear unpinned memories?") },
                text = { androidx.compose.material3.Text("Pinned memories stay. This can't be undone.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        diary.clearUnpinned(); MindHub.flush(); rev++; clearing = false
                    }) { androidx.compose.material3.Text("Clear") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { clearing = false }) { androidx.compose.material3.Text("Cancel") } }
            )
        }
    }
}
