package com.combo.monkeycompanion.v1048

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.*
import com.combo.monkeycompanion.powers.Capabilities
import com.combo.monkeycompanion.powers.Invocation
import com.combo.monkeycompanion.powers.PhoneCommands
import com.combo.monkeycompanion.powers.Plan
import com.combo.monkeycompanion.powers.WebLinks
import com.combo.monkeycompanion.ui.ChatPanelView
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** v10.48: the "open this website" power and 📎 photos / documents for the ChatGPT brain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebAndAttachTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private var server: HttpServer? = null

    @Before fun before() { ChatHub.init(ctx); ChatHub.attach(null); ShadowLooper.idleMainLooper() }
    @After fun after() { server?.stop(0); ChatHub.testBrain = null; ChatHub.attach(null); ShadowLooper.idleMainLooper() }

    // ---------------- open this website ----------------

    @Test fun linksBecomeSafeHttpsLinks() {
        assertEquals("https://youtube.com", WebLinks.normalize("youtube.com"))
        assertEquals("https://www.thehindu.com/news", WebLinks.normalize("www.thehindu.com/news"))
        assertEquals("https://en.wikipedia.org/wiki/Chennai", WebLinks.normalize("https://en.wikipedia.org/wiki/Chennai"))
        assertEquals("http://example.com/a?b=1", WebLinks.normalize("http://example.com/a?b=1"))
        assertEquals("https://amazon.in", WebLinks.normalize("amazon"))
        assertEquals("https://wikipedia.org", WebLinks.known("the wikipedia website"))
        assertEquals("https://youtube.com", WebLinks.normalize("“youtube.com”."))
        for (bad in listOf("javascript:alert(1)", "file:///sdcard/a.txt", "intent://scan#Intent;end", "content://x/y", "hello there",
                "localhost", "", "https://", "https://user:pass@evil.com/", "data:text/html,hi", "ftp://x.com"))
            assertNull(bad, WebLinks.normalize(bad))
        assertEquals("youtube.com", WebLinks.host("https://www.youtube.com/watch?v=1"))
    }

    @Test fun openWebsitePhrasesButOpenAppStaysTheApp() {
        fun p(s: String) = PhoneCommands.parse(s)
        assertEquals(Invocation("open_website", mapOf("url" to "https://youtube.com")), p("open youtube.com"))
        assertEquals(Invocation("open_website", mapOf("url" to "https://en.wikipedia.org/wiki/Tamil")), p("go to https://en.wikipedia.org/wiki/Tamil"))
        assertEquals(Invocation("open_website", mapOf("url" to "https://wikipedia.org")), p("open the wikipedia website"))
        assertEquals(Invocation("open_website", mapOf("url" to "https://amazon.in")), p("can you open amazon site please"))
        assertEquals(Invocation("open_website", mapOf("url" to "https://www.flipkart.com")), p("visit www.flipkart.com"))
        assertEquals(Invocation("open_app", mapOf("name" to "YouTube")), p("open YouTube"))
        for (s in listOf("open the door", "open your heart", "go to sleep", "open settings", "visit grandma tomorrow"))
            assertNotEquals(s, "open_website", p(s)?.id)
    }

    @Test fun openWebsiteCapabilityAsksFirstAndRefusesNonWebLinks() {
        val cap = Capabilities.byId("open_website")!!
        val plan = cap.plan(ctx, ChatHub.settings, mapOf("url" to "youtube.com"))
        assertTrue(plan is Plan.Act)
        assertEquals("Open youtube.com? 🌐", (plan as Plan.Act).question)
        assertEquals(BodyAction.FLY, plan.body)
        assertTrue(plan.exec().isNotBlank())   // opened, or says why not (no browser in the test phone)
        assertTrue(cap.plan(ctx, ChatHub.settings, mapOf("url" to "javascript:alert(1)")) is Plan.Fail)
        // tag for brains without tools
        assertEquals(mapOf("url" to "x.com"), cap.fromTag!!("x.com"))
    }

    @Test fun missingAppFallsBackToItsWebsite() {
        val plan = Capabilities.byId("open_app")!!.plan(ctx, ChatHub.settings, mapOf("name" to "wikipedia"))
        assertTrue(plan is Plan.Act)
        assertTrue((plan as Plan.Act).question, plan.question.contains("wikipedia.org"))
        assertTrue(Capabilities.byId("open_app")!!.plan(ctx, ChatHub.settings, mapOf("name" to "zzqq app")) is Plan.Fail)
    }

    // ---------------- 📎 photos and documents ----------------

    @Test fun fileKinds() {
        assertEquals(Attachment.Kind.IMAGE, Attachment.kindFor("image/png", "a.png"))
        assertEquals(Attachment.Kind.PDF, Attachment.kindFor("application/pdf", "bill.pdf"))
        assertEquals(Attachment.Kind.PDF, Attachment.kindFor("application/octet-stream", "Bill.PDF"))
        assertEquals(Attachment.Kind.TEXT, Attachment.kindFor("text/plain", "notes.txt"))
        assertEquals(Attachment.Kind.TEXT, Attachment.kindFor("application/octet-stream", "data.csv"))
        assertNull(Attachment.kindFor("application/vnd.android.package-archive", "x.apk"))
        assertNull(Attachment.kindFor("video/mp4", "clip.mp4"))
    }

    private fun serve(bodies: MutableList<JSONObject>): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server = s
        s.createContext("/v1/responses") { x ->
            bodies.add(JSONObject(x.requestBody.bufferedReader().readText()))
            val ev = JSONObject().put("type", "response.output_text.delta").put("delta", "Nice photo!")
            val b = ("data: $ev\n\ndata: " + JSONObject().put("type", "response.completed") + "\n\n").toByteArray()
            x.responseHeaders.add("Content-Type", "text/event-stream"); x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        s.start(); return "http://127.0.0.1:${s.address.port}/v1"
    }

    private class Tok : ChatGptBrain.Tokens {
        override fun accessToken() = "t"; override fun onUnauthorized() = false; override val accountLabel = "me"
    }

    @Test fun chatGptGetsPhotosPdfsAndTextFiles() {
        val bodies = CopyOnWriteArrayList<JSONObject>()
        val brain = ChatGptBrain(Tok(), "gpt-mini", serve(bodies))
        assertTrue(brain.supportsAttachments)
        val img = Attachment(Attachment.Kind.IMAGE, "cat.jpg", "image/jpeg", "data:image/jpeg;base64,AAAA")
        val pdf = Attachment(Attachment.Kind.PDF, "bill.pdf", "application/pdf", "data:application/pdf;base64,BBBB")
        val txt = Attachment(Attachment.Kind.TEXT, "notes.txt", "text/plain", text = "buy milk")
        assertEquals("Nice photo!", brain.chat("sys", emptyList(), "look", null, listOf(img, pdf, txt)) {})
        val content = bodies.single().getJSONArray("input").getJSONObject(0).getJSONArray("content")
        assertEquals("input_text", content.getJSONObject(0).getString("type"))
        assertTrue(content.getJSONObject(0).getString("text").contains("[File: notes.txt]\nbuy milk"))
        assertEquals("input_image", content.getJSONObject(1).getString("type"))
        assertEquals("data:image/jpeg;base64,AAAA", content.getJSONObject(1).getString("image_url"))
        assertEquals("input_file", content.getJSONObject(2).getString("type"))
        assertEquals("bill.pdf", content.getJSONObject(2).getString("filename"))
        assertEquals("data:application/pdf;base64,BBBB", content.getJSONObject(2).getString("file_data"))
        // no attachment: plain text content, as before
        bodies.clear(); brain.chat("sys", emptyList(), "hi", null) {}
        assertEquals("hi", bodies.single().getJSONArray("input").getJSONObject(0).getString("content"))
    }

    /** A brain that can see, and records what it got. */
    private class SeeingBrain : Brain {
        val got = ArrayList<List<Attachment>>(); val users = ArrayList<String>()
        override val label = "see"; override val supportsTools = false; override val supportsAttachments = true
        override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit) = chat(system, history, user, tools, emptyList(), onText)
        override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, attachments: List<Attachment>, onText: (String) -> Unit): String {
            got.add(attachments); users.add(user); onText("Cute cat!"); return "Cute cat!"
        }
    }

    private fun waitIdle() {
        val end = System.currentTimeMillis() + 10_000
        while (ChatHub.busy && System.currentTimeMillis() < end) { Thread.sleep(10); ShadowLooper.idleMainLooper() }
        ShadowLooper.idleMainLooper()
    }

    @Test fun anAttachedPhotoGoesWithTheNextMessageOnlyAndCanBeSentWithoutWords() {
        val b = SeeingBrain(); ChatHub.testBrain = b
        val img = Attachment(Attachment.Kind.IMAGE, "cat.jpg", "image/jpeg", "data:image/jpeg;base64,AAAA")
        ChatHub.attach(img); ShadowLooper.idleMainLooper()
        assertEquals(img, ChatHub.attachment)
        ChatHub.send("")   // just the photo
        waitIdle()
        assertEquals(listOf(img), b.got.single())
        assertEquals("What do you think of this photo?", b.users.single())
        assertNull(ChatHub.attachment)
        val log = ChatHub.memory.all().filter { it.fromUser }.last().text
        assertTrue(log, log.startsWith("🖼️ cat.jpg"))
        assertFalse("the photo data never goes in the saved chat", ChatHub.memory.all().any { it.text.contains("base64") })
        // next message: no photo, and phone commands work again
        ChatHub.send("tell me about the mischief from earlier"); waitIdle()
        assertEquals(emptyList<Attachment>(), b.got.last())
    }

    @Test fun otherBrainsSayItNeedsChatGpt() {
        ChatHub.testBrain = object : Brain {
            override val label = "plain"; override val supportsTools = false
            override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit) = "should not be asked"
        }
        val replies = ArrayList<String>()
        val l = object : ChatHub.Listener { override fun onReply(text: String, done: Boolean) { if (done) replies.add(text) } }
        ChatHub.addListener(l)
        try {
            ChatHub.attach(Attachment(Attachment.Kind.PDF, "bill.pdf", "application/pdf", "data:application/pdf;base64,BBBB")); ShadowLooper.idleMainLooper()
            ChatHub.send("what is this bill?"); waitIdle()
            assertTrue(replies.joinToString(), replies.any { it.contains("ChatGPT brain") })
            assertNull(ChatHub.attachment)
        } finally { ChatHub.removeListener(l) }
    }

    @Test fun chatPanelShowsTheClipOnlyForSeeingBrainsAndTheWaitingFile() {
        ChatHub.testBrain = SeeingBrain()
        val panel = ChatPanelView(ctx, floating = false)
        val root = android.widget.FrameLayout(ctx); root.addView(panel)
        org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get().setContentView(root)
        ShadowLooper.idleMainLooper()
        assertTrue(panel.debugClipShown)
        ChatHub.attach(Attachment(Attachment.Kind.IMAGE, "dog.jpg", "image/jpeg", "data:image/jpeg;base64,AAAA")); ShadowLooper.idleMainLooper()
        assertTrue(panel.debugAttachRow, panel.debugAttachRow.contains("dog.jpg"))
        ChatHub.attach(null); ShadowLooper.idleMainLooper()
        assertEquals("", panel.debugAttachRow)
        // a brain that can't see: no 📎
        ChatHub.testBrain = object : Brain {
            override val label = "plain"; override val supportsTools = false
            override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit) = ""
        }
        val panel2 = ChatPanelView(ctx, floating = false)
        org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get().setContentView(panel2)
        ShadowLooper.idleMainLooper()
        assertFalse(panel2.debugClipShown)
    }
}
