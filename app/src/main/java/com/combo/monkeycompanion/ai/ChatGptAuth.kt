package com.combo.monkeycompanion.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * v10.46 "Sign in with ChatGPT" (OpenAI's plan-usage flow for open-source / local apps):
 * OAuth 2 authorization code + PKCE, a public client registered on first sign-in (client_id=dynamic_agent_client),
 * and an HTTP loopback callback on 127.0.0.1 that this app listens on while the browser is open.
 * Tokens are kept only on this phone, encrypted with the Android Keystore ([SecureStore]); never logged, never sent
 * anywhere except OpenAI. Access tokens last an hour; the rotating refresh token lasts 30 days.
 * Docs: https://developers.openai.com/siwc/token-sharing-open-source
 */
object ChatGptAuth : ChatGptBrain.Tokens {
    private const val AUTHORIZE = "https://auth.openai.com/api/accounts/authorize"
    private const val TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
    private const val ISSUER = "https://auth.openai.com"
    private const val RESOURCE = "https://api.openai.com/v1"
    private const val SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    private const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    private const val PATH = "/auth/callback"
    private const val PREFS = "chatgpt_auth"
    private const val APP_NAME = "Set-Tai"
    /** How long the app waits for the browser to come back. */
    private const val WAIT_MS = 5 * 60_000

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Volatile private var cache: JSONObject? = null
    @Volatile private var listener: Thread? = null
    @Volatile private var server: ServerSocket? = null

    fun init(ctx: Context) { if (!::app.isInitialized) app = ctx.applicationContext }

    // ---------------- state ----------------
    private fun record(): JSONObject? {
        cache?.let { return it }
        val blob = prefs.getString("record", null) ?: return null
        return SecureStore.decrypt(blob)?.let { runCatching { JSONObject(it) }.getOrNull() }?.also { cache = it }
    }
    private fun save(r: JSONObject?) {
        cache = r
        if (r == null) prefs.edit().remove("record").apply() else prefs.edit().putString("record", SecureStore.encrypt(r.toString())).apply()
    }

    /** Signed in, and the plan-usage permission was granted. */
    val signedIn: Boolean get() = ::app.isInitialized && record()?.let { it.optString("refresh_token").isNotBlank() && it.optString("scope").contains(PLAN_SCOPE) } == true
    val email: String get() = if (::app.isInitialized) record()?.optString("email").orEmpty() else ""
    override val accountLabel: String get() = email.ifBlank { "your plan" }
    /** Changes whenever the account changes (part of the brain signature). */
    val signature: String get() = if (signedIn) record()?.optString("subject").orEmpty() else ""

    /** This phone's id for OpenAI's registration ("distinct per host"), made once. */
    private fun hostId(): String = prefs.getString("host_id", null) ?: ("settai-android-" + java.util.UUID.randomUUID().toString()).also { prefs.edit().putString("host_id", it).apply() }

    // ---------------- sign in ----------------
    /**
     * Opens the browser at OpenAI's sign-in page and waits (on a background thread) for it to come back to
     * 127.0.0.1. [done] runs on that thread: null = signed in, otherwise a plain message.
     */
    fun signIn(ctx: Context, done: (String?) -> Unit) {
        init(ctx)
        cancelSignIn()
        val ss = runCatching { ServerSocket(1455, 1, InetAddress.getByName("127.0.0.1")) }.getOrNull()
            ?: runCatching { ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) }.getOrElse { return done("Couldn't start the sign-in on this phone (${it.message}).") }
        server = ss
        val redirect = "http://127.0.0.1:${ss.localPort}$PATH"
        val state = random(24); val nonce = random(24); val verifier = random(48)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val old = record()
        val clientId = prefs.getString("client_id", null)
        val u = Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", clientId ?: "dynamic_agent_client")
            .appendQueryParameter("ext_agent_host_id", hostId())
            .apply { if (clientId == null) appendQueryParameter("agent_name_hint", APP_NAME) }
            .apply { old?.optString("id_token")?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("id_token_hint", it) } }
            .apply { old?.optString("email")?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("login_hint", it) } }
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirect)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("resource", RESOURCE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        // listen first, then open the browser (OpenAI's rule)
        val t = Thread({
            val err = try { waitForCallback(ss, redirect, state, nonce, verifier, clientId) } catch (e: Exception) { "Sign-in failed: ${e.message}" }
            runCatching { ss.close() }
            if (server === ss) server = null
            done(err)
        }, "chatgpt-signin")
        listener = t; t.isDaemon = true; t.start()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { cancelSignIn(); done("No browser found to sign in.") }
    }

    fun cancelSignIn() { runCatching { server?.close() }; server = null }

    private fun waitForCallback(ss: ServerSocket, redirect: String, state: String, nonce: String, verifier: String, savedClient: String?): String? {
        ss.soTimeout = WAIT_MS
        while (true) {
            val sock = try { ss.accept() } catch (_: SocketTimeoutException) { return "Sign-in took too long. Try again." } catch (_: Exception) { return "Sign-in was cancelled." }
            sock.use { s ->
                s.soTimeout = 10_000
                val first = BufferedReader(InputStreamReader(s.getInputStream())).readLine().orEmpty()   // "GET /auth/callback?… HTTP/1.1"
                val target = first.split(' ').getOrNull(1).orEmpty()
                if (!target.startsWith(PATH)) { reply(s, 404, "Not here."); return@use }
                val q = Uri.parse("http://127.0.0.1$target")
                if (q.getQueryParameter("state") != state) { reply(s, 400, "This sign-in link is old. Go back to Set-Tai and try again."); return "Sign-in didn't match. Try again." }
                q.getQueryParameter("error")?.let { e ->
                    reply(s, 200, if (e == "access_denied") "No problem, nothing was connected. You can go back to Set-Tai." else "Sign-in stopped ($e). Go back to Set-Tai.")
                    return if (e == "access_denied") "You said no on the ChatGPT page, so nothing was connected." else "ChatGPT sign-in stopped: $e"
                }
                val code = q.getQueryParameter("code") ?: run { reply(s, 400, "Something was missing. Go back to Set-Tai and try again."); return "Sign-in came back without a code. Try again." }
                val issued = q.getQueryParameter("client_id")
                val clientId = when {
                    savedClient == null -> issued ?: run { reply(s, 400, "Registration didn't finish."); return "ChatGPT didn't finish registering Set-Tai. Try again." }
                    issued != null && issued != savedClient -> { reply(s, 400, "Wrong app registration."); return "ChatGPT sent a different app registration. Try again." }
                    else -> savedClient
                }
                val err = exchange(code, verifier, redirect, clientId, nonce)
                reply(s, 200, if (err == null) "✅ Connected! Your character can think with your ChatGPT plan now. Go back to Set-Tai." else "Something went wrong: $err")
                if (err == null) bringBack()
                return err
            }
        }

        return "Sign-in stopped."
    }

    /** Code -> tokens; checks the ID token and the plan permission, then keeps everything encrypted. */
    private fun exchange(code: String, verifier: String, redirect: String, clientId: String, nonce: String): String? {
        val body = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", clientId).add("code", code)
            .add("code_verifier", verifier).add("redirect_uri", redirect).add("resource", RESOURCE).build()
        Http.client.newCall(Request.Builder().url(TOKEN).post(body).build()).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            val o = runCatching { JSONObject(txt) }.getOrNull() ?: return "OpenAI sent an unreadable answer (HTTP ${r.code})."
            if (!r.isSuccessful) return ChatGptBrain.friendly(o.optString("error"), o.optString("error_description"))
            val idTok = o.optString("id_token")
            // OIDC Core 3.1.3.7: the ID token came straight from the token endpoint over TLS, so the TLS check stands in
            // for the signature; the claims are still checked here.
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
            prefs.edit().putString("client_id", clientId).apply()
            save(rec)
            return null
        }
    }

    // ---------------- tokens for the brain ----------------
    @Synchronized override fun accessToken(): String {
        val r = record() ?: throw BrainException(ChatGptBrain.SIGN_IN_AGAIN)
        if (r.optLong("expires_at") - 90_000L > System.currentTimeMillis()) return r.optString("access_token")
        if (!refresh()) throw BrainException(ChatGptBrain.SIGN_IN_AGAIN)
        return record()!!.optString("access_token")
    }

    @Synchronized override fun onUnauthorized(): Boolean = refresh()

    /** Swaps the rotating refresh token for a new pair. false = the user must sign in again (tokens cleared). */
    @Synchronized private fun refresh(): Boolean {
        val r = record() ?: return false
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", r.optString("client_id"))
            .add("refresh_token", r.optString("refresh_token")).add("resource", RESOURCE).build()
        return try {
            Http.client.newCall(Request.Builder().url(TOKEN).post(body).build()).execute().use { resp ->
                val o = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull() ?: JSONObject()
                if (!resp.isSuccessful) {
                    val e = o.optString("error")
                    // only a dead refresh token signs out; a network or server hiccup keeps the tokens
                    if (e in DEAD) { save(null); return false }
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

    /** Forget the tokens on this phone (the app registration and the old ID token hint are kept for a quick sign-in). */
    fun signOut() = save(null)

    // ---------------- helpers ----------------
    private fun bringBack() {
        runCatching {
            app.startActivity(Intent(app, com.combo.monkeycompanion.ui.BrainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

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
    private fun b64url(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** The middle part of a JWT, decoded (no signature check here, see [exchange]). */
    fun jwtClaims(jwt: String): JSONObject? = runCatching {
        val part = jwt.split('.')[1]
        JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8))
    }.getOrNull()

    private fun audienceHas(c: JSONObject, clientId: String): Boolean {
        val a = c.opt("aud")
        return when (a) { is String -> a == clientId; is org.json.JSONArray -> (0 until a.length()).any { a.optString(it) == clientId }; else -> false }
    }
}
