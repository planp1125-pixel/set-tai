package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.combo.monkeycompanion.MainActivity
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import com.combo.monkeycompanion.theme.ThemeSkin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Android presentation only. Desktop and shared engines remain independent of Compose.
 * Routes launch existing feature activities until each screen is migrated.
 */
class SettaiDesignActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignApp() }
    }

    private fun open(type: Class<out Activity>) = startActivity(Intent(this, type))
    private fun advanced() = startActivity(Intent(this, MainActivity::class.java).putExtra("settai_legacy_controls", true))
    private fun summon() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
        } else MonkeyOverlayService.start(this)
    }

    @Composable
    private fun DesignApp() {
        var page by remember { mutableStateOf(0) }
        var theme by remember { mutableStateOf(ThemeSkin.mode(this)) }
        var search by remember { mutableStateOf("") }
        val dark = when (theme) {
            ThemeSkin.DARK -> true
            ThemeSkin.LIGHT -> false
            else -> ThemeSkin.isDark(this)
        }
        val bg = if (dark) Color(0xFF10121A) else Color(0xFFF8F9FD)
        val surface = if (dark) Color(0xFF202331) else Color.White
        val ink = if (dark) Color(0xFFF4F4FA) else Color(0xFF20233B)
        val muted = if (dark) Color(0xFFB6B9CA) else Color(0xFF646B80)
        val primary = Color(0xFF7160E8)
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB4A8FF), background = bg, surface = surface, onSurface = ink) else lightColorScheme(primary = primary, background = bg, surface = surface, onSurface = ink)) {
            Scaffold(containerColor = bg, bottomBar = {
                NavigationBar(containerColor = surface) {
                    listOf("Home", "Create", "Explore", "Settings").forEachIndexed { index, label ->
                        NavigationBarItem(selected = page == index, onClick = { page = index },
                            icon = { Text(listOf("⌂", "✦", "◎", "⚙")[index], fontSize = 23.sp) },
                            label = { Text(label) })
                    }
                }
            }) { padding ->
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text(when(page) { 0 -> "Your companion"; 1 -> "Create something"; 2 -> "Explore Set-Tai"; else -> "Settings" },
                            fontSize = 29.sp, fontWeight = FontWeight.Bold, color = ink)
                        Spacer(Modifier.height(4.dp))
                        Text(when(page) { 0 -> "A little life on your screen"; 1 -> "Stories, looks and characters"; 2 -> "Everything, easy to find"; else -> "Make Set-Tai yours" },
                            color = muted, fontSize = 14.sp)
                    }
                    when (page) {
                        0 -> {
                            item {
                                Panel(surface) {
                                    Text("✦  READY WHEN YOU ARE", color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(18.dp))
                                    Text(CharacterRegistry.current.name, color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                    Text("Bring your companion onto the screen.", color = muted)
                                    Spacer(Modifier.height(18.dp))
                                    Button(onClick = { summon() }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (MonkeyOverlayService.running) "Companion is running" else "Start companion")
                                    }
                                    TextButton(onClick = { advanced() }) { Text("Companion controls & preview") }
                                }
                            }
                            item { Heading("Quick actions", ink) }
                            item { Feature("Choose a character", "Meet your characters and change who appears", "✿", surface, ink, muted) { advanced() } }
                            item { Feature("Make a show", "Create and play animated stories", "▶", surface, ink, muted) { open(ShowsActivity::class.java) } }
                            item { Feature("Give Brain", "Optional AI and personality settings", "✧", surface, ink, muted) { open(SettingsActivity::class.java) } }
                        }
                        1 -> {
                            item { Feature("Create a show", "Build scenes and tell a story", "▶", surface, ink, muted) { open(ShowsActivity::class.java) } }
                            item { Feature("Character studio", "Create a custom companion", "✿", surface, ink, muted) { open(CompanionStudioActivity::class.java) } }
                            item { Feature("Choose characters", "Characters, sizes and combinations", "♡", surface, ink, muted) { advanced() } }
                        }
                        2 -> {
                            item {
                                OutlinedTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    label = { Text("Search features") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                            item { if ("chat".contains(search, ignoreCase = true) || "talk".contains(search, ignoreCase = true)) Feature("Chat", "Talk with your companion", "◉", surface, ink, muted) { open(ChatActivity::class.java) } }
                            item { if ("skills".contains(search, ignoreCase = true) || "abilities".contains(search, ignoreCase = true)) Feature("Skills", "Discover abilities and actions", "✦", surface, ink, muted) { open(SkillsActivity::class.java) } }
                            item { if ("diary".contains(search, ignoreCase = true) || "memories".contains(search, ignoreCase = true)) Feature("Diary", "Moments and memories", "▤", surface, ink, muted) { open(DiaryActivity::class.java) } }
                            item { if ("world".contains(search, ignoreCase = true) || "care".contains(search, ignoreCase = true)) Feature("World & Care", "Activities, mood and companion world", "☼", surface, ink, muted) { advanced() } }
                            item { if (search.isBlank() || "all original features advanced controls".contains(search, ignoreCase = true)) Feature("All original features", "Access every existing control during migration", "☷", surface, ink, muted) { advanced() } }
                        }
                        else -> {
                            item {
                                Panel(surface) {
                                    Heading("Appearance", ink)
                                    Text("App theme", color = muted, fontSize = 13.sp)
                                    listOf(ThemeSkin.SYSTEM to "Use device settings", ThemeSkin.LIGHT to "Light", ThemeSkin.DARK to "Dark").forEach { (value, label) ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                            ThemeSkin.setMode(this@SettaiDesignActivity, value); theme = value
                                        }.padding(vertical = 7.dp)) {
                                            RadioButton(selected = theme == value, onClick = {
                                                ThemeSkin.setMode(this@SettaiDesignActivity, value); theme = value
                                            })
                                            Text(label, color = ink)
                                        }
                                    }
                                }
                            }
                            item { Feature("Companion & behaviour", "Motion, personality and preferences", "♡", surface, ink, muted) { open(SettingsActivity::class.java) } }
                            item { Feature("Brain & AI", "Optional intelligence features", "✧", surface, ink, muted) { open(SettingsActivity::class.java) } }
                            item { Feature("Permissions & battery", "Control what Set-Tai can access", "♢", surface, ink, muted) { advanced() } }
                            item { Feature("Advanced settings", "Full existing settings while migration continues", "⚙", surface, ink, muted) { advanced() } }
                            item { Text("Set-Tai v10.38.2 • UI preview", color = muted, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Panel(color: Color, content: @Composable ColumnScope.() -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(24.dp)).padding(20.dp), content = content)
    }

    @Composable
    private fun Heading(value: String, color: Color) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = color)
    }

    @Composable
    private fun Feature(title: String, subtitle: String, symbol: String, surface: Color, ink: Color, muted: Color, action: () -> Unit) {
        Surface(onClick = action, color = surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, fontSize = 27.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = ink)
                    Text(subtitle, fontSize = 12.sp, color = muted)
                }
                Text("›", fontSize = 26.sp, color = muted)
            }
        }
    }
}
