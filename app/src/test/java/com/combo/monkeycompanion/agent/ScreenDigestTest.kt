package com.combo.monkeycompanion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.2 smart screen reading: digests from fake accessibility trees, the dump detector and the templates. */
class ScreenDigestTest {
    /** Tiny fake-tree builder: each node names its parent (nearest kept ancestor). */
    class FakeTree(val pkg: String, val app: String) {
        val nodes = ArrayList<UiNode>()
        fun n(parent: Int, text: String = "", desc: String = "", id: String = "", click: Boolean = false, edit: Boolean = false,
              cls: String = "android.widget.TextView", top: Int = 0, left: Int = 0, password: Boolean = false, scroll: Boolean = false): Int {
            val i = nodes.size + 1
            nodes.add(UiNode(i, cls, text, desc, if (id.isEmpty()) "" else "$pkg:id/$id", click, edit, scroll, null, password, false, 0, parent, top, left))
            return i
        }
        fun snap() = ScreenSnapshot(pkg, app, nodes)
    }

    private fun whatsappList(): ScreenSnapshot {
        val t = FakeTree("com.whatsapp", "WhatsApp")
        t.n(0, "WhatsApp", top = 40)
        t.n(0, desc = "Camera", click = true, cls = "android.widget.ImageButton", top = 40, left = 800)
        t.n(0, desc = "Search", click = true, cls = "android.widget.ImageButton", top = 40, left = 900)
        val list = t.n(0, cls = "androidx.recyclerview.widget.RecyclerView", scroll = true, top = 200)
        fun row(top: Int, name: String, time: String, msg: String, count: Int?) {
            val r = t.n(list, click = true, cls = "android.widget.RelativeLayout", top = top)
            t.n(r, desc = "Profile photo", click = true, cls = "android.widget.ImageView", top = top, left = 10)
            t.n(r, name, id = "conversations_row_contact_name", top = top, left = 150)
            t.n(r, time, id = "conversations_row_date", top = top, left = 900)
            t.n(r, msg, id = "single_msg_tv", top = top + 50, left = 150)
            if (count != null) t.n(r, "$count", desc = "$count unread messages", id = "conversations_row_message_count", top = top + 50, left = 950)
        }
        row(300, "Amma", "10:42", "Did you eat?", 2)
        row(450, "Ravi", "9:10", "match tonight?", 1)
        row(600, "College Gang", "Yesterday", "Priya: photos pls", null)
        row(750, "HDFC Alerts", "Yesterday", "Your OTP is 482913 for login", null)
        t.n(0, desc = "New chat", click = true, cls = "android.widget.ImageButton", top = 1800)
        for ((i, tab) in listOf("Chats", "Updates", "Communities", "Calls").withIndex()) t.n(0, tab, click = true, top = 2000, left = i * 250)
        return t.snap()
    }

    private fun whatsappChat(): ScreenSnapshot {
        val t = FakeTree("com.whatsapp", "WhatsApp")
        t.n(0, desc = "Navigate up", click = true, cls = "android.widget.ImageButton", top = 40)
        t.n(0, "Amma", id = "conversation_contact_name", click = true, top = 40, left = 150)
        t.n(0, "online", id = "conversation_contact_status", top = 90, left = 150)
        val list = t.n(0, cls = "android.widget.ListView", scroll = true, top = 200)
        t.n(list, "Today", top = 210)
        fun msg(top: Int, text: String, time: String, mine: Boolean) {
            val r = t.n(list, click = true, cls = "android.view.ViewGroup", top = top)
            t.n(r, text, id = "message_text", top = top)
            t.n(r, time, id = "date", top = top + 30, left = 700)
            if (mine) t.n(r, desc = "Read", id = "status", cls = "android.widget.ImageView", top = top + 30, left = 800)
        }
        msg(300, "Did you eat?", "10:40", false)
        msg(400, "Yes ma, dosa", "10:41", true)
        msg(500, "Only dosa? Eat some fruits also", "10:42", false)
        t.n(0, desc = "Message", edit = true, cls = "android.widget.EditText", id = "entry", top = 1900)
        t.n(0, desc = "Voice message", click = true, cls = "android.widget.ImageButton", top = 1900, left = 950)
        return t.snap()
    }

