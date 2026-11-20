package com.combo.settai.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.settai.desktop.ChatGptLogin
import com.combo.settai.desktop.Config
import com.combo.settai.desktop.VERSION
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * D3: the desktop app's main window ("Set-Tai"), made with Compose for Desktop (the same toolkit family as the
 * phone's screens): pick your character, give it a brain (Sign in with ChatGPT, a local Ollama brain, or your own
 * API key), and a few settings. The character itself still floats on the desktop in its own see-through window.
 */
class HomeWindow(
    private val cfg: Config,
    private val currentCharacter: () -> String,
    private val pickCharacter: (String) -> Unit,
    private val openChat: () -> Unit,
    private val openAdvanced: () -> Unit,
    private val brainChanged: () -> Unit,
) {
    private var win: ComposeWindow? = null
    private val rev = mutableStateOf(0)

    /** Something changed outside (sign-in finished, character switched from the menu): redraw. */
    fun refresh() = SwingUtilities.invokeLater { rev.value++ }

    fun open() {
        win?.let { it.isVisible = true; it.toFront(); it.requestFocus(); refresh(); return }
        val w = ComposeWindow()
        w.title = "Set-Tai"
        w.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        runCatching { w.iconImages = listOf(portrait(CharacterRegistry.byId(currentCharacter()) ?: CharacterRegistry.current, 128)) }
        w.setContent { SetTaiTheme { Home() } }
        w.setSize(980, 760)
        w.minimumSize = java.awt.Dimension(720, 560)
        w.setLocationRelativeTo(null)
        w.isVisible = true
        win = w
        ChatGptLogin.onChange = { refresh() }
    }

    // ---------------- screen ----------------

    @Composable
    private fun Home() {
        @Suppress("UNUSED_VARIABLE") val seen = rev.value   // recompose on refresh()
        val selected = currentCharacter()
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // left: the current character, big, on a soft stage
            Column(
                Modifier.width(300.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val c = CharacterRegistry.byId(selected) ?: CharacterRegistry.current
                Text("Set-Tai", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("version $VERSION", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                val img = remember(selected) { portrait(c, 360).toComposeImageBitmap() }
                Image(img, c.name, Modifier.size(240.dp))
                Spacer(Modifier.height(12.dp))
                Text(c.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(c.tagline, fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Button(onClick = openChat, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
                    Text("💬  Chat with ${c.name}", fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Tip: double-click the character on your desktop to chat in its bubble.", fontSize = 12.sp,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // right: brain, characters, settings
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
                BrainSection()
                Spacer(Modifier.height(28.dp))
                CharacterSection(selected)
                Spacer(Modifier.height(28.dp))
                SettingsSection()
            }
        }
    }

    // ---------------- brain ----------------

    private val signInMsg = mutableStateOf<String?>(null)
    private val signingIn = mutableStateOf(false)

    @Composable
    private fun BrainSection() {
        @Suppress("UNUSED_VARIABLE") val seen = rev.value
        SectionTitle("🧠  Give it a brain", "Without a brain it still plays and reacts; with one it chats like a friend.")
        val brain = cfg.brain
        // ChatGPT
        BrainCard(
            title = "Your ChatGPT plan",
            subtitle = if (ChatGptLogin.signedIn) "Signed in as ${ChatGptLogin.email.ifBlank { "your account" }}. Uses your Plus / Pro plan; chats aren't saved in ChatGPT."
                else "Sign in with your ChatGPT Plus or Pro account. Your browser opens; nothing is stored except on this computer.",
            active = brain == "chatgpt" && ChatGptLogin.signedIn,
        ) {
            if (ChatGptLogin.signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (brain != "chatgpt") Button(onClick = { cfg.brain = "chatgpt"; cfg.save(); brainChanged(); refresh() }) { Text("Use ChatGPT") }
                    else AssistChip(onClick = {}, label = { Text("✓ In use") })
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { ChatGptLogin.signOut(); if (cfg.brain == "chatgpt") { brainChanged() }; refresh() }) { Text("Sign out") }
                }
            } else {
                Button(
                    onClick = {
                        signingIn.value = true; signInMsg.value = "Finish signing in in your browser…"
                        ChatGptLogin.signIn { err ->
                            SwingUtilities.invokeLater {
                                signingIn.value = false
                                signInMsg.value = err ?: "✅ Connected!"
                                if (err == null) { cfg.brain = "chatgpt"; cfg.save(); brainChanged(); win?.toFront() }
                                refresh()
                            }
                        }
                    },
                    enabled = !signingIn.value,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101010), contentColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(46.dp),
                ) { Text(if (signingIn.value) "Waiting for the browser…" else "Sign in with ChatGPT", fontWeight = FontWeight.SemiBold) }
                if (signingIn.value) {
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { ChatGptLogin.cancelSignIn() }) { Text("Cancel") }
                }
            }
            signInMsg.value?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(12.dp))
        BrainCard(
            title = "On this computer (Ollama)",
            subtitle = "Private and offline: a small AI model runs on your PC. Needs Ollama installed and a model pulled (the setup helps).",
            active = brain == "ollama",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (brain != "ollama") Button(onClick = { cfg.brain = "ollama"; cfg.save(); brainChanged(); refresh() }) { Text("Use this") }
                else AssistChip(onClick = {}, label = { Text("✓ In use") })
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = openAdvanced) { Text("Set up…") }
            }
        }
        Spacer(Modifier.height(12.dp))
        BrainCard(
            title = "Your own API key",
            subtitle = "OpenAI, Anthropic, Gemini, Groq, OpenRouter or any OpenAI-compatible server. You pay the provider.",
            active = brain == "online",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (brain != "online") Button(onClick = { cfg.brain = "online"; cfg.save(); brainChanged(); refresh() }) { Text("Use this") }
                else AssistChip(onClick = {}, label = { Text("✓ In use") })
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = openAdvanced) { Text("Add key…") }
            }
        }
    }

    @Composable
    private fun BrainCard(title: String, subtitle: String, active: Boolean, actions: @Composable ColumnScope.() -> Unit) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (active) { Spacer(Modifier.width(10.dp)); Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("ACTIVE", Modifier.padding(horizontal = 4.dp)) } }
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                actions()
            }
        }
    }

    // ---------------- characters ----------------

    @Composable
    private fun CharacterSection(selected: String) {
        SectionTitle("🎭  Your character", "Same 13 characters as the phone. Switch any time; your chats stay.")
        val all = CharacterRegistry.all
        val pics = remember { all.associate { it.id to portrait(it, 180).toComposeImageBitmap() } }
        // a simple wrapping grid (5 per row)
        all.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c -> CharacterTile(c, pics[c.id], c.id == selected, Modifier.weight(1f)) }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    @Composable
    private fun CharacterTile(c: Character, pic: ImageBitmap?, on: Boolean, modifier: Modifier) {
        Column(
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .clickable { pickCharacter(c.id); refresh() }
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            pic?.let { Image(it, c.name, Modifier.size(84.dp)) }
            Text(c.name, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
        }
    }

    // ---------------- settings ----------------

    @Composable
    private fun SettingsSection() {
        @Suppress("UNUSED_VARIABLE") val seen = rev.value
        SectionTitle("⚙️  Settings", null)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                SwitchRow("Start Set-Tai when I sign in to this computer", cfg.autostart) { cfg.autostart = it; cfg.save(); brainChanged() }
                HorizontalDivider()
                SwitchRow("Show the faint 💬 chat button beside the character", cfg.bool("chat.button", true)) { cfg.set("chat.button", it.toString()); cfg.save() }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("More settings", fontSize = 15.sp)
                        Text("Local AI models, online providers, the character talking on its own…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = openAdvanced) { Text("Open…") }
                }
            }
        }
    }

    @Composable
    private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        var v by remember { mutableStateOf(value) }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(checked = v, onCheckedChange = { v = it; onChange(it) })
        }
    }

    @Composable
    private fun SectionTitle(title: String, sub: String?) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        sub?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
    }

    companion object {
        /** The character drawn by the same code as the phone, waving, into a picture. */
        fun portrait(c: Character, size: Int): BufferedImage {
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            runCatching {
                val wear = CharacterRegistry.wear(c)
                val r = c.newRenderer().also { it.wear = wear }
                val rig = MonkeyRig(); val p = Pose()
                c.fill(p, MonkeyState.WAVE, 0.6f, 0.6f, AnimContext(), wear)
                rig.layout(p, size / 2f, size * 0.93f, size / 175f, 1f)
                r.draw(android.graphics.Canvas(g), rig, p, 0.6f)
            }
            g.dispose()
            return img
        }
    }
}

