package com.combo.settai.desktop

import com.combo.monkeycompanion.ai.ChatMsg
import java.awt.*
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.border.EmptyBorder

private val ORANGE = Color(0xE8, 0x8A, 0x2E)

/** Settai's brain & behaviour settings. */
class SettingsWindow(private val cfg: Config, private val chat: DesktopChat, private val onSaved: () -> Unit) {
    private val f = JFrame("Settai — Brain & settings")
    private val rbOllama = JRadioButton("Local AI (Ollama, private, offline)")
    private val rbOnline = JRadioButton("Online AI (your API key)")
    private val rbNone = JRadioButton("No AI (canned lines only)")
    private val ollamaUrl = JTextField(cfg.ollamaUrl, 22)
    private val models = JComboBox<String>()
    private val ollamaStatus = JLabel(" ")
    private val pullBtn = JButton("Pull recommended model (${Config.RECOMMENDED_MODEL}, ~3.3 GB)")
    private val pullOther = JTextField("", 12)
    private val pullBar = JProgressBar(0, 1000).apply { isStringPainted = true; string = ""; isVisible = false; preferredSize = java.awt.Dimension(460, 22) }
    private val provider = JComboBox(Providers.all.map { it.label }.toTypedArray())
    private val key = JPasswordField(28)
    private val model = JTextField(18)
    private val customUrl = JTextField(cfg.customUrl, 24)
    private val keyHint = JLabel(" ")
    private val roam = JCheckBox("Roam around (climb edges, hang from the top)", cfg.roam)
    private val quips = JCheckBox("Say things on his own every", cfg.quips)
    private val quipMin = JSpinner(SpinnerNumberModel(cfg.int("quipMinutes", 20), 5, 240, 5))
    private val scale = JSpinner(SpinnerNumberModel(cfg.scale.toDouble(), 0.7, 2.5, 0.05))
    private val autostart = JCheckBox("Start Settai when I log in", cfg.autostart)
    private val testOut = JLabel(" ")
    private var curProvider = cfg.provider

