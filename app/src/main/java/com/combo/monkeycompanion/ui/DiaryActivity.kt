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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
        color = Color.rgb(0x1E, 0x22, 0x40); textSize = 12f * d
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x64, 0x6A, 0x8C); textSize = 11.5f * d; textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF3, 0xE5, 0xD0) }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast((240 * d).toInt())
        val h = (7 * 22 * d + 8 * d).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(c: Canvas) {
        val rows = listOf(
            Triple("⚡ Energy", drives.energy, Color.rgb(0x43, 0xA0, 0x47)),
            Triple("🧭 Curiosity", drives.curiosity, Color.rgb(0x1E, 0x88, 0xE5)),
            Triple("💛 Social", drives.social, Color.rgb(0xFB, 0x8C, 0x00)),
            Triple("😈 Boredom", drives.boredom, Color.rgb(0x8E, 0x24, 0xAA)),
            Triple("😌 Content", drives.contentment, Color.rgb(0x00, 0x89, 0x7B)),
            Triple("😤 Grumpy", drives.annoyance, Color.rgb(0xE5, 0x39, 0x35)),
            Triple("🤫 Focus respect", focus, Color.rgb(0x54, 0x6E, 0x7A)),
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
 * v6 / v9 "Settai's diary & behaviour controls":
 * - Each character's diary (including custom companions like Kuttyma).
 * - User-configurable Diary Limits (`maxEntries`, `retentionDays`, category toggles, pinned memories/rules).
 * - Per-character Behaviour Sliders (`Playfulness`, `Chattiness`, `Curiosity`, `Energy`, `Boldness`, `Drama`).
 */
class DiaryActivity : Activity() {
    private val d by lazy { resources.displayMetrics.density }
    private val cream = Color.rgb(0xF3, 0xF4, 0xFA)
    private val brown = Color.rgb(0x1E, 0x22, 0x40)
    private val soft = Color.rgb(0x64, 0x6A, 0x8C)
    private val accent = Color.rgb(0x6C, 0x5C, 0xE7)
    private lateinit var who: Character

    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        MindHub.init(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        who = CharacterRegistry.byId(intent?.getStringExtra("character")) ?: CharacterRegistry.current
        rebuild()
    }

    private fun rebuild() {
        val m = MindHub.mindFor(who.id)
        val now = System.currentTimeMillis()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        col.addView(TextView(this).apply {
            text = "📖 ${who.name}'s Diary & Behaviour"
            textSize = 25f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        col.addView(TextView(this).apply {
            text = "Tune ${who.name}'s behaviour sliders, diary limits, and pinned memories on this phone."
            textSize = 13f; setTextColor(soft); gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, dp(10))
        })

        // Character switcher pills (includes built-in + custom companions)
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in CharacterRegistry.allWithCustom) {
            val sel = c.id == who.id
            tabs.addView(Button(this).apply {
                text = c.name; isAllCaps = false; textSize = 13f
                setTextColor(if (sel) Color.WHITE else brown)
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (sel) accent else Color.WHITE)
                    setStroke(dp(1), if (sel) accent else Color.rgb(0xDD, 0xE0, 0xF0))
                }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { who = c; rebuild() }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        tabScroll.addView(tabs)
        col.addView(tabScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        if (m != null) {
            val fav = m.personality.fav
            val tr = m.traits
            // 1. Personality & Mood Bars Card
            val prof = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(14))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            topRow.addView(CharacterPreviewView(this, who).apply {
                layoutParams = LinearLayout.LayoutParams(dp(86), dp(96))
            })
            topRow.addView(TextView(this).apply {
                text = "💭 ${m.thinking(now)}"
                textSize = 13.5f; setTextColor(brown); setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            prof.addView(topRow)

            prof.addView(MoodBarsView(this).apply {
                drives = m.drives; focus = m.focus(now)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
            })
            prof.addView(TextView(this).apply {
                text = "Favourites (seeded for your install):\n" +
                    "• Hangout: ${fav.corner.label} · Nap spot: ${fav.napSpot.label}\n" +
                    "• Loves: ${fav.favApp.label} apps · Dislikes: ${fav.hatedApp.label} apps\n" +
                    "• Snack: ${fav.snack}" + (if (fav.power.isNotBlank()) " · Power: ${fav.power}" else "")
                textSize = 12.5f; setTextColor(soft); setPadding(0, dp(8), 0, 0)
            })
            col.addView(prof)

            // 2. Per-Character Behaviour Sliders Card
            val behCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(14))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            behCard.addView(TextView(this).apply {
                text = "🎛️ ${who.name}'s Behaviour Sliders"
                textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
                setPadding(0, 0, 0, dp(4))
            })
            fun updateTrait(mod: (Traits) -> Traits) {
                val cur = Personality.getCustomTraits(who.id) ?: m.traits
                Personality.setCustomTraits(who.id, mod(cur))
                MindHub.flush()
            }
            behCard.addView(traitSlider("🎉 Playfulness (dances & gags)", tr.playfulness) { v -> updateTrait { it.copy(playfulness = v) } })
            behCard.addView(traitSlider("💬 Chattiness (how often they speak)", tr.chattiness) { v -> updateTrait { it.copy(chattiness = v) } })
            behCard.addView(traitSlider("🧭 Curiosity (explores new apps)", tr.curiosity) { v -> updateTrait { it.copy(curiosity = v) } })
            behCard.addView(traitSlider("⚡ Energy / Activity (active vs napping)", tr.energy) { v -> updateTrait { it.copy(laziness = (1f - v).coerceIn(0.05f, 0.95f)) } })
            behCard.addView(traitSlider("🦁 Boldness (heroic stunts & powers)", tr.boldness) { v -> updateTrait { it.copy(boldness = v) } })
            behCard.addView(traitSlider("🎭 Drama (expressive reactions)", tr.drama) { v -> updateTrait { it.copy(drama = v) } })
            behCard.addView(Button(this).apply {
                text = "Reset behaviour to ${who.name}'s default"; isAllCaps = false; textSize = 12.5f; setTextColor(brown)
                setOnClickListener {
                    Personality.clearCustomTraits(who.id)
                    MindHub.flush()
                    rebuild()
                }
            })
            col.addView(behCard)

            // 3. Diary Limits & Retention Controls Card
            val diary = m.diary
            val limCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(14))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            limCard.addView(TextView(this).apply {
                text = "🔒 Diary Limits & What ${who.name} Remembers"
                textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
                setPadding(0, 0, 0, dp(4))
            })
            limCard.addView(TextView(this).apply {
                text = "Max diary entries (pinned entries are never deleted):"
                textSize = 12.5f; setTextColor(soft); setPadding(0, dp(4), 0, dp(4))
            })
            val capRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((capVal, lbl) in listOf(0 to "Pause", 10 to "10", 30 to "30", 60 to "60", 150 to "150", 300 to "300")) {
                val sel = diary.maxEntries == capVal
                capRow.addView(Button(this).apply {
                    text = lbl; isAllCaps = false; textSize = 12f
                    setTextColor(if (sel) Color.WHITE else brown)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(if (sel) accent else Color.rgb(0xFF, 0xF9, 0xEE))
                    }
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    setOnClickListener {
                        diary.maxEntries = capVal
                        diary.enforceLimits(now)
                        MindHub.flush()
                        rebuild()
                    }
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
            }
            limCard.addView(capRow)

            limCard.addView(TextView(this).apply {
                text = "Auto-expire old entries:"
                textSize = 12.5f; setTextColor(soft); setPadding(0, dp(8), 0, dp(4))
            })
            val retRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((days, lbl) in listOf(0 to "Forever", 1 to "1 Day", 7 to "7 Days", 30 to "30 Days")) {
                val sel = diary.retentionDays == days
                retRow.addView(Button(this).apply {
                    text = lbl; isAllCaps = false; textSize = 12f
                    setTextColor(if (sel) Color.WHITE else brown)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(if (sel) accent else Color.rgb(0xFF, 0xF9, 0xEE))
                    }
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    setOnClickListener {
                        diary.retentionDays = days
                        diary.enforceLimits(now)
                        MindHub.flush()
                        rebuild()
                    }
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
            }
            limCard.addView(retRow)

            limCard.addView(CheckBox(this).apply {
                text = "🧭 Log app places & visits"; isChecked = diary.logPlaces; textSize = 13f; setTextColor(brown)
                setOnCheckedChangeListener { _, b -> diary.logPlaces = b; MindHub.flush() }
            })
            limCard.addView(CheckBox(this).apply {
                text = "💬 Log chats, head pats & games"; isChecked = diary.logChats; textSize = 13f; setTextColor(brown)
                setOnCheckedChangeListener { _, b -> diary.logChats = b; MindHub.flush() }
            })
            limCard.addView(CheckBox(this).apply {
                text = "🌙 Log naps & away traces"; isChecked = diary.logNight; textSize = 13f; setTextColor(brown)
                setOnCheckedChangeListener { _, b -> diary.logNight = b; MindHub.flush() }
            })
            limCard.addView(CheckBox(this).apply {
                text = "⚡ Log superpowers & world events"; isChecked = diary.logWorld; textSize = 13f; setTextColor(brown)
                setOnCheckedChangeListener { _, b -> diary.logWorld = b; MindHub.flush() }
            })

            val noteBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
            noteBtnRow.addView(Button(this).apply {
                text = "📌 + Add Memory / Rule"; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(accent) }
                setOnClickListener { promptAddCustomNote(diary) }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            noteBtnRow.addView(Button(this).apply {
                text = "🧹 Clear Unpinned"; isAllCaps = false; textSize = 13f; setTextColor(brown)
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.rgb(0xF5, 0xE6, 0xD0)) }
                setOnClickListener {
                    diary.clearUnpinned()
                    MindHub.flush()
                    rebuild()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            limCard.addView(noteBtnRow)
            col.addView(limCard)

            // 4. Diary Entries Card (with Pin & Delete on each row)
            val diaryCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(14))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            diaryCard.addView(TextView(this).apply {
                text = "Dear diary… (${diary.all.size}/${if (diary.maxEntries == 0) "Paused" else diary.maxEntries.toString()} memories)"
                textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(brown)
                setPadding(0, 0, 0, dp(6))
            })
            val pageRows = diary.pageEntries(who.id, now, 60)
            if (pageRows.isEmpty()) {
                diaryCard.addView(TextView(this).apply {
                    text = "No entries yet! Tap '+ Add Memory / Rule' above, or poke ${who.name} and open a few apps."
                    textSize = 13.5f; setTextColor(soft)
                })
            } else {
                for ((entry, whenStr, body) in pageRows) {
                    val item = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(if (entry.pinned) Color.rgb(0xFF, 0xF3, 0xD6) else Color.rgb(0xFF, 0xF9, 0xEE))
                            setStroke(dp(1), if (entry.pinned) accent else Color.rgb(0xF0, 0xDF, 0xC4))
                        }
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
                    }
                    val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    headRow.addView(TextView(this).apply {
                        text = whenStr; textSize = 11.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(accent)
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    headRow.addView(TextView(this).apply {
                        text = if (entry.pinned) "📌 Unpin" else "📍 Pin"
                        textSize = 11.5f; setTextColor(brown); setPadding(dp(8), dp(2), dp(8), dp(2))
                        setOnClickListener {
                            diary.togglePin(entry.ts)
                            MindHub.flush()
                            rebuild()
                        }
                    })
                    headRow.addView(TextView(this).apply {
                        text = "🗑️"
                        textSize = 12f; setPadding(dp(6), dp(2), dp(2), dp(2))
                        setOnClickListener {
                            diary.deleteEntry(entry.ts)
                            MindHub.flush()
                            rebuild()
                        }
                    })
                    item.addView(headRow)
                    item.addView(TextView(this).apply {
                        text = body; textSize = 13.5f; setTextColor(brown); setPadding(0, dp(2), 0, 0)
                    })
                    diaryCard.addView(item)
                }
            }
            col.addView(diaryCard)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(cream)
            isFillViewport = true
            addView(col)
        }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
    }

    private fun promptAddCustomNote(diary: com.combo.monkeycompanion.mind.Diary) {
        val input = EditText(this).apply {
            hint = "E.g. Always call me Appa and cheer me on before meetings!"
        }
        AlertDialog.Builder(this)
            .setTitle("📌 Add Pinned Memory / Rule for ${who.name}")
            .setView(input)
            .setPositiveButton("Save & Pin") { _, _ ->
                val txt = input.text?.toString()?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    diary.addCustomNote(txt, System.currentTimeMillis(), pinned = true)
                    MindHub.flush()
                    rebuild()
                    Toast.makeText(this, "Pinned to ${who.name}'s diary!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun traitSlider(label: String, value: Float, onChange: (Float) -> Unit): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
        val tv = TextView(this).apply {
            text = "$label: ${(value * 100).toInt()}%"
            textSize = 12.5f; setTextColor(brown)
        }
        val sb = SeekBar(this).apply {
            max = 100
            progress = (value * 100).toInt().coerceIn(5, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = (progress.coerceIn(5, 100)) / 100f
                    tv.text = "$label: ${(v * 100).toInt()}%"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        wrap.addView(tv); wrap.addView(sb)
        return wrap
    }
}
