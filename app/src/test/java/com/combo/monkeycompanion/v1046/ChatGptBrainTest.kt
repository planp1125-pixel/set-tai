package com.combo.monkeycompanion.v1046

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.*
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.ui.BrainStageView
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** v10.46 "Sign in with ChatGPT": the ChatGPT brain (Responses API, streaming, tools, refresh, errors) and the brain choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatGptBrainTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var server: HttpServer? = null
    private val bodies = CopyOnWriteArrayList<JSONObject>()
    private val auths = CopyOnWriteArrayList<String>()

    @Before fun before() { context.getSharedPreferences("monkey_settings", Context.MODE_PRIVATE).edit().clear().commit(); ChatHub.init(context) }
    @After fun after() { server?.stop(0); ChatHub.settings.useChatGpt = false; ChatHub.chooseBrain(ChatHub.BrainChoice.CLOUD) }

    private class FakeTokens : ChatGptBrain.Tokens {
        var token = "tok-1"; var refreshes = 0; var allowRefresh = true
        override fun accessToken() = token
        override fun onUnauthorized(): Boolean { refreshes++; token = "tok-2"; return allowRefresh }
        override val accountLabel = "manik@example.com"
    }

    private fun sse(vararg events: JSONObject): String = events.joinToString("") { "event: ${it.optString("type")}\ndata: $it\n\n" }
    private fun delta(t: String) = JSONObject().put("type", "response.output_text.delta").put("delta", t)
    private fun done() = JSONObject().put("type", "response.completed").put("response", JSONObject())

    /** A fake OpenAI: [responses] answers each POST /v1/responses in turn (status to body). */
    private fun serve(responses: List<Pair<Int, String>>, models: String = """{"models":[{"slug":"gpt-big","display_name":"Big","visibility":"list"},{"slug":"gpt-mini","display_name":"Mini","visibility":"list"},{"slug":"hidden","visibility":"hide"}]}"""): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server = s
        var n = 0
        fun send(x: HttpExchange, status: Int, body: String, type: String) {
            val b = body.toByteArray(); x.responseHeaders.add("Content-Type", type); x.sendResponseHeaders(status, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        s.createContext("/v1/responses") { x ->
            auths.add(x.requestHeaders.getFirst("Authorization").orEmpty())
            bodies.add(JSONObject(x.requestBody.bufferedReader().readText()))
            val (st, body) = responses[minOf(n++, responses.size - 1)]
            send(x, st, body, if (st == 200) "text/event-stream" else "application/json")
        }
        s.createContext("/v1/models") { x -> auths.add(x.requestHeaders.getFirst("Authorization").orEmpty()); send(x, 200, models, "application/json") }
        s.start(); return "http://127.0.0.1:${s.address.port}/v1"
    }

    @Test fun streamsTheReplyWithThePersonaAndHistoryAndPicksASmallModel() {
        val api = serve(listOf(200 to sse(delta("Hey "), delta("there!"), done())))
        val brain = ChatGptBrain(FakeTokens(), "", api)
        val seen = ArrayList<String>()
        val out = brain.chat("You are Tutty.", listOf(ChatMsg(true, "hi"), ChatMsg(false, "yo")), "how are you?", null) { seen.add(it) }
        assertEquals("Hey there!", out)
        assertEquals(listOf("Hey", "Hey there!"), seen)
        val b = bodies.single()
        assertEquals("a small fast model from the account", "gpt-mini", b.getString("model"))
        assertEquals(false, b.getBoolean("store")); assertEquals(true, b.getBoolean("stream"))
        assertEquals("You are Tutty.", b.getString("instructions"))
        val input = b.getJSONArray("input")
        assertEquals(3, input.length())
        assertEquals("assistant", input.getJSONObject(1).getString("role")); assertEquals("how are you?", input.getJSONObject(2).getString("content"))
        assertTrue(auths.all { it == "Bearer tok-1" })
        assertEquals("ChatGPT (manik@example.com)", brain.label)
    }

    @Test fun runsATool_thenSendsTheResultBackAndFinishes() {
        val call = JSONObject().put("type", "response.output_item.done")
            .put("item", JSONObject().put("type", "function_call").put("call_id", "c1").put("name", "do_action").put("arguments", "{\"action\":\"dance\"}"))
        val api = serve(listOf(200 to sse(call, done()), 200 to sse(delta("Look at me go!"), done())))
        val ran = ArrayList<String>()
        val tools = object : ToolHost {
            override val specs = listOf(ToolSpec("do_action", "move the body", JSONObject().put("type", "object")))
            override fun call(name: String, argsJson: String): String { ran.add("$name $argsJson"); return "ok, danced" }
        }
        val out = ChatGptBrain(FakeTokens(), "gpt-mini", api).chat("sys", emptyList(), "dance!", tools) {}
        assertEquals("Look at me go!", out)
        assertEquals(listOf("do_action {\"action\":\"dance\"}"), ran)
        val tool = bodies[0].getJSONArray("tools").getJSONObject(0)
        assertEquals("function", tool.getString("type")); assertEquals("do_action", tool.getString("name"))
        val second = bodies[1].getJSONArray("input")
        val kinds = (0 until second.length()).map { second.getJSONObject(it).optString("type") }
        assertTrue(kinds.contains("function_call")); assertTrue(kinds.contains("function_call_output"))
        assertEquals("ok, danced", (0 until second.length()).map { second.getJSONObject(it) }.first { it.optString("type") == "function_call_output" }.getString("output"))
    }

    @Test fun anExpiredTokenIsRefreshedOnceAndTheRequestRepeated() {
        val api = serve(listOf(401 to """{"error":{"code":"token_expired"}}""", 200 to sse(delta("Back!"), done())))
        val t = FakeTokens()
        assertEquals("Back!", ChatGptBrain(t, "gpt-mini", api).chat("s", emptyList(), "hi", null) {})
        assertEquals(1, t.refreshes)
        assertEquals(listOf("Bearer tok-1", "Bearer tok-2"), auths.toList())
    }

    @Test fun planErrorsAreSaidInPlainWords() {
        val limit = JSONObject().put("type", "response.failed").put("response", JSONObject().put("error", JSONObject().put("code", "subscription_sharing_usage_limit_exceeded")))
        val api = serve(listOf(200 to sse(limit)))
        val e = runCatching { ChatGptBrain(FakeTokens(), "gpt-mini", api).chat("s", emptyList(), "hi", null) {} }.exceptionOrNull()
        assertTrue(e is BrainException); assertTrue(e!!.message!!.contains("ChatGPT limit"))
        server?.stop(0)
        val api2 = serve(listOf(403 to """{"error":{"code":"subscription_sharing_user_not_eligible","message":"no"}}"""))
        val e2 = runCatching { ChatGptBrain(FakeTokens(), "gpt-mini", api2).chat("s", emptyList(), "hi", null) {} }.exceptionOrNull()
        assertTrue(e2!!.message!!.contains("Plus or Pro"))
        val t = FakeTokens().apply { allowRefresh = false }
        server?.stop(0)
        val api3 = serve(listOf(401 to "{}"))
        val e3 = runCatching { ChatGptBrain(t, "gpt-mini", api3).chat("s", emptyList(), "hi", null) {} }.exceptionOrNull()
        assertEquals(ChatGptBrain.SIGN_IN_AGAIN, e3!!.message)
    }

    @Test fun modelChoiceAndFriendlyCodes() {
        assertEquals("gpt-6-mini", ChatGptBrain.chooseModel(listOf("gpt-6", "gpt-6-mini")))
        assertEquals("gpt-6", ChatGptBrain.chooseModel(listOf("gpt-6")))
        assertNull(ChatGptBrain.chooseModel(emptyList()))
        assertTrue(ChatGptBrain.friendly("subscription_sharing_usage_unavailable", "").contains("busy"))
    }

    @Test fun choosingChatGptWithoutSigningInAsksToSignIn_andAutomaticLinesStayLocal() {
        ChatGptAuth.init(context); ChatGptAuth.signOut()
        ChatHub.chooseBrain(ChatHub.BrainChoice.CHATGPT)
        assertEquals(ChatHub.BrainChoice.CHATGPT, ChatHub.brainChoice())
        assertTrue(ChatHub.onChatGpt()); assertFalse("not the free cloud", ChatHub.onCloud())
        assertFalse("automatic lines don't spend the user's ChatGPT plan", ChatHub.autoAiAllowed())
        val b = ChatHub.brain()
        assertTrue(b is CannedBrain)
        ChatHub.chooseBrain(ChatHub.BrainChoice.CLOUD)
        assertEquals(ChatHub.BrainChoice.CLOUD, ChatHub.brainChoice()); assertTrue(ChatHub.onCloud()); assertFalse(ChatGptAuth.signedIn)
        ChatHub.chooseBrain(ChatHub.BrainChoice.NONE); assertEquals(ChatHub.BrainChoice.NONE, ChatHub.brainChoice())
    }

    @Test fun idTokenClaimsAreRead() {
        fun b64(s: String) = android.util.Base64.encodeToString(s.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        val jwt = b64("{\"alg\":\"RS256\"}") + "." + b64("{\"iss\":\"https://auth.openai.com\",\"email\":\"a@b.c\",\"aud\":[\"oaiapp_1\"]}") + ".sig"
        val c = ChatGptAuth.jwtClaims(jwt)!!
        assertEquals("a@b.c", c.getString("email")); assertTrue(c.get("aud") is JSONArray)
        assertNull(ChatGptAuth.jwtClaims("nonsense"))
    }

    /** v10.46.1: OpenAI rejected "settai-android-…"; the host id must be urn:uuid:<UUIDv4>, and an old one is replaced. */
    @Test fun theHostIdIsAUrnUuidAndAnOldOneIsReplaced() {
        ChatGptAuth.init(context)
        val p = context.getSharedPreferences("chatgpt_auth", Context.MODE_PRIVATE)
        p.edit().putString("host_id", "settai-android-1234").commit()
        val id = ChatGptAuth.hostId()
        assertTrue(id, id.startsWith("urn:uuid:")); assertTrue(ChatGptAuth.isValidHostId(id))
        assertEquals("kept the next time", id, ChatGptAuth.hostId())
        assertFalse(ChatGptAuth.isValidHostId("settai-android-0f8fad5b-d9cb-469f-a165-70867728950e"))
        assertTrue(ChatGptAuth.isValidHostId("urn:uuid:0f8fad5b-d9cb-469f-a165-70867728950e"))
    }

    @Test fun theBrainScreenBackgroundDrawsEveryCharacter() {
        val c = Canvas(Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888))
        for (ch in CharacterRegistry.all) {
            val v = BrainStageView(context); v.who = ch; v.layout(0, 0, 1080, 900)
            v.draw(c)
        }
    }
}
