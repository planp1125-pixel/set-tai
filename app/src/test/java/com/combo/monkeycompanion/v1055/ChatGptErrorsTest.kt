package com.combo.monkeycompanion.v1055

import com.combo.monkeycompanion.ai.BrainException
import com.combo.monkeycompanion.ai.ChatGptBrain
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** v10.55.8: ChatGPT errors always say what OpenAI said (never "had a problem ()"), and a refused model falls back to the next one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatGptErrorsTest {
    private var server: HttpServer? = null
    private val models = CopyOnWriteArrayList<String>()
    @After fun after() { server?.stop(0) }

    private class T : ChatGptBrain.Tokens {
        override fun accessToken() = "tok"
        override fun onUnauthorized() = false
        override val accountLabel = "me@example.com"
    }

    private fun sse(vararg e: JSONObject) = e.joinToString("") { "event: ${it.optString("type")}\ndata: $it\n\n" }

    /** [answer] gets the model asked for and returns the SSE body. */
    private fun serve(answer: (String) -> String): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server = s
        s.createContext("/v1/responses") { x ->
            val m = JSONObject(x.requestBody.bufferedReader().readText()).getString("model"); models.add(m)
            val b = answer(m).toByteArray(); x.responseHeaders.add("Content-Type", "text/event-stream"); x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        s.createContext("/v1/models") { x ->
            val b = """{"models":[{"slug":"gpt-old-mini","visibility":"list"},{"slug":"gpt-new","visibility":"list"}]}""".toByteArray()
            x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        s.start(); return "http://127.0.0.1:${s.address.port}/v1"
    }

    private fun nestedError(msg: String) = JSONObject().put("type", "error")
        .put("error", JSONObject().put("type", "invalid_request_error").put("code", "model_not_supported").put("message", msg))

    @Test fun nestedErrorEventsShowOpenAisWords() {
        val e = JSONObject().put("type", "error").put("error", JSONObject().put("code", "x_code").put("message", "The model is not supported."))
        assertEquals("ChatGPT: The model is not supported. (x_code)", ChatGptBrain.errorOf(e, e.toString()))
        val bare = JSONObject().put("type", "error")
        assertTrue(ChatGptBrain.errorOf(bare, bare.toString()).startsWith("ChatGPT had a problem: {"))
        assertFalse(ChatGptBrain.errorOf(bare, bare.toString()).contains("()"))
    }

    @Test fun aRefusedModelFallsBackToTheNextOne() {
        val api = serve { m ->
            if (m == "gpt-old-mini") sse(nestedError("This model is not supported with a ChatGPT account."))
            else sse(JSONObject().put("type", "response.output_text.delta").put("delta", "Hi!"), JSONObject().put("type", "response.completed"))
        }
        val out = ChatGptBrain(T(), "", api).chat("You are Meenu.", emptyList(), "hi", null) {}
        assertEquals("Hi!", out)
        assertEquals(listOf("gpt-old-mini", "gpt-new"), models.toList())
    }

    @Test fun whenEveryModelFailsTheRealReasonIsShown() {
        val api = serve { sse(nestedError("Your plan can't use this app yet.")) }
        val e = runCatching { ChatGptBrain(T(), "", api).chat("s", emptyList(), "hi", null) {} }.exceptionOrNull()
        assertTrue(e is BrainException)
        assertEquals("ChatGPT: Your plan can't use this app yet. (model_not_supported)", e!!.message)
    }
}