    @Test fun whatsappChatList() {
        val d = Digest.build(whatsappList())
        assertEquals(ScreenType.CHAT_LIST, d.type)
        assertEquals("Amma", d.items[0].title)
        assertEquals(2, d.items[0].unread)
        assertEquals("Did you eat?", d.items[0].detail)
        assertEquals("10:42", d.items[0].time)
        assertEquals(2, d.unreadChats)
        assertEquals(3, d.unreadTotal)
        assertTrue(d.buttons.toString(), "New chat" in d.buttons && "Calls" in d.buttons)
        assertFalse(d.buttons.contains("Amma"))
        val text = Digest.forBrain(d)
        assertTrue(text, text.contains("Unread: 2 chats, 3 messages"))
        assertTrue(text, text.contains("Amma (2 unread, 10:42): \"Did you eat?\""))
        assertFalse("OTP must be masked", text.contains("482913"))
        val line = ScreenTalk.template(ScreenTalk.Mode.SUMMARY, d)
        assertTrue(line, line.startsWith("2 unread chats! Amma's is newest"))
        assertFalse(ScreenTalk.looksLikeDump(line, d))
        assertEquals("open", ScreenTalk.followUps(ScreenTalk.Mode.SUMMARY, d)[0].id)
        assertEquals("Amma", ScreenTalk.followUps(ScreenTalk.Mode.SUMMARY, d)[0].arg)
    }

    @Test fun whatsappConversation() {
        val d = Digest.build(whatsappChat())
        assertEquals(ScreenType.CONVERSATION, d.type)
        assertEquals("Amma", d.title)
        assertEquals(3, d.items.size)
        assertTrue(d.items[1].fromMe)
        assertFalse(d.items[2].fromMe)
        assertEquals("Only dosa? Eat some fruits also", d.items[2].detail)
        assertEquals("online", d.facts["status"])
        val s = ScreenTalk.template(ScreenTalk.Mode.SUMMARY, d)
        assertTrue(s, s.startsWith("Amma says “Only dosa?"))
        assertTrue(ScreenTalk.template(ScreenTalk.Mode.CHAT_SUMMARY, d).contains("Your turn to reply"))
        assertEquals("Yes, ate! You? 😊", ScreenTalk.draftReply(d))
        assertTrue(Digest.forBrain(d).contains("You: \"Yes ma, dosa\""))
        assertEquals(listOf("summarise", "reply_for_me"), ScreenTalk.contextChips(d.type).map { it.id })
    }

    @Test fun youtubeFeed() {
        val t = FakeTree("com.google.android.youtube", "YouTube")
        t.n(0, desc = "Search", click = true)
        t.n(0, desc = "Learn Kotlin in 10 minutes - 10 minutes, 5 seconds - Go to channel - CodeWithMonkey - 1.2M views - 2 years ago - play video", click = true, top = 300)
        t.n(0, desc = "Best Chennai biryani?? - 22 minutes - Go to channel - Foodie Anna - 88K views - 3 days ago - play video", click = true, top = 900)
        t.n(0, "Home", click = true, top = 2000); t.n(0, "Shorts", click = true, top = 2000, left = 200)
        val d = Digest.build(t.snap())
        assertEquals(ScreenType.VIDEO_LIST, d.type)
        assertEquals("Learn Kotlin in 10 minutes", d.items[0].title)
        assertEquals("10 minutes, 5 seconds", d.items[0].time)
        assertTrue(d.items[0].detail, d.items[0].detail.startsWith("CodeWithMonkey"))
        assertEquals(2, d.items.size)
        assertTrue(ScreenTalk.template(ScreenTalk.Mode.SUMMARY, d).contains("2 videos"))
        assertEquals("worth", ScreenTalk.contextChips(d.type)[0].id)
    }

    @Test fun youtubeWatchPage() {
        val t = FakeTree("com.google.android.youtube", "YouTube")
        t.n(0, "2:13 / 18:40", top = 100)
        t.n(0, "Chennai street food tour | Best dosa in town", id = "title", top = 700)
        t.n(0, "1.2M views  3 days ago  #food", top = 760)
        t.n(0, "Foodie Anna", top = 850)
        t.n(0, "245K subscribers", top = 880)
        t.n(0, "Subscribe", desc = "Subscribe to Foodie Anna.", click = true, top = 850, left = 800)
        t.n(0, desc = "like this video along with 12K other people", click = true, top = 950)
        t.n(0, desc = "Masala dosa at home - 8:12 - Go to channel - Amma's Kitchen - 50K views - 1 year ago - play video", click = true, top = 1300)
        val d = Digest.build(t.snap())
        assertEquals(ScreenType.VIDEO, d.type)
        assertEquals("Chennai street food tour | Best dosa in town", d.title)
        assertEquals("Foodie Anna", d.facts["channel"])
        assertEquals("18:40", d.facts["duration"])
        assertEquals("Masala dosa at home", d.items[0].title)
        val w = ScreenTalk.template(ScreenTalk.Mode.WORTH, d)
        assertTrue(w, w.contains("Chennai street food tour"))
        val s = ScreenTalk.template(ScreenTalk.Mode.SUMMARY, d)
        assertTrue(s, s.contains("by Foodie Anna (18:40)"))
    }

