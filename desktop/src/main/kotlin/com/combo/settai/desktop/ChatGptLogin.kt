package com.combo.settai.desktop

import com.combo.monkeycompanion.ai.BrainException
import com.combo.monkeycompanion.ai.ChatGptBrain
import com.combo.monkeycompanion.ai.Http
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * D3: "Sign in with ChatGPT" on the desktop (same OpenAI flow as the phone's ChatGptAuth: authorization code + PKCE,
 * client_id=dynamic_agent_client on the first sign-in, loopback callback on 127.0.0.1). It opens the normal browser.
 * Tokens stay on this computer only, in a file only this user can read (`chatgpt.json` in the settings folder);
 * never logged, never sent anywhere except OpenAI.
 */
object ChatGptLogin : ChatGptBrain.Tokens {
    private const val AUTHORIZE = "https://auth.openai.com/api/accounts/authorize"
    private const val TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
    private const val ISSUER = "https://auth.openai.com"
    private const val RESOURCE = "https://api.openai.com/v1"
    private const val SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    private const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    private const val PATH = "/auth/callback"
    private const val APP_NAME = "Set-Tai"
    private const val WAIT_MS = 5 * 60_000

    private var cfg: Config? = null
    @Volatile private var cache: JSONObject? = null
    @Volatile private var server: ServerSocket? = null
    /** Told (on any thread) whenever the sign-in state changes. */
    @Volatile var onChange: (() -> Unit)? = null

    fun init(c: Config) { if (cfg == null) cfg = c }
    private val file: File? get() = cfg?.let { File(it.dir, "chatgpt.json") }

