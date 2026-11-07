package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.combo.monkeycompanion.MainActivity
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.world.Population
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import com.combo.monkeycompanion.theme.ThemeSkin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    private fun advanced(tab: String? = null) = startActivity(Intent(this, MainActivity::class.java).putExtra("settai_legacy_controls", true).apply { if (tab != null) putExtra("settai_open_tab", tab) })
    private fun summon() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
        } else if (MonkeyOverlayService.running) MonkeyOverlayService.stop(this)
        else MonkeyOverlayService.start(this)
    }

    @Composable
    private fun DesignApp() {
        var page by remember { mutableStateOf(0) }
        var selectedCharacter by remember { mutableStateOf(CharacterRegistry.current.id) }
        var crewMode by remember { mutableStateOf(Population.Mode.of(MindHub.neighbourMode)) }
        var theme by remember { mutableStateOf(ThemeSkin.mode(this)) }
        var search by remember { mutableStateOf("") }
        var exploreCategory by remember { mutableStateOf("All") }
        var settingsPanel by remember { mutableStateOf("main") }
        var overlayRunning by remember { mutableStateOf(MonkeyOverlayService.running) }
        var launchPending by remember { mutableStateOf(false) }
        var launchMessage by remember { mutableStateOf("") }
        var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    overlayRunning = MonkeyOverlayService.running
                    if (overlayRunning) { launchPending = false; launchMessage = "Companion is active" }
                    overlayAllowed = Settings.canDrawOverlays(this@SettaiDesignActivity)
                    selectedCharacter = CharacterRegistry.current.id
                    crewMode = Population.Mode.of(MindHub.neighbourMode)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        var welcomeDismissed by remember {
            mutableStateOf(getSharedPreferences("settai_v3", 0).getBoolean("compose_welcome_dismissed", false))
        }
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
        LaunchedEffect(launchPending) {
            if (launchPending) {
                kotlinx.coroutines.delay(1800)
                overlayRunning = MonkeyOverlayService.running
                launchPending = false
                launchMessage = if (overlayRunning) "Companion started" else "Could not confirm startup. Check permissions."
            }
        }
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB4A8FF), background = bg, surface = surface, onSurface = ink) else lightColorScheme(primary = primary, background = bg, surface = surface, onSurface = ink)) {
            Scaffold(containerColor = bg, bottomBar = {
                NavigationBar(containerColor = surface) {
                    listOf("Home", "Characters", "Create", "Explore", "Settings").forEachIndexed { index, label ->
                        NavigationBarItem(selected = page == index, onClick = { page = index },
                            icon = { Text(listOf("⌂", "♡", "✦", "◎", "⚙")[index], fontSize = 23.sp) },
                            label = { Text(label) })
                    }
                }
            }) { padding ->
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text(when(page) { 0 -> "Your companion"; 1 -> "Characters"; 2 -> "Create something"; 3 -> "Explore Set-Tai"; else -> "Settings" },
                            fontSize = 29.sp, fontWeight = FontWeight.Bold, color = ink)
                        Spacer(Modifier.height(4.dp))
                        Text(when(page) { 0 -> "A little life on your screen"; 1 -> "Find your perfect companion"; 2 -> "Stories, looks and characters"; 3 -> "Everything, easy to find"; else -> "Make Set-Tai yours" },
                            color = muted, fontSize = 14.sp)
                    }
                    when (page) {
                        0 -> {
                            if (!welcomeDismissed) {
                                item {
                                    Panel(surface) {
                                        Text("Welcome to Set-Tai", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ink)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Choose a companion, allow it to appear over other apps, then bring it to life.", color = muted)
                                        Spacer(Modifier.height(14.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { page = 1 }) { Text("Choose character") }
                                            TextButton(onClick = {
                                                getSharedPreferences("settai_v3", 0).edit().putBoolean("compose_welcome_dismissed", true).apply()
                                                welcomeDismissed = true
                                            }) { Text("Got it") }
                                        }
                                    }
                                }
                            }
                            item {
                                Panel(surface) {
                                    Text(if (overlayRunning) "●  ACTIVE ON YOUR SCREEN" else "✦  READY WHEN YOU ARE", color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(18.dp))
                                    Text(CharacterRegistry.byId(selectedCharacter)?.name ?: CharacterRegistry.current.name, color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                    Text(if (overlayRunning) "Your companion is active on screen." else "Bring your companion onto the screen.", color = muted)
                                    Spacer(Modifier.height(8.dp))
                                    Text(if (overlayAllowed) "Overlay permission ready" else "Overlay permission needed to start", color = muted, fontSize = 12.sp)
                                    Spacer(Modifier.height(12.dp))
                                    AndroidView(
                                        factory = { ctx -> CharacterPreviewView(ctx, CharacterRegistry.current) },
                                        update = { it.who = CharacterRegistry.byId(selectedCharacter) ?: CharacterRegistry.current },
                                        modifier = Modifier.fillMaxWidth().height(190.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = {
                                        if (!overlayRunning && overlayAllowed) {
                                            launchPending = true
                                            launchMessage = "Starting companion…"
                                        }
                                        summon()
                                        overlayAllowed = Settings.canDrawOverlays(this@SettaiDesignActivity)
                                        if (overlayRunning) {
                                            overlayRunning = false
                                            launchMessage = "Companion stopped"
                                        }
                                    }, enabled = !launchPending, modifier = Modifier.fillMaxWidth()) {
                                        if (launchPending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        else Text(if (overlayRunning) "Stop companion" else if (!overlayAllowed) "Enable overlay permission" else "Start companion")
                                    }
                                    if (launchMessage.isNotBlank()) Text(launchMessage, color = muted, fontSize = 13.sp)
                                    TextButton(onClick = { advanced() }) { Text("Companion controls & preview") }
                                }
                            }
                            item { Heading("Quick actions", ink) }
                            item { Feature("Choose a character", "Meet your characters and change who appears", "✿", surface, ink, muted) { page = 1 } }
                            item { Feature("Make a show", "Create and play animated stories", "▶", surface, ink, muted) { open(ShowsActivity::class.java) } }
                            item { Feature("Give Brain", "Optional AI and personality settings", "✧", surface, ink, muted) { open(SettingsActivity::class.java) } }
                        }
                        1 -> {
                            item {
                                Text("Choose who lives on your screen. Your memories and settings stay with you.", color = muted, fontSize = 14.sp)
                            }
                            item {
                                Panel(surface) {
                                    Heading("Companions on screen", ink)
                                    Text("Select the number of active companions", color = muted)
                                    Spacer(Modifier.height(10.dp))
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        Population.Mode.entries.forEachIndexed { index, mode ->
                                            SegmentedButton(
                                                selected = crewMode == mode,
                                                onClick = {
                                                    MindHub.setNeighbourMode(this@SettaiDesignActivity, mode)
                                                    MonkeyOverlayService.syncCrew(this@SettaiDesignActivity)
                                                    crewMode = mode
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(index, Population.Mode.entries.size)
                                            ) {
                                                Text(when (mode) {
                                                    Population.Mode.SOLO -> "Solo"
                                                    Population.Mode.DUO -> "Duo"
                                                    Population.Mode.TRIO -> "Trio"
                                                })
                                            }
                                        }
                                    }
                                    Text("Lead: " + CharacterRegistry.current.name, color = ink)
                                    if (crewMode != Population.Mode.SOLO) {
                                        Text("Partners use your saved crew configuration. Customize partners in the crew picker.", color = muted, fontSize = 12.sp)
                                        TextButton(onClick = { advanced("characters") }) { Text("Configure partners") }
                                    }
                                }
                            }
                            item { Heading("Character gallery", ink) }
                            items(CharacterRegistry.allWithCustom, key = { it.id }) { character ->
                                Surface(
                                    onClick = {
                                        ChatHub.settings.characterId = character.id
                                        ChatHub.settings.applyCharacter()
                                        MonkeyOverlayService.syncCrew(this@SettaiDesignActivity)
                                        selectedCharacter = character.id
                                    },
                                    color = if (selectedCharacter == character.id) MaterialTheme.colorScheme.primaryContainer else surface,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AndroidView(
                                            factory = { ctx -> CharacterPreviewView(ctx, character) },
                                            update = { it.who = character; it.picked = selectedCharacter == character.id },
                                            modifier = Modifier.size(width = 88.dp, height = 112.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(character.name, fontWeight = FontWeight.Bold, color = ink)
                                            Text(character.tagline, color = muted, fontSize = 12.sp)
                                        }
                                        if (selectedCharacter == character.id) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                                    }
                                }
                            }
                            item { Feature("Custom character studio", "Make your own companion", "✦", surface, ink, muted) { open(CompanionStudioActivity::class.java) } }
                        }
                        2 -> {
                            item {
                                Panel(surface) {
                                    Text("Make Set-Tai your own", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ink)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Start with a story or design a new companion. Your creations use the existing Set-Tai engines.", color = muted)
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { open(ShowsActivity::class.java) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Create a show")
                                    }
                                }
                            }
                            item { Heading("Creative tools", ink) }
                            item { Feature("Shows & scenes", "Create, edit and play your animated stories", "▶", surface, ink, muted) { open(ShowsActivity::class.java) } }
                            item { Feature("Character studio", "Design a custom companion", "✿", surface, ink, muted) { open(CompanionStudioActivity::class.java) } }
                            item { Feature("Character collection", "Choose your active character", "♡", surface, ink, muted) { page = 1 } }
                        }
                        3 -> {
                            item {
                                OutlinedTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    label = { Text("Search features") },
                                    placeholder = { Text("Try chat, skills or care") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    listOf("All", "Connect", "Discover", "Care", "Tools").forEach { category ->
                                        FilterChip(
                                            selected = exploreCategory == category,
                                            onClick = { exploreCategory = category },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                            }
                            val features = listOf(
                                Triple("Chat", "Connect", "Talk with your companion"),
                                Triple("Skills", "Discover", "Discover abilities and actions"),
                                Triple("Diary", "Care", "Moments and memories"),
                                Triple("World & Care", "Care", "Activities, mood and companion world"),
                                Triple("Shows", "Discover", "Watch and create animated shows"),
                                Triple("Advanced controls", "Tools", "All original Set-Tai controls")
                            )
                            val matching = features.filter { (title, category, description) ->
                                (exploreCategory == "All" || exploreCategory == category) &&
                                    (search.isBlank() || listOf(title, category, description).any { it.contains(search.trim(), ignoreCase = true) })
                            }
                            if (matching.isEmpty()) {
                                item {
                                    Panel(surface) {
                                        Text("No matching features", fontWeight = FontWeight.SemiBold, color = ink)
                                        Text("Try another keyword or choose All.", color = muted)
                                        TextButton(onClick = { search = ""; exploreCategory = "All" }) { Text("Clear filters") }
                                    }
                                }
                            }
                            items(matching, key = { it.first }) { (title, _, description) ->
                                Feature(title, description, when(title) {
                                    "Chat" -> "◉"
                                    "Skills" -> "✦"
                                    "Diary" -> "▤"
                                    "World & Care" -> "☼"
                                    "Shows" -> "▶"
                                    else -> "☷"
                                }, surface, ink, muted) {
                                    when (title) {
                                        "Chat" -> open(ChatActivity::class.java)
                                        "Skills" -> open(SkillsActivity::class.java)
                                        "Diary" -> open(DiaryActivity::class.java)
                                        "World & Care" -> advanced("world")
                                        "Shows" -> open(ShowsActivity::class.java)
                                        else -> advanced()
                                    }
                                }
                            }
                        }
                        else -> {
                            if (settingsPanel != "main") {
                                item {
                                    TextButton(onClick = { settingsPanel = "main" }) { Text("‹ All settings") }
                                    Heading(when(settingsPanel) {
                                        "appearance" -> "Appearance"
                                        "permissions" -> "Permissions & battery"
                                        else -> "Companion settings"
                                    }, ink)
                                }
                            }
                            if (settingsPanel == "main") {
                                item { Feature("Appearance", "Theme and display preferences", "◐", surface, ink, muted) { settingsPanel = "appearance" } }
                                item { Feature("Companion & behaviour", "Motion, personality and preferences", "♡", surface, ink, muted) { advanced("settings") } }
                                item { Feature("Brain & AI", "Optional intelligence features", "✧", surface, ink, muted) { open(SettingsActivity::class.java) } }
                                item { Feature("Permissions & battery", "Control what Set-Tai can access", "♢", surface, ink, muted) { settingsPanel = "permissions" } }
                                item { Feature("Advanced settings", "Full original settings during migration", "⚙", surface, ink, muted) { advanced("settings") } }
                                item { Text("Set-Tai v10.38.8", color = muted, fontSize = 12.sp) }
                            }
                            if (settingsPanel == "permissions") {
                                item {
                                    Panel(surface) {
                                        Heading("Display over other apps", ink)
                                        Text(if (Settings.canDrawOverlays(this@SettaiDesignActivity)) "Allowed" else "Permission required to show your companion", color = muted)
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedButton(onClick = {
                                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                                        }) { Text("Manage overlay permission") }
                                    }
                                }
                                item { Feature("Battery and other permissions", "Open the original guided permission controls", "♢", surface, ink, muted) { advanced("settings") } }
                            }
                            if (settingsPanel == "appearance") {
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
                            }
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