    @Test fun passwordsNeverAndGenericFallback() {
        val t = FakeTree("com.example.bank2", "Some App")
        t.n(0, "Sign in", top = 50)
        t.n(0, "hunter2", password = true, edit = true, cls = "android.widget.EditText", top = 300)
        t.n(0, "Forgot password?", click = true, top = 400)
        val d = Digest.build(t.snap())
        val txt = Digest.forBrain(d) + ScreenTalk.template(ScreenTalk.Mode.SUMMARY, d)
        assertFalse(txt.contains("hunter2"))
        assertEquals(ScreenType.OTHER, d.type)
        assertTrue(ScreenTalk.template(ScreenTalk.Mode.SUMMARY, d).contains("Some App"))
    }

    @Test fun dumpDetection() {
        assertTrue(ScreenTalk.looksLikeDump("👓 I'm looking at WhatsApp. I see: Message your assistant · New chat · Camera · Search"))
        assertTrue(ScreenTalk.looksLikeDump("- Amma\n- Ravi\n- College Gang\n- Swiggy"))
        assertTrue(ScreenTalk.looksLikeDump("1. Amma\n2. Ravi\n3. College Gang"))
        assertTrue(ScreenTalk.looksLikeDump("App: WhatsApp (chat list)\nUnread: 2 chats"))
        assertTrue(ScreenTalk.looksLikeDump("Amma, Ravi, College Gang, Swiggy, New chat, Camera, Search, Calls"))
        assertTrue(ScreenTalk.looksLikeDump("[3] Button \"Send\" [5] TextField"))
        assertTrue(ScreenTalk.looksLikeDump(""))
        val d = Digest.build(whatsappList())
        assertTrue(ScreenTalk.looksLikeDump("Amma Ravi College Gang HDFC Alerts New chat Calls Chats Updates there you go", d))
        assertFalse(ScreenTalk.looksLikeDump("2 unread chats! Amma's is newest, asking if you ate 🍛. Want me to open it?", d))
        assertFalse(ScreenTalk.looksLikeDump("Ooh, you're watching a dosa video, now I'm hungry! Want my verdict?"))
    }

    @Test fun promptHasFewShotForSmallModels() {
        val d = Digest.build(whatsappList())
        val small = ScreenTalk.prompt(ScreenTalk.Mode.SUMMARY, d, compact = true)
        assertTrue(small.contains("Good answer:"))
        assertTrue(small.contains("MY SCREEN NOW:"))
        assertTrue(small.length < 1400)
        assertFalse(ScreenTalk.prompt(ScreenTalk.Mode.SUMMARY, d, compact = false).contains("Good answer:"))
    }

    @Test fun phrases() {
        assertEquals(ScreenTalk.Mode.SUMMARY, ScreenTalk.parse("What's on my screen?"))
        assertEquals(ScreenTalk.Mode.SUMMARY, ScreenTalk.parse("screen la enna"))
        assertEquals(ScreenTalk.Mode.CHAT_SUMMARY, ScreenTalk.parse("summarise this chat"))
        assertEquals(ScreenTalk.Mode.REPLY_DRAFT, ScreenTalk.parse("Reply for me"))
        assertEquals(ScreenTalk.Mode.ROAST, ScreenTalk.parse("roast this screen 😂"))
        assertEquals(ScreenTalk.Mode.WORTH, ScreenTalk.parse("Is this worth watching?"))
        assertEquals(ScreenTalk.Mode.CHEAPER, ScreenTalk.parse("Find cheaper?"))
        assertEquals(ScreenTalk.Mode.HOW_LONG, ScreenTalk.parse("How long?"))
        assertEquals(null, ScreenTalk.parse("how long is the nile"))
        assertNotNull(ScreenTalk.parse("what do you see"))
    }
}