    private fun record(): JSONObject? {
        cache?.let { return it }
        val f = file ?: return null
        if (!f.exists()) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()?.also { cache = it }
    }
    private fun save(r: JSONObject?) {
        cache = r
        val f = file ?: return
        if (r == null) { f.delete(); return }
        f.writeText(r.toString())
        runCatching { Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------")) }
        runCatching { f.setReadable(false, false); f.setReadable(true, true); f.setWritable(false, false); f.setWritable(true, true) }
    }

    val signedIn: Boolean get() = record()?.let { it.optString("refresh_token").isNotBlank() && it.optString("scope").contains(PLAN_SCOPE) } == true
    val email: String get() = record()?.optString("email").orEmpty()
    override val accountLabel: String get() = email.ifBlank { "your plan" }
    val signature: String get() = if (signedIn) record()?.optString("subject").orEmpty() else ""

    /** This computer's id for OpenAI ("distinct per host"): urn:uuid:<UUIDv4>, made once and kept. */
    fun hostId(): String {
        val c = cfg ?: return "urn:uuid:" + java.util.UUID.randomUUID()
        c.get("chatgpt.host_id").takeIf { isValidHostId(it) }?.let { return it }
        val id = "urn:uuid:" + java.util.UUID.randomUUID().toString()
        c.set("chatgpt.host_id", id); c.save()
        return id
    }
    fun isValidHostId(id: String): Boolean =
        Regex("urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(id)

    /** The sign-in page address (exposed for tests). */
    fun authorizeUrl(redirect: String, state: String, nonce: String, challenge: String): String {
        val old = record()
        val clientId = cfg?.get("chatgpt.client_id").orEmpty().ifBlank { null }
        val q = linkedMapOf(
            "client_id" to (clientId ?: "dynamic_agent_client"),
            "ext_agent_host_id" to hostId(),
        )
        if (clientId == null) q["agent_name_hint"] = APP_NAME
        old?.optString("id_token")?.takeIf { it.isNotBlank() }?.let { q["id_token_hint"] = it }
        old?.optString("email")?.takeIf { it.isNotBlank() }?.let { q["login_hint"] = it }
        q["response_type"] = "code"; q["redirect_uri"] = redirect; q["scope"] = SCOPE; q["resource"] = RESOURCE
        q["state"] = state; q["nonce"] = nonce; q["code_challenge_method"] = "S256"; q["code_challenge"] = challenge
        return AUTHORIZE + "?" + q.entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
    }

    /** Opens the browser at OpenAI's sign-in page; [done] runs on a background thread: null = signed in, else a message. */
    fun signIn(done: (String?) -> Unit) {
        cancelSignIn()
        val ss = runCatching { ServerSocket(1455, 1, InetAddress.getByName("127.0.0.1")) }.getOrNull()
            ?: runCatching { ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) }.getOrElse { return done("Couldn't start the sign-in (${it.message}).") }
        server = ss
        val redirect = "http://127.0.0.1:${ss.localPort}$PATH"
        val state = random(24); val nonce = random(24); val verifier = random(48)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val clientId = cfg?.get("chatgpt.client_id").orEmpty().ifBlank { null }
        val url = authorizeUrl(redirect, state, nonce, challenge)
        // listen first, then open the browser (OpenAI's rule)
        val t = Thread({
            val err = try { waitForCallback(ss, redirect, state, nonce, verifier, clientId) } catch (e: Exception) { "Sign-in failed: ${e.message}" }
            runCatching { ss.close() }
            if (server === ss) server = null
            done(err)
            runCatching { onChange?.invoke() }
        }, "chatgpt-signin")
        t.isDaemon = true; t.start()
        runCatching { DesktopChat.browse(url) }.onFailure { cancelSignIn(); done("No browser found to sign in.") }
    }

    fun cancelSignIn() { runCatching { server?.close() }; server = null }
    val signingIn: Boolean get() = server != null

    private fun query(target: String): Map<String, String> =
        target.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private fun waitForCallback(ss: ServerSocket, redirect: String, state: String, nonce: String, verifier: String, savedClient: String?): String? {
        ss.soTimeout = WAIT_MS
        while (true) {
            val sock = try { ss.accept() } catch (_: SocketTimeoutException) { return "Sign-in took too long. Try again." } catch (_: Exception) { return "Sign-in was cancelled." }
            sock.use { s ->
                s.soTimeout = 10_000
                val first = BufferedReader(InputStreamReader(s.getInputStream())).readLine().orEmpty()
                val target = first.split(' ').getOrNull(1).orEmpty()
                if (!target.startsWith(PATH)) { reply(s, 404, "Not here."); return@use }
                val q = query(target)
                if (q["state"] != state) { reply(s, 400, "This sign-in link is old. Go back to Set-Tai and try again."); return "Sign-in didn't match. Try again." }
                q["error"]?.let { e ->
                    reply(s, 200, if (e == "access_denied") "No problem, nothing was connected. You can go back to Set-Tai." else "Sign-in stopped ($e). Go back to Set-Tai.")
                    return if (e == "access_denied") "You said no on the ChatGPT page, so nothing was connected." else "ChatGPT sign-in stopped: $e"
                }
                val code = q["code"] ?: run { reply(s, 400, "Something was missing. Go back to Set-Tai and try again."); return "Sign-in came back without a code. Try again." }
                val issued = q["client_id"]
                val clientId = when {
                    savedClient == null -> issued ?: run { reply(s, 400, "Registration didn't finish."); return "ChatGPT didn't finish registering Set-Tai. Try again." }
                    issued != null && issued != savedClient -> { reply(s, 400, "Wrong app registration."); return "ChatGPT sent a different app registration. Try again." }
                    else -> savedClient
                }
                val err = exchange(code, verifier, redirect, clientId, nonce)
                reply(s, 200, if (err == null) "✅ Connected! Your character can think with your ChatGPT plan now. Go back to Set-Tai." else "Something went wrong: $err")
                return err
            }
        }
        @Suppress("UNREACHABLE_CODE") return "Sign-in stopped."
    }

    private fun exchange(code: String, verifier: String, redirect: String, clientId: String, nonce: String): String? {
        val body = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", clientId).add("code", code)
            .add("code_verifier", verifier).add("redirect_uri", redirect).add("resource", RESOURCE).build()
        Http.client.newCall(Request.Builder().url(TOKEN).post(body).build()).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            val o = runCatching { JSONObject(txt) }.getOrNull() ?: return "OpenAI sent an unreadable answer (HTTP ${r.code})."
            if (!r.isSuccessful) return ChatGptBrain.friendly(o.optString("error"), o.optString("error_description"))
            val idTok = o.optString("id_token")
            // OIDC Core 3.1.3.7: straight from the token endpoint over TLS; the claims are still checked.
            val claims = jwtClaims(idTok) ?: return "OpenAI's sign-in answer was missing the account details."
            if (claims.optString("iss") != ISSUER) return "That sign-in did not come from OpenAI."
            if (!audienceHas(claims, clientId)) return "That sign-in was for another app."
            if (claims.optString("nonce") != nonce) return "Sign-in didn't match. Try again."
            if (claims.optLong("exp") * 1000L < System.currentTimeMillis() - 60_000L) return "That sign-in had already expired. Try again."
            val scope = o.optString("scope")
            if (scope.isNotBlank() && !scope.contains(PLAN_SCOPE)) return "ChatGPT didn't allow using your plan in Set-Tai (it needs Plus or Pro)."
            val rec = JSONObject()
                .put("issuer", ISSUER).put("subject", claims.optString("sub")).put("email", claims.optString("email"))
                .put("client_id", clientId).put("ext_agent_host_id", hostId())
                .put("id_token", idTok).put("access_token", o.optString("access_token")).put("refresh_token", o.optString("refresh_token"))
                .put("token_type", o.optString("token_type", "Bearer")).put("scope", scope.ifBlank { PLAN_SCOPE })
                .put("expires_at", System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L).put("saved_at", System.currentTimeMillis())
            cfg?.let { it.set("chatgpt.client_id", clientId); it.save() }
            save(rec)
            return null
        }
    }

