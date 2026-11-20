package d3

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.settai.desktop.ChatGptLogin
import com.combo.settai.desktop.Config
import com.combo.settai.desktop.ui.HomeWindow
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

/** D3: desktop Sign in with ChatGPT (same OpenAI rules as the phone) and the app window's character pictures. */
class DesktopBrainTest {
    @Test fun signInAddressFollowsOpenAisRules() {
        ChatGptLogin.init(Config())
        val url = ChatGptLogin.authorizeUrl("http://127.0.0.1:1455/auth/callback", "st", "no", "ch")
        assertTrue(url, url.startsWith("https://auth.openai.com/api/accounts/authorize?"))
        val q = url.substringAfter('?').split('&').associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("dynamic_agent_client", q["client_id"] ?: "")   // first sign-in on a fresh machine
        assertTrue(q["ext_agent_host_id"]!!, ChatGptLogin.isValidHostId(q["ext_agent_host_id"]!!))
        assertEquals("Set-Tai", q["agent_name_hint"])
        assertEquals("code", q["response_type"])
        assertEquals("http://127.0.0.1:1455/auth/callback", q["redirect_uri"])
        assertEquals("openid profile email offline_access resource.invoke chatgpt.tokens.use.direct", q["scope"])
        assertEquals("https://api.openai.com/v1", q["resource"])
        assertEquals("S256", q["code_challenge_method"])
        assertFalse("no '+' for spaces", url.contains("+"))
        // the same id every time on this computer
        assertEquals(ChatGptLogin.hostId(), ChatGptLogin.hostId())
        assertFalse(ChatGptLogin.isValidHostId("settai-desktop-123"))
    }

    @Test fun jwtClaimsDecode() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"iss":"https://auth.openai.com","email":"a@b.c"}""".toByteArray())
        val c = ChatGptLogin.jwtClaims("x.$payload.y")!!
        assertEquals("a@b.c", c.optString("email"))
    }

    @Test fun portraitsForTheAppWindow() {
        for (c in CharacterRegistry.all) {
            val img = HomeWindow.portrait(c, 120)
            var n = 0
            for (y in 0 until 120 step 3) for (x in 0 until 120 step 3) if ((img.getRGB(x, y) ushr 24) > 40) n++
            assertTrue(c.id, n > 40)
        }
    }
}
