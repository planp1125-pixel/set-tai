package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.combo.monkeycompanion.ai.ChatGptAuth
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ChatHub.BrainChoice
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.sin

/**
 * v10.46 "Give Tutty a brain": one screen to choose how the companion thinks.
 * ChatGPT (the user's own Plus / Pro plan, "Sign in with ChatGPT"), Set-Tai Cloud (free), the on-device brain,
 * or no brain. The current character appears and vanishes in smoke behind it (drawn in code, no images).
 */
class BrainActivity : ComponentActivity() {
    private var rev by mutableStateOf(0)
    private var busy by mutableStateOf(false)
    private var note by mutableStateOf("")
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ChatHub.init(this); ChatGptAuth.init(this)
        setContent { SettaiTheme(this) { Screen() } }
    }

    override fun onResume() { super.onResume(); rev++ }
    override fun onDestroy() { if (busy) ChatGptAuth.cancelSignIn(); super.onDestroy() }

    private fun signIn() {
        busy = true; note = "Opening ChatGPT… sign in, tap Allow, then come back here."
        ChatGptAuth.signIn(this) { err ->
            ui.post {
                busy = false
                if (err == null) { ChatHub.chooseBrain(BrainChoice.CHATGPT); note = "✅ Connected as ${ChatGptAuth.email}. Say hi to ${who().name}!" }
                else note = err
                rev++
            }
        }
    }

    private fun who(): Character = CharacterRegistry.current

    @Composable
    private fun Screen() {
        val seen = rev   // read so the screen redraws when [rev] changes
        if (seen < 0) return
        val name = who().name
        val choice = ChatHub.brainChoice()
        val dark = Color(0xFF14122B)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B1F5C), dark)))) {
            AndroidView(factory = { BrainStageView(it) }, update = { v -> v.who = who() }, modifier = Modifier.fillMaxWidth().height(330.dp))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { finish() }) { Text("←  Back", color = Color.White) }
                }
                Spacer(Modifier.height(210.dp))
                Text("Give $name a brain", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Pick how $name thinks and talks. You can change it any time.", color = Color(0xFFCFCBEA), fontSize = 14.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))

                // 1. ChatGPT (v10.54: Play build shows it only after OpenAI approves, or for a developer / already signed-in tester)
                if (ChatHub.chatGptOffered()) Option(choice == BrainChoice.CHATGPT, "Your ChatGPT plan", "For ChatGPT Plus and Pro. $name thinks with your own plan: no Set-Tai limit, the sharpest answers.") {
                    if (ChatGptAuth.signedIn) {
                        Text("Signed in as ${ChatGptAuth.email.ifBlank { "your ChatGPT account" }}", color = Color(0xFF3B3566), fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (choice != BrainChoice.CHATGPT) Button(onClick = { ChatHub.chooseBrain(BrainChoice.CHATGPT); rev++ }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))) { Text("Use ChatGPT") }
                            TextButton(onClick = {
                                ChatGptAuth.signOut()
                                if (ChatHub.brainChoice() == BrainChoice.CHATGPT) ChatHub.chooseBrain(BrainChoice.CLOUD)
                                note = "Signed out. $name is back on Set-Tai Cloud."; rev++
                            }) { Text("Sign out") }
                        }
                    } else {
                        SignInWithChatGpt(busy) { signIn() }
                    }
                }
                if (ChatHub.chatGptOffered()) Spacer(Modifier.height(12.dp))
                // 2. Set-Tai Cloud
                Option(choice == BrainChoice.CLOUD, "Set-Tai Cloud", "Free, no sign-in. About 40 chats a day for every phone.") {
                    if (choice != BrainChoice.CLOUD) Button(onClick = { ChatHub.chooseBrain(BrainChoice.CLOUD); rev++ }) { Text("Use Set-Tai Cloud") }
                }
                Spacer(Modifier.height(12.dp))
                // 3. On-device
                Option(choice == BrainChoice.OFFLINE, "On this phone", "Private and works offline. Needs a big download and is slower.") {
                    TextButton(onClick = { startActivity(Intent(this@BrainActivity, SettingsActivity::class.java).putExtra("settai_settings_section", "brain")) }) { Text(if (choice == BrainChoice.OFFLINE) "Brain settings" else "Set it up") }
                }
                if (choice == BrainChoice.OWN_KEY) {
                    Spacer(Modifier.height(12.dp))
                    Option(true, "Your own API key", "You are using an API key from Brain & settings.") {}
                }
                Spacer(Modifier.height(14.dp))
                if (note.isNotBlank()) {
                    Text(note, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = {
                    if (choice == BrainChoice.NONE) { ChatHub.chooseBrain(BrainChoice.CLOUD); rev++ } else finish()
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (choice == BrainChoice.NONE) "Turn the brain on (Set-Tai Cloud)" else "Done", color = Color(0xFFCFCBEA))
                }
                Text("$name's built-in lines, games and powers work without any brain.", color = Color(0xFF8E89B5), fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
            }
        }
    }

    @Composable
    private fun Option(selected: Boolean, title: String, body: String, content: @Composable () -> Unit) {
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = if (selected) BorderStroke(3.dp, Color(0xFF8C7BFF)) else null,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color(0xFF1E1A3C), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (selected) Surface(color = Color(0xFF8C7BFF), shape = RoundedCornerShape(50)) {
                        Text("IN USE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(body, color = Color(0xFF5B5680), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }

    /** The sign-in button in OpenAI's plain style: black, rounded, "Sign in with ChatGPT". */
    @Composable
    private fun SignInWithChatGpt(busy: Boolean, onClick: () -> Unit) {
        Button(onClick = onClick, enabled = !busy, shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D0D0D), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (busy) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Waiting for ChatGPT…") }
            else { OpenAiMark(); Spacer(Modifier.width(10.dp)); Text("Sign in with ChatGPT", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    /** A small round mark (drawn, not a copied logo). */
    @Composable
    private fun OpenAiMark() {
        Box(Modifier.size(20.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).background(Color(0xFF0D0D0D), CircleShape))
        }
    }
}

/**
 * The background of the brain screen: the current character floats in the middle, and every few seconds vanishes
 * in its own smoke and pops back in (drawn with the character's renderer, like everywhere else).
 */
@SuppressLint("ViewConstructor")
class BrainStageView(ctx: Context) : View(ctx) {
    var who: Character = CharacterRegistry.current
        set(v) { if (v.id != field.id) { field = v; renderer = v.newRenderer().also { it.wear = CharacterRegistry.wear(v) } } }
    private var renderer = who.newRenderer().also { it.wear = CharacterRegistry.wear(who) }
    private val rig = MonkeyRig(); private val pose = Pose(); private val anim = AnimContext()
    private val t0 = SystemClock.uptimeMillis()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        val w = width.toFloat(); val h = height.toFloat()
        // a soft spotlight
        glow.shader = android.graphics.RadialGradient(w / 2f, h * 0.48f, w * 0.55f, AColor.argb(110, 0x9C, 0x8C, 0xFF), AColor.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(w / 2f, h * 0.48f, w * 0.55f, glow)
        // vanish cycle: 4 s shown, 0.6 s out, 0.6 s in (it pops back somewhere a little different)
        val cyc = 5.2f; val k = t % cyc; val round = (t / cyc).toInt()
        val alpha = when { k < 4f -> 1f; k < 4.6f -> 1f - (k - 4f) / 0.6f; else -> (k - 4.6f) / 0.6f }
        val x = w / 2f + (if (round % 2 == 0) -1f else 1f) * w * 0.08f
        val bob = sin(t * 2.2f) * 8f * d
        val y = h * 0.78f + bob
        if (alpha < 0.99f) com.combo.monkeycompanion.engine.stagefx.StageFx.smoke(c, x, y - h * 0.22f, w * 0.18f, who.vanishStyle, 1f - alpha, t)
        who.fill(pose, MonkeyState.IDLE, t, t, anim, CharacterRegistry.wear(who))
        Expressions.apply(if (k < 2f) "happy" else "curious", pose, t, 1f)
        pose.shadow = 0f
        val scale = h * 0.55f / MonkeyRig.HEIGHT
        rig.layout(pose, x, y, scale, 1f)
        if (alpha > 0.02f) {
            val sv = c.saveLayerAlpha(null, (255 * alpha).toInt())
            renderer.draw(c, rig, pose, t)
            c.restoreToCount(sv)
        }
        postInvalidateOnAnimation()
    }
}