    @Synchronized override fun accessToken(): String {
        val r = record() ?: throw BrainException(SIGN_IN_AGAIN)
        if (r.optLong("expires_at") - 90_000L > System.currentTimeMillis()) return r.optString("access_token")
        if (!refresh()) throw BrainException(SIGN_IN_AGAIN)
        return record()!!.optString("access_token")
    }

    @Synchronized override fun onUnauthorized(): Boolean = refresh()

    @Synchronized private fun refresh(): Boolean {
        val r = record() ?: return false
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", r.optString("client_id"))
            .add("refresh_token", r.optString("refresh_token")).add("resource", RESOURCE).build()
        return try {
            Http.client.newCall(Request.Builder().url(TOKEN).post(body).build()).execute().use { resp ->
                val o = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull() ?: JSONObject()
                if (!resp.isSuccessful) {
                    if (o.optString("error") in DEAD) { save(null); runCatching { onChange?.invoke() }; return false }
                    throw BrainException("Couldn't reach ChatGPT to refresh (HTTP ${resp.code}). Try again.")
                }
                r.put("access_token", o.optString("access_token"))
                o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { r.put("refresh_token", it) }
                o.optString("id_token").takeIf { it.isNotBlank() }?.let { r.put("id_token", it) }
                o.optString("scope").takeIf { it.isNotBlank() }?.let { r.put("scope", it) }
                r.put("expires_at", System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L)
                save(r)
                true
            }
        } catch (e: BrainException) { throw e } catch (e: Exception) { throw BrainException("Couldn't reach ChatGPT (${e.message}). Check the internet.") }
    }
    private val DEAD = setOf("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")

    const val SIGN_IN_AGAIN = "Please sign in to ChatGPT again (right-click me › Set-Tai… › Sign in with ChatGPT)."

    fun signOut() { save(null); runCatching { onChange?.invoke() } }

    private fun reply(s: java.net.Socket, status: Int, msg: String) {
        val html = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>" +
            "<title>Set-Tai</title><style>body{font-family:sans-serif;background:#F3F1FF;color:#221E40;display:flex;min-height:90vh;align-items:center;justify-content:center;text-align:center;padding:24px}" +
            "div{background:#fff;border-radius:24px;padding:28px;max-width:420px;box-shadow:0 6px 24px #0002}</style></head><body><div><h2>Set-Tai</h2><p>" +
            msg.replace("<", "&lt;") + "</p><p>You can close this tab.</p></div></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
        runCatching { s.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(bytes); flush() } }
    }

    private val rng = SecureRandom()
    private fun random(n: Int): String { val b = ByteArray(n); rng.nextBytes(b); return b64url(b) }
    private fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun jwtClaims(jwt: String): JSONObject? = runCatching {
        JSONObject(String(Base64.getUrlDecoder().decode(jwt.split('.')[1].trimEnd('=')), Charsets.UTF_8))
    }.getOrNull()

    private fun audienceHas(c: JSONObject, clientId: String): Boolean = when (val a = c.opt("aud")) {
        is String -> a == clientId
        is org.json.JSONArray -> (0 until a.length()).any { a.optString(it) == clientId }
        else -> false
    }
}
