package com.combo.monkeycompanion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import com.combo.monkeycompanion.agent.DailyUtilityEngine
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.EnumMap
import com.combo.monkeycompanion.ai.BrainType
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.CustomCompanionStore
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.powers.SettaiNotificationListener
import com.combo.monkeycompanion.ui.CharacterPreviewView
import com.combo.monkeycompanion.ui.ChatActivity
import com.combo.monkeycompanion.ui.CompanionStudioActivity
import com.combo.monkeycompanion.ui.DiaryActivity
import com.combo.monkeycompanion.ui.MoodBarsView
import com.combo.monkeycompanion.ui.SettingsActivity
import com.combo.monkeycompanion.ui.SkillsActivity
import com.combo.monkeycompanion.wallpaper.SettaiWallpaperService
import com.combo.monkeycompanion.wallpaper.SpotsEditorActivity
import com.combo.monkeycompanion.wallpaper.WallpaperPrefs

/**
 * Settai v9.0 Main App:
 * Structured 4-Tab Architecture with a Top Persistent Status Bar and Pinned Bottom Navigation Bar:
 * 1. 🏠 Home (Live 60fps Stage, Companion Size 1.0x/1.25x/1.5x, Featured Photo-to-Look-Alike Studio, Quick Controls)
 * 2. 🦸 Characters (Visual Roster for Custom Look-Alike, Settai, Superboy, Armour Anna, Mask Man, Billa & Chittu + Powers & Duo)
 * 3. 🌍 World & Care (Focus Desk Buddy, 60s Breathing, Night Campfire, Heart & Care Animations, World Weather & Mood Bars)
 * 4. ⚙️ Settings (Diary Limits & Behaviour Sliders, Brain Settings, Permissions Health & Live Wallpaper)
 */
class MainActivity : Activity() {

    enum class Tab(val icon: String, val label: String) {
        HOME("🏠", "Home"),
        CHARACTERS("🦸", "Characters"),
        WORLD_CARE("🌍", "World & Care"),
        SETTINGS("⚙️", "Settings"),
    }

    private val d by lazy { resources.displayMetrics.density }
    private lateinit var overlayStatus: TextView
    private lateinit var notifStatus: TextView
    private lateinit var notifAccessStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var overlayBtn: Button
    private lateinit var notifBtn: Button
    private lateinit var notifAccessBtn: Button
    private lateinit var batteryBtn: Button
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var topToggleBtn: Button
    private lateinit var topActiveBadge: TextView
    private lateinit var runningText: TextView
    private lateinit var brainText: TextView
    private lateinit var wallText: TextView
    private lateinit var friendText: TextView
    private lateinit var mindText: TextView
    private lateinit var moodBars: MoodBarsView

    private val modeButtons = ArrayList<Pair<com.combo.monkeycompanion.world.Population.Mode, Button>>()
    private val sizeButtons = ArrayList<Pair<Float, Button>>()
    private val tabViews = EnumMap<Tab, View>(Tab::class.java)
    private val navButtons = EnumMap<Tab, LinearLayout>(Tab::class.java)
    private val charThumbs = ArrayList<CharacterPreviewView>()
    private var rosterGridContainer: LinearLayout? = null
    private lateinit var homeBrainStatusTv: TextView
    private lateinit var homeBrainToggleBtn: Button
    private lateinit var settingsBrainStatusTv: TextView
    private lateinit var settingsBrainToggleBtn: Button
    private lateinit var utilityStatusTv: TextView
    private var homePreview: MonkeyView? = null
    var activeTab: Tab = Tab.HOME; private set

