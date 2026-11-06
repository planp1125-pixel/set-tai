package com.combo.monkeycompanion.v1028

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.mind.MindLines
import com.combo.monkeycompanion.speech.*
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudBrainTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var server: HttpServer? = null
    @Before fun before() { context.getSharedPreferences("monkey_settings", Context.MODE_PRIVATE).edit().clear().commit(); CharacterRegistry.select("devil") }
    @After fun after() { server?.stop(0); CharacterRegistry.select("monkey") }
    private fun serve(status: Int, response: String, seen: (String) -> Unit = {}): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server = s
        s.createContext("/v1/chat") { exchange ->
            seen(exchange.requestBody.bufferedReader().readText())
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        s.start(); return "http://127.0.0.1:${s.address.port}/v1/chat"
    }
    @Test fun optInAndAnonymousIdPersistAndTouchDefaultsStayUnchanged() {
        val a = AppSettings(context)
        assertFalse(a.aiBrainEnabled); assertEquals(BrainType.NONE, a.brainType)
        assertFalse(a.useOwnApiKey); assertFalse(a.flickThrow)
        val id = a.cloudUserId(); assertEquals(id, UUID.fromString(id).toString()); assertEquals(id, AppSettings(context).cloudUserId())
        a.aiBrainEnabled = true; assertEquals(BrainType.ONLINE, a.brainType)
        a.aiBrainEnabled = false; assertEquals(BrainType.NONE, a.brainType)
        assertEquals(id, a.cloudUserId())
    }
    @Test fun defaultIsGatewayOnlyAfterGiveBrainAndOwnKeyRemainsExplicit() {
        ChatHub.init(context); ChatHub.setBrainEnabled(false)
        assertFalse(ChatHub.brain().isReal)
        ChatHub.setBrainEnabled(true); assertTrue(ChatHub.brain() is GatewayBrain)
        ChatHub.settings.useOwnApiKey = true
        assertFalse(ChatHub.brain() is GatewayBrain)
        ChatHub.settings.useOwnApiKey = false; ChatHub.setBrainEnabled(false)
    }
    @Test fun gatewaySendsAnonymousIdentityBodySenseAndHistory() {
        var captured = ""
        val url = serve(200, """{"text":"Tiny horns, big hello!","leftToday":39}""") { captured = it }
        val b = GatewayBrain("anonymous-test", { true }, { "Body Sense: awake" }, url)
        assertEquals("Tiny horns, big hello!", b.chat("You are Kutti Satan", listOf(ChatMsg(true, "hi")), "hello", null) {})
        val json = JSONObject(captured)
        assertEquals("anonymous-test", json.getString("userId")); assertEquals("devil", json.getString("characterId"))
        assertEquals("Body Sense: awake", json.getString("bodySense")); assertEquals(1, json.getJSONArray("history").length())
        assertEquals(39, b.leftToday)
    }
    @Test fun aiOffNeverContactsGateway() {
        val hits = AtomicInteger()
        val url = serve(200, """{"text":"unexpected","leftToday":39}""") { hits.incrementAndGet() }
        GatewayBrain("id", { false }, endpoint = url).chat("", emptyList(), "hello", null) {}
        assertEquals(0, hits.get())
    }
    @Test fun quotaAndUpstreamFailuresStayFriendlyAndCharacterSafe() {
        val url = serve(429, """{"error":"daily_limit","leftToday":0}""")
        val b = GatewayBrain("id", { true }, endpoint = url)
        val line = b.chat("", emptyList(), "hi", null) {}
        assertTrue(line.contains("Local jokes")); assertEquals(0, b.leftToday)
        server?.stop(0)
        val down = serve(502, """{"error":"gemini_unavailable","upstreamStatus":400}""")
        val fallback = GatewayBrain("id", { true }, endpoint = down).chat("", emptyList(), "hi", null) {}
        assertTrue(fallback.contains("cloud connection is resting")); assertFalse(fallback.contains("banana", true))
    }
    @Test fun timeoutFallsBackWithoutRawError() {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server = s
        s.createContext("/v1/chat") { e -> Thread.sleep(300); e.close() }; s.start()
        val client = OkHttpClient.Builder().callTimeout(50, TimeUnit.MILLISECONDS).build()
        val line = GatewayBrain("id", { true }, endpoint = "http://127.0.0.1:${s.address.port}/v1/chat", client = client).chat("", emptyList(), "hi", null) {}
        assertTrue(line.contains("cloud connection is resting")); assertFalse(line.contains("Exception"))
    }
    @Test fun payloadFitsUtf8BodyLimit() {
        val b = GatewayBrain("id", { true }, { "😈".repeat(600) })
        val payload = b.payload("தமிழ்".repeat(2000), List(20) { ChatMsg(true, "😇".repeat(500)) }, "வணக்கம்".repeat(500))
        assertTrue(payload.toByteArray(Charsets.UTF_8).size <= 8192)
        assertTrue(JSONObject(payload).getString("message").isNotBlank())
    }
    @Test fun everyBuiltInHasOwnJokesAndNoMonkeyFallbackLeaks() {
        assertEquals(CharacterRegistry.all.map { it.id }.toSet(), CharacterHumor.jokesById.keys)
        val starts = mutableSetOf<String>()
        val monkey = Regex("(?i)banana|monkey|smart-ape|ooh ooh|🍌|🐒")
        val pools = listOf(Lines.IDLE, Lines.BORED, Lines.RANDOM, Lines.FALL, Lines.DANCE, Lines.TICKLE, Lines.PAT, Lines.PICKUP, Lines.REST, Lines.POKED_AWAKE, Lines.SLEEPY)
        for (who in CharacterRegistry.all) {
            CharacterRegistry.select(who.id)
            assertTrue(starts.add(CharacterHumor.jokes(who).first()))
            if (who.id == "monkey") continue
            repeat(30) { for (pool in pools) assertFalse("${who.id}: $pool", monkey.containsMatchIn(Lines.pick(pool))) }
            assertFalse(monkey.containsMatchIn(MindLines.leave(who.id)))
            assertFalse(monkey.containsMatchIn(MindLines.excuse(who.id)))
            val prompt = Persona.systemPrompt(false, who = who)
            assertTrue(prompt.contains(who.name)); assertTrue(prompt.contains("Do not use banana or monkey jokes"))
            assertFalse(monkey.containsMatchIn(LocalChat.reply("tell me a joke")!!))
        }
    }
    @Test fun studioVoiceUsesSavedDescriptionNotMonkey() {
        val c = CustomCharacter(CustomCompanionSpec(id = "custom_regression", name = "Mira", analysisSummary = "A cheerful roller-skating friend"))
        val prompt = Persona.systemPrompt(false, who = c)
        assertTrue(prompt.contains("Mira")); assertTrue(prompt.contains("cheerful roller-skating friend"))
        assertTrue(CharacterHumor.jokes(c).any { it.contains("Mira") })
        assertFalse(CharacterHumor.jokes(c).any { it.contains("banana", true) || it.contains("monkey", true) })
    }
    @Test fun localRequestsDoNotNeedACloudBrain() {
        assertNotNull(LocalChat.reply("tell me a joke")); assertNotNull(LocalChat.reply("another joke"))
        assertTrue(LocalChat.reply("dance for me")!!.contains("[dance]"))
        assertNull(LocalChat.reply("Why is the sky blue?"))
    }
}
