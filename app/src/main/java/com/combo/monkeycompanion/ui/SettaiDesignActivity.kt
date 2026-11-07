package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.app.AlertDialog
import android.app.TimePickerDialog
import com.combo.monkeycompanion.brain.CareTools
import com.combo.monkeycompanion.agent.ToolStore
import com.combo.monkeycompanion.overlay.CompanionSize
import com.combo.monkeycompanion.wallpaper.SettaiWallpaperService
import com.combo.monkeycompanion.wallpaper.SpotsEditorActivity
import com.combo.monkeycompanion.wallpaper.WallpaperPrefs
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.combo.monkeycompanion.fun5.PairActivity
import com.combo.monkeycompanion.smash.SmashActivity
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
    private fun settings(section: String) = startActivity(Intent(this, SettingsActivity::class.java).putExtra("settai_settings_section", section))
    private fun wardrobe() = startActivity(Intent(this, WardrobeActivity::class.java).putExtra("character", CharacterRegistry.current.id))
    private fun setSize(size: Float) {
        CompanionSize.set(this, CharacterRegistry.current.id, size)
        if (MonkeyOverlayService.running) {
            MonkeyOverlayService.stop(this)
            Handler(mainLooper).postDelayed({ MonkeyOverlayService.start(this) }, 600L)
        }
    }
    private fun wallpaper() {
        if (MonkeyOverlayService.running) MonkeyOverlayService.stop(this)
        runCatching { startActivity(SettaiWallpaperService.chooserIntent(this)) }
            .onFailure { runCatching { startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } }
    }
    private fun wallpaperBackground() {
        val colors = intArrayOf(0xFF2E5E4E.toInt(), 0xFF1E2A44.toInt(), 0xFFE9D8B4.toInt(), 0xFF3A2A4A.toInt())
        AlertDialog.Builder(this).setTitle("Wallpaper background").setItems(arrayOf("Soft jungle", "Deep green", "Night blue", "Warm sand", "Dusk purple", "My own image…")) { _, index ->
            when(index) {
                0 -> WallpaperPrefs.setBackground(this, WallpaperPrefs.BG_JUNGLE)
                in 1..4 -> WallpaperPrefs.setBackground(this, WallpaperPrefs.BG_COLOR, color = colors[index - 1])
                else -> {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 52)
                }
            }
        }.show()
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode == 52 && resultCode == RESULT_OK) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            WallpaperPrefs.setBackground(this, WallpaperPrefs.BG_IMAGE, image = uri.toString())
        }
    }
    private fun editTool(tool: CareTools.Tool, refresh: () -> Unit) {
        val st = ToolStore.get(this, tool)
        when (tool.kind) {
            CareTools.Kind.INTERVAL -> {
                val opts = listOf(30, 60, 90, 120)
                AlertDialog.Builder(this).setTitle(tool.label).setItems(opts.map { "Every $it min" }.toTypedArray()) { _, i ->
                    ToolStore.put(this, tool, st.copy(value = opts[i], lastFiredMs = System.currentTimeMillis())); refresh()
                }.show()
            }
            CareTools.Kind.DAILY -> TimePickerDialog(this, { _, h, m ->
                ToolStore.put(this, tool, st.copy(value = h * 60 + m)); refresh()
            }, st.value / 60, st.value % 60, false).show()
            else -> Toast.makeText(this, CareTools.describe(tool, st), Toast.LENGTH_SHORT).show()
        }
    }
    private fun advanced(tab: String? = null) = startActivity(Intent(this, MainActivity::class.java).putExtra("settai_legacy_controls", true).apply { if (tab != null) putExtra("settai_open_tab", tab) })
    private fun syncActiveCrew() { if (MonkeyOverlayService.running) MonkeyOverlayService.syncCrew(this) }
    private fun v5(cmd: String) {
        if (!MonkeyOverlayService.running) {
            Toast.makeText(this, "Start your companion first", Toast.LENGTH_SHORT).show()
            return
        }
        startService(Intent(this, MonkeyOverlayService::class.java)
            .setAction(MonkeyOverlayService.ACTION_V5).putExtra("cmd", cmd))
    }
    private fun screenSmash() {
        moveTaskToBack(true)
        Handler(Looper.getMainLooper()).postDelayed({ SmashActivity.launch(applicationContext) }, 450L)
    }
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
        var partnerPicker by remember { mutableIntStateOf(0) }
        var partnerOne by remember { mutableStateOf(MindHub.customPartner1Id) }
        var partnerTwo by remember { mutableStateOf(MindHub.customPartner2Id) }
        var theme by remember { mutableStateOf(ThemeSkin.mode(this)) }
        var search by remember { mutableStateOf("") }
        var size by remember(selectedCharacter) { mutableFloatStateOf(CompanionSize.get(this, selectedCharacter)) }
        var toolRefresh by remember { mutableIntStateOf(0) }
        var exploreCategory by remember { mutableStateOf("All") }
        var settingsPanel by remember { mutableStateOf("main") }
        var lastPage by remember { mutableIntStateOf(0) }
        BackHandler(enabled = settingsPanel != "main" || partnerPicker != 0 || page != 0) {
            when {
                partnerPicker != 0 -> partnerPicker = 0
                page == 4 && settingsPanel != "main" -> settingsPanel = "main"
                else -> { page = if (page == lastPage) 0 else lastPage; lastPage = 0; settingsPanel = "main" }
            }
        }
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
                    partnerOne = MindHub.customPartner1Id
                    partnerTwo = MindHub.customPartner2Id
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
        val bg = if (dark) Color(0xFF171719) else Color(0xFFF1F1F1)
        val surface = if (dark) Color(0xFF27272A) else Color.White
        val ink = if (dark) Color(0xFFF3F3F3) else Color(0xFF282828)
        val muted = if (dark) Color(0xFFB0B0B0) else Color(0xFF777777)
        val primary = if (dark) Color(0xFFE05A64) else Color(0xFFB73540)
        LaunchedEffect(launchPending) {
            if (launchPending) {
                kotlinx.coroutines.delay(1800)
                overlayRunning = MonkeyOverlayService.running
                launchPending = false
                launchMessage = if (overlayRunning) "Companion started" else "Could not confirm startup. Check permissions."
            }
        }
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = primary, onPrimary = Color(0xFF171719), background = bg, surface = surface, onSurface = ink) else lightColorScheme(primary = primary, onPrimary = Color.White, background = bg, surface = surface, onSurface = ink)) {
            Scaffold(containerColor = bg, bottomBar = {
                NavigationBar(containerColor = surface) {
                    listOf("Home", "Characters", "Create", "Explore", "Settings").forEachIndexed { index, label ->
                        NavigationBarItem(selected = page == index, onClick = { lastPage = page; page = index; partnerPicker = 0; settingsPanel = "main" },
                            icon = { Icon(listOf(Icons.Filled.Home, Icons.Filled.Face, Icons.Filled.AddCircle, Icons.Filled.Search, Icons.Filled.Settings)[index], contentDescription = label) },
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
                                        update = { val target = CharacterRegistry.byId(selectedCharacter) ?: CharacterRegistry.current; if (it.who.id != target.id) it.who = target },
                                        modifier = Modifier.fillMaxWidth().height(190.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text("Companion size", color = muted, fontSize = 13.sp)
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        CompanionSize.CHOICES.forEachIndexed { index, choice ->
                                            SegmentedButton(selected = size == choice.first, onClick = { setSize(choice.first); size = choice.first },
                                                shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(choice.second) }
                                        }
                                    }
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
                                    TextButton(onClick = { page = 1 }) { Text("Manage companions") }
                                }
                            }
                            item { Heading("Care tools", ink) }
                            item {
                                Panel(surface) {
                                    val currentRevision = toolRefresh
                                    key(currentRevision) { CareTools.Tool.entries.forEach { tool ->
                                        val st = ToolStore.get(this@SettaiDesignActivity, tool)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tool.icon + " " + tool.label, modifier = Modifier.weight(1f), color = ink)
                                            Switch(checked = st.on, onCheckedChange = { on ->
                                                ToolStore.put(this@SettaiDesignActivity, tool, CareTools.toggle(tool, st, on, System.currentTimeMillis()))
                                                toolRefresh++
                                            })
                                        }
                                        TextButton(onClick = { editTool(tool) { toolRefresh++ } }) {
                                            Text(CareTools.describe(tool, st))
                                        }
                                    } }
                                }
                            }
                            item { Heading("Quick actions", ink) }
                            item { Feature("Choose a character", "Meet your characters and change who appears", "✿", surface, ink, muted) { page = 1 } }
                            item { Feature("Show me tricks", "Watch the companion's interactive demonstration", "✦", surface, ink, muted) { v5("onboard") } }
                            item { Feature("What You Missed", "See your notification summary", "▤", surface, ink, muted) { v5("notif_card_demo") } }
                            item { Feature("Make a show", "Create and play animated stories", "▶", surface, ink, muted) { page = 2 } }
                            item { Feature("Give Brain", "Optional AI and personality settings", "✧", surface, ink, muted) { page = 4; settingsPanel = "brain" } }
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
                                                    syncActiveCrew()
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
                                        Text("Choose each partner below. Auto selects from your saved characters.", color = muted, fontSize = 12.sp)
                                        OutlinedButton(onClick = { partnerPicker = 1 }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Partner 1: " + (CharacterRegistry.byId(partnerOne)?.name ?: "Auto"))
                                        }
                                        if (crewMode == Population.Mode.TRIO) {
                                            OutlinedButton(onClick = { partnerPicker = 2 }, modifier = Modifier.fillMaxWidth()) {
                                                Text("Partner 2: " + (CharacterRegistry.byId(partnerTwo)?.name ?: "Auto"))
                                            }
                                        }
                                    }
                                }
                            }
                            if (partnerPicker != 0) {
                                item {
                                    Panel(surface) {
                                        Heading("Choose partner " + partnerPicker, ink)
                                        TextButton(onClick = { partnerPicker = 0 }) { Text("Cancel") }
                                        val candidates = listOf(null) + CharacterRegistry.allWithCustom.filter {
                                            it.id != selectedCharacter && (partnerPicker != 2 || it.id != partnerOne)
                                        }
                                        candidates.forEach { candidate ->
                                            TextButton(onClick = {
                                                val chosen = candidate?.id
                                                val p1 = if (partnerPicker == 1) chosen else partnerOne
                                                val p2 = (if (partnerPicker == 2) chosen else partnerTwo)?.takeIf { it != p1 }
                                                MindHub.setCrew(this@SettaiDesignActivity, selectedCharacter, crewMode, p1, p2)
                                                partnerOne = p1
                                                partnerTwo = p2
                                                syncActiveCrew()
                                                partnerPicker = 0
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Text(candidate?.name ?: "Auto", color = ink)
                                            }
                                        }
                                    }
                                }
                            }
                            item { Heading("Character gallery", ink) }
                            items(CharacterRegistry.allWithCustom.filter { it.id != "chittu" }, key = { it.id }) { character ->
                                Surface(
                                    onClick = {
                                        ChatHub.settings.characterId = character.id
                                        ChatHub.settings.applyCharacter()
                                        if (character.id == "billa") {
                                            val mode = if (crewMode == Population.Mode.TRIO) Population.Mode.TRIO else Population.Mode.DUO
                                            val third = partnerTwo?.takeIf { it != "billa" && it != "chittu" }
                                            MindHub.setCrew(this@SettaiDesignActivity, "billa", mode, "chittu", third)
                                            crewMode = mode
                                            partnerOne = "chittu"
                                            partnerTwo = if (mode == Population.Mode.TRIO) third else null
                                        }
                                        syncActiveCrew()
                                        selectedCharacter = character.id
                                    },
                                    color = if (selectedCharacter == character.id) MaterialTheme.colorScheme.primaryContainer else surface,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AndroidView(
                                            factory = { ctx -> CharacterPreviewView(ctx, character) },
                                            update = { if (it.who.id != character.id) it.who = character; it.picked = selectedCharacter == character.id },
                                            modifier = Modifier.size(width = 88.dp, height = 112.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(if (character.id == "billa") "Billa & Chittu" else character.name, fontWeight = FontWeight.Bold, color = ink)
                                            Text(character.tagline, color = muted, fontSize = 12.sp)
                                        }
                                        if (selectedCharacter == character.id) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                                    }
                                }
                            }
                            if (CharacterRegistry.current.wardrobe.isNotEmpty()) {
                                item { Feature("Wardrobe", "Dress your current companion", "✿", surface, ink, muted) { wardrobe() } }
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
                                    listOf("All", "Connect", "Discover", "Care", "Play", "Tools", "Phone").forEach { category ->
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
                                Triple("Focus / Study Buddy", "Care", "Focus companion for study"),
                                Triple("Calm Breathing", "Care", "Breathe with your companion"),
                                Triple("Night Campfire", "Care", "A calm evening together"),
                                Triple("Screen Hug", "Care", "Glass knock and comfort hug"),
                                Triple("Cheer-up Sign", "Care", "A little encouragement"),
                                Triple("Filter Coffee", "Care", "Coffee with your companion"),
                                Triple("Mini-games", "Play", "Play together"),
                                Triple("Screen Smash", "Play", "Smash the previous screen"),
                                Triple("Record a 5s Clip", "Play", "Capture a shareable clip"),
                                Triple("Two Phones", "Play", "Connect companions on Wi-Fi"),
                                Triple("Start a Skit", "Play", "Watch an improvised skit"),
                                Triple("Shows", "Discover", "Watch and create animated shows"),
                                Triple("Character Studio", "Discover", "Design a companion"),
                                Triple("Brain & Chat", "Tools", "AI models and voice settings"),
                                Triple("Permissions", "Tools", "Overlay and notification access"),
                                Triple("Appearance", "Tools", "Theme and display settings"),
                                Triple("Care Tools", "Tools", "Water, tablet, eye, neck and reminders"),
                                Triple("Wardrobe", "Discover", "Outfits for your character"),
                                Triple("Wallpaper", "Phone", "Set as live wallpaper"),
                                Triple("Climbing Spots", "Phone", "Edit wallpaper climbing spots"),
                                Triple("Wallpaper Background", "Phone", "Wallpaper colours and images"),
                                Triple("Touch Feel", "Tools", "Flick, reactions, rest and animation"),
                                Triple("Buddy & Moments", "Care", "Quiet hours and proactive companion"),
                                Triple("Engagement", "Play", "Friendship, greetings and challenges"),
                                Triple("Superpowers", "Play", "Use companion powers"),
                                Triple("Phone Powers", "Phone", "Permissions and phone abilities"),
                                Triple("Background AI", "Tools", "Background intelligence settings")
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
                                        "Focus / Study Buddy" -> v5("focus")
                                        "Calm Breathing" -> v5("breathe")
                                        "Night Campfire" -> v5("campfire")
                                        "Screen Hug" -> v5("comfort_hug")
                                        "Cheer-up Sign" -> v5("comfort_sign")
                                        "Filter Coffee" -> v5("comfort_coffee")
                                        "Mini-games" -> v5("games")
                                        "Screen Smash" -> screenSmash()
                                        "Record a 5s Clip" -> v5("clip")
                                        "Two Phones" -> open(PairActivity::class.java)
                                        "Start a Skit" -> v5("skit")
                                        "Character Studio" -> open(CompanionStudioActivity::class.java)
                                        "Brain & Chat" -> { page = 4; settingsPanel = "brain" }
                                        "Permissions" -> { page = 4; settingsPanel = "permissions" }
                                        "Care Tools" -> page = 0
                                        "Wardrobe" -> if (CharacterRegistry.current.wardrobe.isNotEmpty()) wardrobe() else page = 1
                                        "Wallpaper" -> wallpaper()
                                        "Climbing Spots" -> open(SpotsEditorActivity::class.java)
                                        "Wallpaper Background" -> wallpaperBackground()
                                        "Touch Feel" -> settings("feel")
                                        "Buddy & Moments" -> settings("buddy")
                                        "Engagement" -> settings("engagement")
                                        "Superpowers" -> settings("superpowers")
                                        "Phone Powers" -> settings("phone")
                                        "Background AI" -> settings("brain")
                                        "Shows" -> open(ShowsActivity::class.java)
                                        else -> { page = 4; settingsPanel = "appearance" }
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
                                        "brain" -> "Brain & chat"
                                        "wallpaper" -> "Wallpaper"
                                        else -> "Companion settings"
                                    }, ink)
                                }
                            }
                            if (settingsPanel == "main") {
                                item { Feature("Appearance", "Theme and display preferences", "◐", surface, ink, muted) { settingsPanel = "appearance" } }
                                item { Feature("Companions & behaviour", "Choose companions and configure your crew", "♡", surface, ink, muted) { page = 1 } }
                                item { Feature("Brain & chat", "Cloud, own key, offline models and voice", "✧", surface, ink, muted) { settingsPanel = "brain" } }
                                item { Feature("How they feel & move", "Touch feel, flick, rest and animation", "♡", surface, ink, muted) { settings("feel") } }
                                item { Feature("Buddy & moments", "Proactive behaviour, quiet hours and nudges", "☼", surface, ink, muted) { settings("buddy") } }
                                item { Feature("Engagement & fun", "Friendship, challenges and daily moments", "✦", surface, ink, muted) { settings("engagement") } }
                                item { Feature("Superpowers", "Choose and use companion powers", "✧", surface, ink, muted) { settings("superpowers") } }
                                item { Feature("Phone powers", "Phone abilities, skills and activity log", "▤", surface, ink, muted) { settings("phone") } }
                                item { Feature("Wallpaper", "Live wallpaper, spots and background", "☼", surface, ink, muted) { settingsPanel = "wallpaper" } }
                                item { Feature("Permissions & battery", "Control what Set-Tai can access", "♢", surface, ink, muted) { settingsPanel = "permissions" } }
                                item { Feature("Creative tools", "Shows and character studio", "✦", surface, ink, muted) { page = 2 } }
                                item { Text("Set-Tai v10.38.11", color = muted, fontSize = 12.sp) }
                            }
                            if (settingsPanel == "brain") {
                                item {
                                    Panel(surface) {
                                        Heading("Brain & AI", ink)
                                        Text("Set-Tai can run without optional AI. Your companion's existing animations and interactions work independently.", color = muted)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { settings("brain") }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Brain, voice and models")
                                        }
                                        Text("Opens the existing detailed brain controls directly; all model and voice settings remain available.", color = muted, fontSize = 12.sp)
                                    }
                                }
                            }
                            if (settingsPanel == "wallpaper") {
                                item { Feature("Set as Live Wallpaper", "Move your companion into the wallpaper", "☼", surface, ink, muted) { wallpaper() } }
                                item { Feature("Edit Climbing Spots", "Where your companion climbs", "✿", surface, ink, muted) { open(SpotsEditorActivity::class.java) } }
                                item { Feature("Wallpaper Background", "Choose colours or your own picture", "◐", surface, ink, muted) { wallpaperBackground() } }
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
                                item {
                                    Panel(surface) {
                                        Heading("Notifications", ink)
                                        Text("Manage notifications for the running companion.", color = muted)
                                        OutlinedButton(onClick = {
                                            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                                        }) { Text("Notification settings") }
                                    }
                                }
                                item {
                                    Panel(surface) {
                                        Heading("Notification summary access", ink)
                                        Text("Allow notification listener access for What You Missed.", color = muted)
                                        OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Notification access") }
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= 33) {
                                    item {
                                        Panel(surface) {
                                            Heading("Post notifications", ink)
                                            OutlinedButton(onClick = {
                                                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                                            }) { Text("Allow notifications") }
                                        }
                                    }
                                }
                                item {
                                    Panel(surface) {
                                        Heading("Keep Set-Tai awake", ink)
                                        OutlinedButton(onClick = {
                                            runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
                                                .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                        }) { Text("Request battery exemption") }
                                    }
                                }
                                item {
                                    Panel(surface) {
                                        Heading("Battery", ink)
                                        Text("Manage battery restrictions if the companion stops unexpectedly.", color = muted)
                                        OutlinedButton(onClick = {
                                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                                        }) { Text("App battery settings") }
                                    }
                                }
                            }
                            if (settingsPanel == "appearance") {
                            item {
                                Panel(surface) {
                                    Heading("Appearance", ink)
                                    Text("App theme", color = muted, fontSize = 13.sp)
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        listOf(ThemeSkin.SYSTEM to "System", ThemeSkin.LIGHT to "Light", ThemeSkin.DARK to "Dark").forEachIndexed { index, choice ->
                                            SegmentedButton(
                                                selected = theme == choice.first,
                                                onClick = { ThemeSkin.setMode(this@SettaiDesignActivity, choice.first); theme = choice.first },
                                                shape = SegmentedButtonDefaults.itemShape(index, 3)
                                            ) { Text(choice.second) }
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