    private val cream = Color.rgb(0xFF, 0xF4, 0xE2)
    private val brown = Color.rgb(0x5A, 0x36, 0x18)
    private val soft = Color.rgb(0x8A, 0x63, 0x3E)
    private val accent = Color.rgb(0xE8, 0x8A, 0x2E)
    private val purple = Color.rgb(0x6A, 0x1B, 0x9A)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CustomCompanionStore.init(this)
        ChatHub.init(this)
        com.combo.monkeycompanion.mind.MindHub.init(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        setContentView(buildUi())
        handleBringBack(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBringBack(intent)
    }

    private fun handleBringBack(i: Intent?) {
        if (i?.action == ACTION_BRING_BACK && !MonkeyOverlayService.running) startMonkey()
    }

    override fun onResume() {
        super.onResume()
        CustomCompanionStore.init(this)
        ChatHub.settings.applyCharacter()
        populateRosterGrid()
        refresh()
        if (::friendText.isInitialized) friendText.text = friendSummary()
        refreshMindCard()
    }

    fun selectTab(tab: Tab) {
        activeTab = tab
        for ((t, v) in tabViews) {
            v.visibility = if (t == tab) View.VISIBLE else View.GONE
        }
        for ((t, btn) in navButtons) {
            val sel = t == tab
            btn.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (sel) Color.rgb(0xFF, 0xEC, 0xD2) else Color.TRANSPARENT)
            }
            for (i in 0 until btn.childCount) {
                val tv = btn.getChildAt(i) as? TextView ?: continue
                tv.setTextColor(if (sel) accent else brown)
                tv.setTypeface(tv.typeface, if (sel) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    private fun refreshMindCard() {
        if (!::mindText.isInitialized || !::moodBars.isInitialized) return
        com.combo.monkeycompanion.mind.MindHub.init(this)
        val m = com.combo.monkeycompanion.mind.MindHub.mind() ?: return
        val now = System.currentTimeMillis()
        moodBars.drives = m.drives
        moodBars.focus = m.focus(now)
        val ws = com.combo.monkeycompanion.mind.MindHub.world.state
        val trace = com.combo.monkeycompanion.mind.MindHub.lastNightTrace
        val tracePart = if (trace != null) "\n🌙 Morning trace: “${trace.line}”" else ""
        val activeNames = com.combo.monkeycompanion.mind.MindHub.activeCreatures().joinToString(" & ") { com.combo.monkeycompanion.world.Population.displayName(it) }
        val rec = synchronized(com.combo.monkeycompanion.mind.MindHub.recent) { com.combo.monkeycompanion.mind.MindHub.recent.take(3) }
        val logPart = if (rec.isEmpty()) "" else "\nRecent thoughts:\n" + rec.joinToString("\n") { "• $it" }
        mindText.text = "🌍 ${ws.homeSummary()}\n👥 On screen: $activeNames$tracePart\n\n💭 ${m.thinking(now)}$logPart"
        val curMode = com.combo.monkeycompanion.world.Population.Mode.of(com.combo.monkeycompanion.mind.MindHub.neighbourMode)
        for ((mode, btn) in modeButtons) {
            val sel = mode == curMode
            btn.setTextColor(if (sel) Color.WHITE else brown)
            btn.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (sel) accent else Color.WHITE)
                setStroke(dp(1), if (sel) accent else Color.rgb(0xE6, 0xC9, 0xA3))
            }
        }
        val curScale = CustomCompanionStore.activeSpec(this).companionScale
        for ((sz, btn) in sizeButtons) {
            val sel = kotlin.math.abs(curScale - sz) < 0.08f
            btn.setTextColor(if (sel) Color.WHITE else brown)
            btn.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (sel) purple else Color.WHITE)
                setStroke(dp(1), if (sel) purple else Color.rgb(0xE6, 0xC9, 0xA3))
            }
        }
        val curChar = CharacterRegistry.current
        charThumbs.forEach { it.picked = it.who.id == curChar.id }
        homePreview?.invalidate()
    }

    private fun friendSummary(): String {
        val F = com.combo.monkeycompanion.v5.Friendship
        val st = com.combo.monkeycompanion.fun5.Fun5Store.friendship(this)
        val lvl = F.level(st.xp)
        val pct = (F.progress(st.xp) * 100).toInt()
        val bar = "▰".repeat(pct / 10) + "▱".repeat(10 - pct / 10)
        val got = F.unlocked(st.xp).joinToString(", ") { it.title }.ifEmpty { "nothing yet — pet me!" }
        val next = F.unlocks.firstOrNull { it.level > lvl }
        val sc = com.combo.monkeycompanion.fun5.Fun5Store.scores(this)
        val best = "🍌 ${sc["banana"]} · 👀 ${sc["stare_w"]}W/${sc["stare_l"]}L · ✊ ${sc["rps_w"]}W/${sc["rps_l"]}L/${sc["rps_d"]}D"
        return "Level $lvl — ${F.title(lvl)}\n$bar $pct%  (${st.xp} XP)" +
            (if (st.streak > 1) "  🔥 ${st.streak}-day streak" else "") +
            "\nUnlocked: $got" + (next?.let { "\nNext at level ${it.level}: ${it.title}" } ?: "") +
            "\nBest: $best"
    }

    private fun v5(cmd: String) {
        if (cmd == "notif_card_demo" || cmd == "notif_card") {
            homePreview?.openNotifSummaryCard(useDemoIfEmpty = true)
        }
        if (!MonkeyOverlayService.running) {
            if (Settings.canDrawOverlays(this)) {
                startMonkey()
                window.decorView.postDelayed({
                    runCatching {
                        startService(Intent(this, MonkeyOverlayService::class.java).setAction(MonkeyOverlayService.ACTION_V5).putExtra("cmd", cmd))
                    }
                }, 350L)
            } else if (cmd != "notif_card_demo" && cmd != "notif_card") {
                Toast.makeText(this, "Grant Overlay Permission in Settings to summon on screen!", Toast.LENGTH_SHORT).show()
            }
            return
        }
        startService(Intent(this, MonkeyOverlayService::class.java).setAction(MonkeyOverlayService.ACTION_V5).putExtra("cmd", cmd))
    }

    companion object {
        const val ACTION_BRING_BACK = "com.combo.monkeycompanion.BRING_BACK"
    }

    private fun dp(v: Int) = (v * d).toInt()

    @SuppressLint("SetTextI18n")
    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
        }

        // ---------------- 1. Top Persistent Status Bar ----------------
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(0xF0, 0xDF, 0xC4))
            }
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "Set-Tai v10.12"
            textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
        })
        topActiveBadge = TextView(this).apply {
            text = "Active: ${CharacterRegistry.current.name}"
            textSize = 12f; setTextColor(soft)
        }
        titleCol.addView(topActiveBadge)
        topBar.addView(titleCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        topToggleBtn = Button(this).apply {
            text = "▶ Summon"
            isAllCaps = false; textSize = 13.5f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(accent) }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener {
                if (MonkeyOverlayService.running) {
                    MonkeyOverlayService.stop(this@MainActivity)
                    postDelayed({ refresh() }, 300)
                } else {
                    startMonkey()
                }
            }
        }
        topBar.addView(topToggleBtn)
        root.addView(topBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---------------- 2. Scrollable Tab Content Area ----------------
        val contentFrame = FrameLayout(this)
        val homeTab = buildHomeTab()
        val charsTab = buildCharactersTab()
        val worldTab = buildWorldAndCareTab()
        val settingsTab = buildSettingsTab()

        tabViews[Tab.HOME] = homeTab
        tabViews[Tab.CHARACTERS] = charsTab
        tabViews[Tab.WORLD_CARE] = worldTab
        tabViews[Tab.SETTINGS] = settingsTab

        for (v in tabViews.values) {
            contentFrame.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(contentFrame)
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ---------------- 3. Pinned Bottom Navigation Bar (4 Tabs) ----------------
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(0xE6, 0xC9, 0xA3))
            }
        }
        for (tab in Tab.entries) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener { selectTab(tab) }
            }
            cell.addView(TextView(this).apply {
                text = tab.icon; textSize = 18f; gravity = Gravity.CENTER
            })
            cell.addView(TextView(this).apply {
                text = tab.label; textSize = 11.5f; gravity = Gravity.CENTER
            })
            navButtons[tab] = cell
            navBar.addView(cell, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        root.addView(navBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }

        selectTab(Tab.HOME)
        return root
    }

    private fun buildHomeTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // 1. Featured Photo-to-Look-Alike Studio Hero Card
        val studioCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.rgb(0x23, 0x1B, 0x3A))
                setStroke(dp(2), Color.rgb(0xFF, 0xC1, 0x07))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        studioCard.addView(TextView(this).apply {
            text = "📸✨ Photo-to-Look-Alike Studio"
            textSize = 19f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
        })
        studioCard.addView(TextView(this).apply {
            text = "Upload your photo (100% offline) to create a big 60fps look-alike with your real hair, beard & shirt — or swap onto Superboy & Ironboy bodies with all 18 powers!"
            textSize = 12.8f; setTextColor(Color.rgb(0xE0, 0xD8, 0xF0)); setPadding(0, dp(4), 0, dp(10))
        })
        studioCard.addView(button("📸✨  Open Photo-to-Look-Alike Studio", primary = true) {
            startActivity(Intent(this@MainActivity, CompanionStudioActivity::class.java))
        })
        col.addView(studioCard)

        // 1B. Highlighted "🧠 Give Brain to Set-Tai" Opt-In Card on Home Tab!
        val giveBrainHomeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.rgb(0x1E, 0x16, 0x33))
                setStroke(dp(2), Color.rgb(0xFF, 0xC1, 0x07))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        giveBrainHomeCard.addView(TextView(this).apply {
            text = "🧠 Give Brain to Set-Tai (AI Model Opt-In)"
            textSize = 17.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(0xFF, 0xD5, 0x4F))
        })
        homeBrainStatusTv = TextView(this).apply {
            text = ChatHub.giveBrainStatusLine()
            textSize = 12.5f; setTextColor(Color.rgb(0xE0, 0xD8, 0xF0)); setPadding(0, dp(4), 0, dp(8))
        }
        giveBrainHomeCard.addView(homeBrainStatusTv)
        val gbHomeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gbHomeRow.addView(button("🧠 Give Brain (Connect AI)", primary = true) {
            com.combo.monkeycompanion.ui.GiveBrainDialog.show(this@MainActivity, onChanged = { refresh() })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f).apply { rightMargin = dp(6) })
        homeBrainToggleBtn = button(if (ChatHub.isBrainEnabled()) "🔒 Turn OFF" else "✅ Turn ON") {
            ChatHub.setBrainEnabled(!ChatHub.isBrainEnabled())
            refresh()
        }
        gbHomeRow.addView(homeBrainToggleBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f))
        giveBrainHomeCard.addView(gbHomeRow)
        col.addView(giveBrainHomeCard)

        // 2. Live 60fps Stage Card + Companion Size Selector (1.0x / 1.25x / 1.5x)
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.rgb(0xFF, 0xE7, 0xC4)) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        val preview = MonkeyView(this, null) { startActivity(Intent(this, ChatActivity::class.java)) }.also { homePreview = it }
        previewCard.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(250)))

        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), 0)
        }
        sizeRow.addView(TextView(this).apply {
            text = "Companion Size:"; textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(8) })
        sizeButtons.clear()
        for ((sz, lbl) in listOf(1.0f to "Normal 1.0x", 1.25f to "Large 1.25x", 1.5f to "XL 1.5x")) {
            val b = Button(this).apply {
                text = lbl; isAllCaps = false; textSize = 12f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    val cur = CustomCompanionStore.activeSpec(this@MainActivity)
                    CustomCompanionStore.save(this@MainActivity, cur.copy(companionScale = sz))
                    refreshMindCard()
                }
            }
            sizeButtons += sz to b
            sizeRow.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        previewCard.addView(sizeRow)

        val homeCrewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        for (mode in com.combo.monkeycompanion.world.Population.Mode.entries) {
            val b = Button(this).apply {
                text = when (mode) {
                    com.combo.monkeycompanion.world.Population.Mode.SOLO -> "👤 Solo (1)"
                    com.combo.monkeycompanion.world.Population.Mode.DUO -> "👥 Duo (2)"
                    com.combo.monkeycompanion.world.Population.Mode.TRIO -> "👨‍👩‍👧 Trio (3)"
                }
                isAllCaps = false; textSize = 12f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    com.combo.monkeycompanion.mind.MindHub.setNeighbourMode(this@MainActivity, mode)
                    MonkeyOverlayService.syncCrew(this@MainActivity)
                    refresh(); refreshMindCard()
                    Toast.makeText(this@MainActivity, "Screen Mode: ${mode.label} (${com.combo.monkeycompanion.mind.MindHub.activeCreatures().size} on screen!)", Toast.LENGTH_SHORT).show()
                }
            }
            modeButtons += mode to b
            homeCrewRow.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        previewCard.addView(homeCrewRow)
        col.addView(previewCard)

        // 3. Quick Summon & Notification Summary Card Controls
        runningText = status()
        startBtn = button("🐒  Start Set-Tai on Screen", primary = true) { startMonkey() }
        stopBtn = button("Stop Overlay") {
            MonkeyOverlayService.stop(this)
            stopBtn.postDelayed({ refresh() }, 300)
        }
        col.addView(section("🚀 Quick Screen Controls", runningText, startBtn).also {
            val l = it as LinearLayout
            l.addView(stopBtn, btnLp())
            l.addView(button("📬  What You Missed (Notification Summary Card)") { v5("notif_card_demo") }, btnLp())
            l.addView(button("💬  Chat with ${CharacterRegistry.current.name}") {
                startActivity(Intent(this@MainActivity, ChatActivity::class.java))
            }, btnLp())
        })

        // 4. Purposeful Daily Life Utilities Suite (6 Real-World Assistant Tools!)
        DailyUtilityEngine.init(this)
        val utilCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(2), Color.rgb(0x2E, 0x7D, 0x32))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        utilCard.addView(TextView(this).apply {
            text = "🎯 Purposeful Daily Life Utilities (6 Smart Tools)"
            textSize = 17.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(0x1B, 0x5E, 0x20))
        })
        utilityStatusTv = TextView(this).apply {
            text = buildUtilitySummaryText()
            textSize = 12.5f; setTextColor(brown); setPadding(0, dp(4), 0, dp(8))
        }
        utilCard.addView(utilityStatusTv)

        // Tool 1: 1-Tap OTP & Live Delivery / Cab / Train Catcher
        val uRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        uRow1.addView(button("🔐 Test OTP Catcher", primary = true) {
            DailyUtilityEngine.triggerDemoOtp(this@MainActivity, "482910", "HDFC Bank")
            v5("demo_otp")
            homePreview?.invalidate()
            refresh()
            Toast.makeText(this@MainActivity, "🔐 Caught OTP 482910! Tap the green pill on Settai (or Copy below)!", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        uRow1.addView(button("🛵 Test Delivery Pill") {
            DailyUtilityEngine.triggerDemoDelivery(this@MainActivity, "🛵 Swiggy: Order arriving in 4 mins")
            v5("demo_delivery")
            homePreview?.invalidate()
            refresh()
            Toast.makeText(this@MainActivity, "🛵 Live Delivery Pill pinned above Settai!", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        utilCard.addView(uRow1)

        // Tool 2 & 5: 20-Min Focus / Kids' Study Timer & 20-20-20 Eye/Neck Care Guard
        val uRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        uRow2.addView(button("🍅 20m Study / Focus Timer") {
            DailyUtilityEngine.startFocusTimer(this@MainActivity, 20)
            v5("focus_20")
            homePreview?.invalidate()
            refresh()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        uRow2.addView(button("🧘 20s Eye & Neck Break") {
            DailyUtilityEngine.triggerEyeBreakNow(20f)
            v5("eye_break")
            homePreview?.invalidate()
            refresh()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        utilCard.addView(uRow2)

        // Tool 3: Sticky Chest Reminder & Medicine / Water Presets
        utilCard.addView(TextView(this).apply {
            text = "📌 Sticky Chest Reminder (stays pinned on Settai until you tap ✓ Done):"
            textSize = 12.2f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
            setPadding(0, dp(10), 0, dp(4))
        })
        val remRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val remInput = EditText(this).apply {
            hint = "e.g. 💊 Take tablet at 9 PM, 🥛 Buy milk"
            setText(DailyUtilityEngine.stickyReminderText)
            textSize = 13.5f; setTextColor(brown); setSingleLine(true)
        }
        remRow.addView(remInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        remRow.addView(button("📌 Pin", primary = true) {
            val txt = remInput.text?.toString()?.trim().orEmpty().ifBlank { "💊 Take tablet at 9 PM" }
            DailyUtilityEngine.setStickyReminder(this@MainActivity, txt)
            remInput.setText(txt)
            homePreview?.invalidate()
            refresh()
            Toast.makeText(this@MainActivity, "📌 Pinned reminder '$txt' on Settai!", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        utilCard.addView(remRow)

        val presetRemRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        for ((lbl, txt) in listOf("💊 Tablet" to "💊 Take tablet 9PM", "💧 Water" to "💧 Drink 1 glass water", "🥛 Milk" to "🥛 Buy milk & curd", "✓ Clear" to "")) {
            presetRemRow.addView(Button(this).apply {
                text = lbl; isAllCaps = false; textSize = 11.5f; setTextColor(brown)
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.rgb(0xFF, 0xF8, 0xEB)); setStroke(dp(1), Color.rgb(0xE6, 0xC9, 0xA3)) }
                setOnClickListener {
                    if (txt.isEmpty()) DailyUtilityEngine.clearStickyReminder(this@MainActivity)
                    else DailyUtilityEngine.setStickyReminder(this@MainActivity, txt)
                    remInput.setText(txt)
                    homePreview?.invalidate()
                    refresh()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        }
        utilCard.addView(presetRemRow)

        // Tool 4: Pocket Clipboard Backpack (5 Slots)
        val uRow4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        uRow4.addView(button("🎒 Open Clipboard Backpack (5 Slots)", primary = true) {
            showClipboardBackpackDialog()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.3f).apply { rightMargin = dp(6) })
        uRow4.addView(button("📋 Pin Clipboard") {
            val pinned = DailyUtilityEngine.pinCurrentSystemClipboard(this@MainActivity)
            if (pinned != null) {
                Toast.makeText(this@MainActivity, "🎒 Pinned to Backpack: ${pinned.take(36)}", Toast.LENGTH_SHORT).show()
            } else {
                showClipboardBackpackDialog()
            }
            refresh()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.9f))
        utilCard.addView(uRow4)

        col.addView(utilCard)
        return col
    }

    private fun buildUtilitySummaryText(): String {
        val bh = DailyUtilityEngine.readBatteryHealth(this)
        val otpPart = DailyUtilityEngine.activeOtpCode?.let { "\n🔐 Active OTP: $it (Tap green pill on Settai to copy!)" } ?: ""
        val delPart = DailyUtilityEngine.activeDeliveryBadge?.let { "\n$it" } ?: ""
        val remPart = DailyUtilityEngine.stickyReminderText.takeIf { it.isNotBlank() }?.let { "\n📌 Pinned on Settai: $it" } ?: ""
        val focusPart = if (com.combo.monkeycompanion.mind.MindHub.focusBuddy.active) "\n🍅 Focus/Study Timer: ${com.combo.monkeycompanion.mind.MindHub.focusBuddy.timerLabel()}" else ""
        val alertPart = bh.alertLine?.let { "\n⚠️ $it" } ?: ""
        return "${bh.statusLine} · 🧘 Edge YouTube/Reels Guard: ${DailyUtilityEngine.doomscrollLimitMinutes}m ON ✅$otpPart$delPart$remPart$focusPart$alertPart"
    }

    private fun showClipboardBackpackDialog() {
        DailyUtilityEngine.init(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val input = EditText(this).apply {
            hint = "Type or paste a link, phone number, address, or note to pin…"
            textSize = 14f; setTextColor(brown)
        }
        box.addView(input)
        var dlgRef: AlertDialog? = null
        box.addView(button("➕ Pin Note / Current Clipboard into Backpack", primary = true) {
            val typed = input.text?.toString()?.trim().orEmpty()
            if (typed.isNotEmpty()) {
                DailyUtilityEngine.pinToBackpack(this, typed)
            } else {
                DailyUtilityEngine.pinCurrentSystemClipboard(this)
            }
            refresh()
            runCatching { dlgRef?.dismiss() }
            showClipboardBackpackDialog()
        }, btnLp())

        val items = DailyUtilityEngine.clipboardBackpack(this)
        box.addView(TextView(this).apply {
            text = "Saved Slots (${items.size}/5) — Tap any slot to Copy 📋:"
            textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
            setPadding(0, dp(10), 0, dp(4))
        })
        items.forEachIndexed { idx, item ->
            box.addView(button("${idx + 1}. 📋 ${item.take(55)}") {
                DailyUtilityEngine.copyBackpackItem(this, idx)
                Toast.makeText(this, "📋 Copied Slot #${idx + 1} to Clipboard!", Toast.LENGTH_SHORT).show()
                runCatching { dlgRef?.dismiss() }
            }, btnLp())
        }

        dlgRef = AlertDialog.Builder(this)
            .setTitle("🎒 Pocket Clipboard Backpack (5 Slots)")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Done", null)
            .create()
        runCatching { dlgRef.show() }
    }

    private fun buildCharactersTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        val rosterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        rosterCard.addView(TextView(this).apply {
            text = "🦸 Choose Active Companion"
            textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
        })
        rosterCard.addView(TextView(this).apply {
            text = "Tap any character to put them on screen immediately:"
            textSize = 12.5f; setTextColor(soft); setPadding(0, dp(2), 0, dp(8))
        })

        val gridBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rosterGridContainer = gridBox
        rosterCard.addView(gridBox)
        populateRosterGrid()
        rosterCard.addView(button("📸✨  Customize Look-Alike & All 18 Superpowers", primary = true) {
            startActivity(Intent(this, CompanionStudioActivity::class.java))
        }, btnLp())
        col.addView(rosterCard)

        // Duo / Trio Neighbours & Cat-and-Mouse Chase Card
        val duoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        duoCard.addView(TextView(this).apply {
            text = "👥 Companions on Screen (Solo / Duo / Trio)"
            textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
            setPadding(0, 0, 0, dp(6))
        })
        val nRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (mode in com.combo.monkeycompanion.world.Population.Mode.entries) {
            val b = Button(this).apply {
                text = when (mode) {
                    com.combo.monkeycompanion.world.Population.Mode.SOLO -> "👤 Solo (1)"
                    com.combo.monkeycompanion.world.Population.Mode.DUO -> "👥 Duo (2)"
                    com.combo.monkeycompanion.world.Population.Mode.TRIO -> "👨‍👩‍👧 Trio (3)"
                }
                isAllCaps = false; textSize = 13f
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener {
                    com.combo.monkeycompanion.mind.MindHub.setNeighbourMode(this@MainActivity, mode)
                    MonkeyOverlayService.syncCrew(this@MainActivity)
                    refresh(); refreshMindCard()
                    Toast.makeText(this@MainActivity, "Active on Screen: ${com.combo.monkeycompanion.mind.MindHub.activeCreatures().joinToString(" + ") { com.combo.monkeycompanion.world.Population.displayName(it) }}", Toast.LENGTH_SHORT).show()
                }
            }
            modeButtons += mode to b
            nRow.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        duoCard.addView(nRow)
        // v10.12 free crew picker: choose ANY character for every slot (never auto-overridden).
        duoCard.addView(TextView(this).apply {
            text = "👥 Build your crew — tap a slot to pick anyone (Gajju, Rocket Girl and your own companions too):"
            textSize = 12.2f; setTextColor(soft); setPadding(0, dp(8), 0, dp(2))
        })
        val MH = com.combo.monkeycompanion.mind.MindHub
        val P = com.combo.monkeycompanion.world.Population
        val slotBtns = ArrayList<Button>()
        fun nameOf(id: String?) = if (id.isNullOrBlank()) "🎲 Auto" else P.displayName(id)
        fun refreshSlots() {
            val mode = com.combo.monkeycompanion.world.Population.Mode.of(MH.neighbourMode)
            slotBtns.getOrNull(0)?.text = "⭐ Lead: ${CharacterRegistry.current.name}"
            slotBtns.getOrNull(1)?.apply { text = "Partner 1: ${nameOf(MH.customPartner1Id)}"; isEnabled = mode != com.combo.monkeycompanion.world.Population.Mode.SOLO }
            slotBtns.getOrNull(2)?.apply { text = "Partner 2: ${nameOf(MH.customPartner2Id)}"; isEnabled = mode == com.combo.monkeycompanion.world.Population.Mode.TRIO }
        }
        fun pickSlot(slot: Int) {
            val chars = CharacterRegistry.allWithCustom
            val labels = (if (slot == 0) emptyList() else listOf("🎲 Auto (let them choose)")) + chars.map { it.name }
            android.app.AlertDialog.Builder(this).setTitle(if (slot == 0) "Pick the lead" else "Pick partner $slot")
                .setItems(labels.toTypedArray()) { _, which ->
                    val pick = if (slot == 0) chars[which].id else if (which == 0) null else chars[which - 1].id
                    var lead = CharacterRegistry.current.id
                    var p1 = MH.customPartner1Id
                    var p2 = MH.customPartner2Id
                    when (slot) {
                        0 -> { if (pick == p1) p1 = lead else if (pick == p2) p2 = lead; lead = pick!! }
                        1 -> { if (pick != null && pick == p2) p2 = p1; p1 = pick }
                        else -> { if (pick != null && pick == p1) p1 = p2; p2 = pick }
                    }
                    var mode = com.combo.monkeycompanion.world.Population.Mode.of(MH.neighbourMode)
                    if (slot >= 1 && pick != null && mode.maxCreatures <= slot) mode = if (slot == 1) com.combo.monkeycompanion.world.Population.Mode.DUO else com.combo.monkeycompanion.world.Population.Mode.TRIO
                    MH.setCrew(this, lead, mode, p1, p2)
                    MonkeyOverlayService.syncCrew(this)
                    refresh(); refreshMindCard(); refreshSlots()
                    Toast.makeText(this, "On screen: " + MH.activeCreatures().joinToString(" + ") { P.displayName(it) }, Toast.LENGTH_SHORT).show()
                }.show()
        }
        for (slot in 0..2) {
            val b = button("") { pickSlot(slot) }
            slotBtns += b
            duoCard.addView(b, btnLp())
        }
        refreshSlots()
        duoCard.addView(android.widget.CheckBox(this).apply {
            text = "🎬 Funny skits between friends (chase, pranks, snack heists)"
            isChecked = ChatHub.settings.skitsEnabled
            setOnCheckedChangeListener { _, on -> ChatHub.settings.skitsEnabled = on }
        })
        duoCard.addView(button("🎬  Start a skit now", primary = true) {
            if (MonkeyOverlayService.running) v5("skit")
            else Toast.makeText(this, "Start the companion on screen first", Toast.LENGTH_SHORT).show()
        }, btnLp())
        col.addView(duoCard)
        return col
    }

    fun populateRosterGrid() {
        val gridBox = rosterGridContainer ?: return
        gridBox.removeAllViews()
        charThumbs.clear()
        val allChars = CharacterRegistry.allWithCustom
        for (chunk in allChars.chunked(3)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
            }
            for (c in chunk) {
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(4); rightMargin = dp(4) }
                }
                val pv = CharacterPreviewView(this, c).apply {
                    picked = c.id == CharacterRegistry.current.id
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(112))
                }
                charThumbs.add(pv)
                cell.addView(pv)
                cell.addView(TextView(this).apply {
                    text = c.name; textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                cell.setOnClickListener {
                    ChatHub.settings.characterId = c.id
                    ChatHub.settings.applyCharacter()
                    MonkeyOverlayService.syncCrew(this@MainActivity)
                    refresh()
                    refreshMindCard()
                    Toast.makeText(this@MainActivity, "Switched to ${c.name}!", Toast.LENGTH_SHORT).show()
                }
                row.addView(cell)
            }
            gridBox.addView(row)
        }
    }

    private fun buildWorldAndCareTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // 1. Co-Presence Rituals & Heart Comfort Card
        val careCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        careCard.addView(TextView(this).apply {
            text = "❤️ Co-Presence Rituals & Heart Comfort"
            textSize = 16.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
        })
        careCard.addView(button("📚  Start 25-min Focus / Study Desk Buddy", primary = true) { v5("focus") }, btnLp())
        careCard.addView(button("🫧  Start 60-sec Calm Breathing Buddy (4-4-6)") { v5("breathe") }, btnLp())
        careCard.addView(button("🔥  Light Night Campfire at Screen Bottom") { v5("campfire") }, btnLp())
        careCard.addView(button("🫂  Glass Knock + Screen Hug") { v5("comfort_hug") }, btnLp())
        careCard.addView(button("🪧  Hold Up Cheer-Up Sign Card") { v5("comfort_sign") }, btnLp())
        careCard.addView(button("☕  Offer Brass Davara Filter Coffee") { v5("comfort_coffee") }, btnLp())
        col.addView(careCard)

        // 2. Phone-as-a-World & Mind Status Card
        mindText = status()
        moodBars = MoodBarsView(this)
        col.addView(section("🌍 The Phone as a World · Mind & Mood", mindText, button("📖  Open Diary, Memory Limits & Sliders", primary = true) {
            startActivity(Intent(this, DiaryActivity::class.java))
        }).also {
            val l = it as LinearLayout
            l.addView(moodBars, 1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(4) })
        })

        // 3. Friendship & Games Card
        friendText = status()
        col.addView(section("🤝 Friendship & Mini-Games", friendText, button("🎮  Play Mini-Games", primary = true) { v5("games") }).also {
            val l = it as LinearLayout
            l.addView(button("💥  Screen Smash!", primary = true) {
                // Step back so the game smashes the screen you were on (home screen / last app), not this page.
                moveTaskToBack(true)
                android.os.Handler(mainLooper).postDelayed({ com.combo.monkeycompanion.smash.SmashActivity.launch(applicationContext) }, 450)
            }, btnLp())
            l.addView(button("🎬  Record a 5s Shareable Clip") { v5("clip") }, btnLp())
            l.addView(button("📱📱  Two Phones (Same Wi-Fi)") {
                startActivity(Intent(this, com.combo.monkeycompanion.fun5.PairActivity::class.java))
            }, btnLp())
        })
        return col
    }

    private fun buildSettingsTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // 0. Pinned #1 Highlighted "🧠 Give Brain (Connect AI Model)" Card at the very top of Settings!
        val giveBrainSettingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.rgb(0x23, 0x1B, 0x3A))
                setStroke(dp(2), Color.rgb(0xFF, 0xC1, 0x07))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        giveBrainSettingsCard.addView(TextView(this).apply {
            text = "🧠 Give Brain to Set-Tai (Highlighted AI Control)"
            textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(0xFF, 0xD5, 0x4F))
        })
        settingsBrainStatusTv = TextView(this).apply {
            text = ChatHub.giveBrainStatusLine() + " — Strictly OFF until you enable Give Brain!"
            textSize = 12.5f; setTextColor(Color.rgb(0xE0, 0xD8, 0xF0)); setPadding(0, dp(4), 0, dp(8))
        }
        giveBrainSettingsCard.addView(settingsBrainStatusTv)
        val gbSetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gbSetRow.addView(button("🧠 Give Brain (Quick Setup)", primary = true) {
            com.combo.monkeycompanion.ui.GiveBrainDialog.show(this@MainActivity, onChanged = { refresh() })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f).apply { rightMargin = dp(6) })
        settingsBrainToggleBtn = button(if (ChatHub.isBrainEnabled()) "🔒 Turn OFF" else "✅ Turn ON") {
            ChatHub.setBrainEnabled(!ChatHub.isBrainEnabled())
            refresh()
        }
        gbSetRow.addView(settingsBrainToggleBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f))
        giveBrainSettingsCard.addView(gbSetRow)
        col.addView(giveBrainSettingsCard)

        // 1. Diary, Behaviour Sliders, Skills & Brain Settings Card
        brainText = status()
        col.addView(section("🧠 Brain, Diary Limits & Behaviour Sliders", brainText, button("📖  Diary Limits & Behaviour Sliders", primary = true) {
            startActivity(Intent(this, DiaryActivity::class.java))
        }).also {
            val l = it as LinearLayout
            l.addView(button("⚙️  Brain, Voice & Character Settings") {
                startActivity(Intent(this, SettingsActivity::class.java))
            }, btnLp())
            l.addView(button("🛠️  Set-Tai Skills & Phone Powers") {
                startActivity(Intent(this, SkillsActivity::class.java))
            }, btnLp())
        })

        // 2. Permissions & System Health Cards
        overlayStatus = status(); notifStatus = status(); notifAccessStatus = status(); batteryStatus = status()
        overlayBtn = button("Allow \"Display over other apps\"") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        notifBtn = button("Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        notifAccessBtn = button("Grant Notification Access (for What You Missed Card)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        batteryBtn = button("Keep Set-Tai awake (no battery limits)") { askBatteryExemption() }

        col.addView(section("1. Overlay Permission", overlayStatus, overlayBtn))
        col.addView(section("2. Notification Summary Access", notifAccessStatus, notifAccessBtn))
        if (Build.VERSION.SDK_INT >= 33) col.addView(section("3. Post Notifications", notifStatus, notifBtn))
        col.addView(section("4. Battery Health (Recommended)", batteryStatus, batteryBtn))

        // 3. Live Wallpaper Mode Card
        wallText = status()
        col.addView(section("🌴 Live Wallpaper Mode", wallText, button("Set as Live Wallpaper", primary = true) { setWallpaper() }).also {
            val l = it as LinearLayout
            l.addView(button("Edit Climbing Spots") { startActivity(Intent(this, SpotsEditorActivity::class.java)) }, btnLp())
            l.addView(button("Wallpaper Background") { pickBackground() }, btnLp())
        })
        return col
    }

    private fun status() = TextView(this).apply { textSize = 13.8f; setTextColor(brown); setPadding(0, dp(2), 0, dp(4)) }

    private fun btnLp() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(if (primary) Color.WHITE else brown)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (primary) accent else Color.WHITE)
            setStroke(dp(2), if (primary) accent else Color.rgb(0xE6, 0xC9, 0xA3))
        }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

    private fun section(title: String, status: TextView, btn: Button): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
        addView(TextView(context).apply {
            text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
        })
        addView(status)
        addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun setWallpaper() {
        if (MonkeyOverlayService.running) {
            MonkeyOverlayService.stop(this)
            Toast.makeText(this, "Settai left the overlay to move into your wallpaper.", Toast.LENGTH_SHORT).show()
        }
        try { startActivity(SettaiWallpaperService.chooserIntent(this)) }
        catch (e: Exception) {
            try { startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            catch (_: Exception) { Toast.makeText(this, "This phone doesn't support live wallpapers.", Toast.LENGTH_LONG).show() }
        }
    }

    private val bgColors = intArrayOf(0xFF2E5E4E.toInt(), 0xFF1E2A44.toInt(), 0xFFE9D8B4.toInt(), 0xFF3A2A4A.toInt())

    private fun pickBackground() {
        val items = arrayOf("Soft jungle (default)", "Plain: deep green", "Plain: night blue", "Plain: warm sand", "Plain: dusk purple", "My own image…")
        AlertDialog.Builder(this).setTitle("Wallpaper background").setItems(items) { _, i ->
            when (i) {
                0 -> WallpaperPrefs.setBackground(this, WallpaperPrefs.BG_JUNGLE)
                in 1..4 -> WallpaperPrefs.setBackground(this, WallpaperPrefs.BG_COLOR, color = bgColors[i - 1])
                else -> {
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                    @Suppress("DEPRECATION") startActivityForResult(pick, 52)
                }
            }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data ?: return
        if (requestCode == 52 && resultCode == RESULT_OK) {
            runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            WallpaperPrefs.setBackground(this, WallpaperPrefs.BG_IMAGE, image = u.toString())
        }
    }

    private fun startMonkey() {
        if (SettaiWallpaperService.isActive(this)) {
            AlertDialog.Builder(this)
                .setTitle("Settai lives in your wallpaper")
                .setMessage("Only one Settai at a time. Move Settai to the floating overlay?")
                .setPositiveButton("Use overlay") { _, _ -> startOverlay() }
                .setNegativeButton("Keep wallpaper", null)
                .show()
            return
        }
        startOverlay()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            selectTab(Tab.SETTINGS)
            runningText.text = "First allow \"Display over other apps\", then come back here."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        MonkeyOverlayService.start(this)
        startBtn.postDelayed({ refresh() }, 300)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val st = ChatHub.settings
        val cur = CharacterRegistry.current
        if (::topActiveBadge.isInitialized) {
            topActiveBadge.text = "Active: ${cur.name} (${cur.noun})"
        }
        if (::utilityStatusTv.isInitialized) utilityStatusTv.text = buildUtilitySummaryText()
        val gbLine = ChatHub.giveBrainStatusLine()
        val gbOn = ChatHub.isBrainEnabled()
        if (::homeBrainStatusTv.isInitialized) homeBrainStatusTv.text = gbLine
        if (::homeBrainToggleBtn.isInitialized) homeBrainToggleBtn.text = if (gbOn) "🔒 Turn OFF" else "✅ Turn ON"
        if (::settingsBrainStatusTv.isInitialized) settingsBrainStatusTv.text = gbLine + " — Strictly OFF until you enable Give Brain!"
        if (::settingsBrainToggleBtn.isInitialized) settingsBrainToggleBtn.text = if (gbOn) "🔒 Turn OFF" else "✅ Turn ON"
        brainText.text = gbLine
        val overlay = Settings.canDrawOverlays(this)
        overlayStatus.text = if (overlay) "✅ Active. ${cur.name} can live on top of your apps."
        else "⚠️ Tap to grant so ${cur.name} can walk & fly on top of other apps."
        overlayBtn.visibility = if (overlay) View.GONE else View.VISIBLE

        val nAccess = SettaiNotificationListener.isAccessGranted(this)
        notifAccessStatus.text = if (nAccess) "✅ Active. 'What You Missed' summary card is ready."
        else "⚠️ Optional: grant Notification Access to group missed messages."
        notifAccessBtn.visibility = if (nAccess) View.GONE else View.VISIBLE

        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        notifStatus.text = if (notif) "✅ Active."
        else "⚠️ Needed for Stop / Dance notification controls."
        notifBtn.visibility = if (notif) View.GONE else View.VISIBLE

        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        val energyNote = "⚡ Energy-Smart Adaptive Engine: 60fps active · 24fps idle · 8fps sleep · 0fps screen-off.\n"
        batteryStatus.text = energyNote + if (exempt) "✅ Active. Phone won't put ${cur.name} to sleep."
        else "Recommended: keeps ${cur.name} smooth in the background."
        batteryBtn.visibility = if (exempt) View.GONE else View.VISIBLE

        val running = MonkeyOverlayService.running
        val quit = !running && MonkeyOverlayService.quitByUser(this)
        runningText.text = when {
            running -> "✅ ${cur.name} is out on your screen right now!"
            quit -> "${cur.name} is resting off-screen. Tap below to summon!"
            else -> "Ready to summon ${cur.name} onto your screen."
        }
        if (::topToggleBtn.isInitialized) {
            topToggleBtn.text = if (running) "⏹ Stop Overlay" else "▶ Summon"
            topToggleBtn.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (running) Color.rgb(0xC6, 0x28, 0x28) else accent)
            }
        }
        startBtn.text = if (quit) "✨  Bring ${cur.name} Back" else "✨  Start ${cur.name} on Screen"
        startBtn.isEnabled = !running
        startBtn.alpha = if (running) 0.5f else 1f
        stopBtn.isEnabled = running
        stopBtn.alpha = if (running) 1f else 0.5f

        val wall = SettaiWallpaperService.isActive(this)
        wallText.text = when {
            wall && running -> "✅ Wallpaper is set (currently in overlay mode)."
            wall -> "✅ ${cur.name} lives in your home-screen wallpaper."
            else -> "Optional: run ${cur.name} inside your home-screen live wallpaper."
        }
    }
}