    init {
        ButtonGroup().apply { add(rbOllama); add(rbOnline); add(rbNone) }
        when (cfg.brain) { "online" -> rbOnline; "none" -> rbNone; else -> rbOllama }.isSelected = true
        models.isEditable = true
        models.selectedItem = cfg.ollamaModel
        provider.selectedIndex = Providers.all.indexOfFirst { it.id == cfg.provider }.coerceAtLeast(0)
        loadProvider()
        provider.addActionListener { storeProvider(); curProvider = Providers.all[provider.selectedIndex].id; loadProvider() }

        val root = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = EmptyBorder(12, 14, 12, 14) }
        fun section(title: String) = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title), EmptyBorder(4, 6, 6, 6))
            alignmentX = Component.LEFT_ALIGNMENT
            root.add(this); root.add(Box.createVerticalStrut(8))
        }
        var row = 0
        fun JPanel.line(vararg c: JComponent) {
            val p = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)); c.forEach { p.add(it) }
            add(p, GridBagConstraints().apply { gridx = 0; gridy = row++; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
        }

        section("Settai's brain").apply { line(rbOllama); line(rbOnline); line(rbNone) }
        section("Local AI — Ollama").apply {
            line(JLabel("Server"), ollamaUrl, JButton("Check").apply { addActionListener { refreshModels() } })
            line(JLabel("Model"), models, JButton("Refresh").apply { addActionListener { refreshModels() } })
            line(pullBtn.apply { addActionListener { pull(Config.RECOMMENDED_MODEL) } })
            line(JLabel("Or pull another:"), pullOther, JButton("Pull").apply { addActionListener { pullOther.text.trim().takeIf { it.isNotEmpty() }?.let { pull(it) } } })
            line(pullBar); line(ollamaStatus)
            line(JLabel("<html><small>Gemma 3 4B runs fine on a modern laptop CPU (~4-8 words/s). Smaller &amp; faster: gemma3:1b or qwen3:1.7b.</small></html>"))
        }
        section("Online AI").apply {
            line(JLabel("Provider"), provider)
            line(JLabel("API key"), key)
            line(keyHint)
            line(JLabel("Model"), model)
            line(JLabel("Custom URL"), customUrl)
            line(JLabel("<html><small>" + Platform.current.keyStoreNote + "</small></html>"))
        }
        section("Behaviour").apply {
            line(roam)
            line(quips, quipMin, JLabel("minutes"))
            line(JLabel("Size"), scale, JLabel("(applies after restart)"))
            line(autostart)
            Platform.current.notes?.let { line(JLabel("<html><small>$it</small></html>")) }
            line(JButton("Forget our chat").apply { addActionListener { chat.clearMemory(); testOut.text = "Chat memory cleared." } })
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(testOut)
            add(JButton("Test brain").apply { addActionListener { save(); testOut.text = "Thinking…"; chat.test { r -> testOut.text = "<html>" + r.fold({ "✓ " + it.take(90) }, { "✗ " + (it.message ?: it.toString()).take(120) }) + "</html>" } } })
            add(JButton("Save").apply { background = ORANGE; addActionListener { save(); f.isVisible = false } })
        }
        root.add(buttons)
        f.contentPane = JScrollPane(root).apply { border = null }
        f.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        f.iconImage = iconImage(64)
        f.pack()
        f.setSize(f.width.coerceAtMost(640), f.height.coerceAtMost(860))
        f.setLocationRelativeTo(null)
    }

    private fun loadProvider() {
        val p = Providers.byId(curProvider)
        key.text = cfg.key(p.id); model.text = cfg.model(p.id); keyHint.text = "<html><small>${p.keyHint}</small></html>"
        customUrl.isEnabled = p.id == "CUSTOM"
    }
    private fun storeProvider() {
        cfg.setKey(curProvider, String(key.password).trim()); cfg.setModel(curProvider, model.text.trim())
    }

    fun open() { f.isVisible = true; f.toFront(); refreshModels() }

    private fun base() = ollamaUrl.text.trim().trimEnd('/').ifEmpty { "http://localhost:11434" }

    private fun refreshModels() {
        val b = base()
        ollamaStatus.text = "Checking $b …"
        Thread {
            val r = runCatching { Ollama.version(b) to Ollama.models(b) }
            SwingUtilities.invokeLater {
                r.onSuccess { (v, list) ->
                    val keep = models.selectedItem?.toString().orEmpty()
                    models.removeAllItems(); list.forEach { models.addItem(it.first) }
                    models.selectedItem = keep.ifEmpty { list.firstOrNull()?.first ?: "" }
                    if (keep.isEmpty() && list.isNotEmpty()) models.selectedItem = list.firstOrNull { it.first.startsWith("gemma3") }?.first ?: list[0].first
                    ollamaStatus.text = "<html>Ollama ${v ?: "?"} is running. " + (if (list.isEmpty()) "<b>No models yet</b> — pull one above." else "${list.size} model(s): " +
                        list.joinToString { "${it.first} (%.1f GB)".format(it.second / 1e9) }) + "</html>"
                }.onFailure {
                    ollamaStatus.text = "<html><font color=#b00000>Can't reach Ollama at $b.</font> Install: <tt>curl -fsSL https://ollama.com/install.sh | sh</tt></html>"
                }
                f.pack(); f.setSize(f.width.coerceAtMost(640), f.height.coerceAtMost(860))
            }
        }.apply { isDaemon = true }.start()
    }

    private fun pull(name: String) {
        val b = base()
        pullBtn.isEnabled = false; pullBar.isVisible = true; pullBar.value = 0; pullBar.string = "Starting $name…"
        Thread {
            val r = runCatching {
                Ollama.pull(b, name) { st, frac ->
                    SwingUtilities.invokeLater {
                        pullBar.isIndeterminate = frac < 0
                        if (frac >= 0) pullBar.value = (frac * 1000).toInt()
                        pullBar.string = "$name: $st" + if (frac >= 0) " ${(frac * 100).toInt()}%" else ""
                    }
                }
            }
            SwingUtilities.invokeLater {
                pullBtn.isEnabled = true; pullBar.isIndeterminate = false
                r.onSuccess {
                    pullBar.value = 1000; pullBar.string = "$name ready!"
                    models.selectedItem = name; rbOllama.isSelected = true; save(); refreshModels()
                }.onFailure { pullBar.string = "Pull failed: ${it.message}" }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun save() {
        storeProvider()
        cfg.brain = when { rbOnline.isSelected -> "online"; rbNone.isSelected -> "none"; else -> "ollama" }
        cfg.ollamaUrl = base()
        cfg.ollamaModel = models.selectedItem?.toString()?.trim().orEmpty()
        cfg.provider = curProvider
        cfg.customUrl = customUrl.text.trim()
        cfg.roam = roam.isSelected
        cfg.quips = quips.isSelected
        cfg.set("quipMinutes", quipMin.value.toString())
        cfg.scale = (scale.value as Number).toFloat()
        cfg.autostart = autostart.isSelected
        cfg.save()
        onSaved()
    }
}

/** Full chat history + entry box (the bubble's "expand" button). */
class ChatWindow(private val chat: DesktopChat) : DesktopChat.Listener {
    private val f = JFrame("Chat with Settai")
    private val log = JEditorPane("text/html", "").apply { isEditable = false }
    private val input = JTextField()
    private var live = ""
    private val fmt = SimpleDateFormat("HH:mm")

    init {
        val send = JButton("Send").apply { background = ORANGE }
        val act = { val t = input.text.trim(); if (t.isNotEmpty() && !chat.busy) { input.text = ""; chat.send(t) } }
        send.addActionListener { act() }; input.addActionListener { act() }
        val bottom = JPanel(BorderLayout(6, 0)).apply { border = EmptyBorder(6, 6, 6, 6); add(input); add(send, BorderLayout.EAST) }
        f.contentPane = JPanel(BorderLayout()).apply { add(JScrollPane(log)); add(bottom, BorderLayout.SOUTH) }
        f.setSize(420, 560); f.setLocationRelativeTo(null)
        f.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        f.iconImage = iconImage(64)
        chat.addListener(this)
    }

    fun open() { render(); f.isVisible = true; f.toFront(); input.requestFocusInWindow() }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")
    private fun render() {
        val sb = StringBuilder("<html><body style='font-family:sans-serif;font-size:11pt;margin:6px'>")
        for (m in chat.history()) bubble(sb, m)
        if (live.isNotEmpty()) bubble(sb, ChatMsg(false, live))
        sb.append("</body></html>")
        log.text = sb.toString()
        SwingUtilities.invokeLater { log.caretPosition = log.document.length }
    }
    private fun bubble(sb: StringBuilder, m: ChatMsg) {
        val (bg, al, who) = if (m.fromUser) Triple("#FFE2C2", "right", "You") else Triple(if (m.note) "#F1ECE6" else "#FFFFFF", "left", "Settai")
        sb.append("<div align='$al'><table cellpadding=6 style='background:$bg'><tr><td><small><b>$who</b> ${fmt.format(Date(m.time))}</small><br>${esc(m.text)}</td></tr></table></div><br>")
    }

    override fun onThinking() { live = "…"; if (f.isVisible) render() }
    override fun onReply(text: String, done: Boolean) { live = if (done) "" else text; if (f.isVisible) render() }
    override fun onAnnounce(text: String) { if (f.isVisible) render() }
}