/** Set-Tai's look on the desktop: the phone's lavender palette, light or dark with the system. */
@Composable
fun SetTaiTheme(content: @Composable () -> Unit) {
    val dark = remember { isSystemDark() }
    val light = lightColorScheme(
        primary = Color(0xFF5B4BDB), onPrimary = Color.White, primaryContainer = Color(0xFFE6E1FF), onPrimaryContainer = Color(0xFF1B1464),
        background = Color(0xFFF7F5FF), onBackground = Color(0xFF221E40), surface = Color.White, onSurface = Color(0xFF221E40),
        surfaceVariant = Color(0xFFEEEAFE), onSurfaceVariant = Color(0xFF5C5878), outlineVariant = Color(0xFFDAD5F2),
    )
    val darkS = darkColorScheme(
        primary = Color(0xFFB7AEFF), onPrimary = Color(0xFF221A6E), primaryContainer = Color(0xFF3B3192), onPrimaryContainer = Color(0xFFE6E1FF),
        background = Color(0xFF141220), onBackground = Color(0xFFE8E5F7), surface = Color(0xFF1E1B2E), onSurface = Color(0xFFE8E5F7),
        surfaceVariant = Color(0xFF26223A), onSurfaceVariant = Color(0xFFB9B4D4), outlineVariant = Color(0xFF3A3552),
    )
    MaterialTheme(colorScheme = if (dark) darkS else light, content = content)
}

/** Best guess at the system's dark mode (GNOME / Windows). */
fun isSystemDark(): Boolean = runCatching {
    if (System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")) {
        val p = ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme").start()
        val out = p.inputStream.bufferedReader().readText(); p.waitFor()
        out.contains("0x0")
    } else {
        val p = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").start()
        val out = p.inputStream.bufferedReader().readText(); p.waitFor()
        out.contains("dark")
    }
}.getOrDefault(false)
