package com.combo.monkeycompanion.agent

/**
 * v4 screen-agent safety rules (pure, unit-tested). Settai never drives: the lock screen, banking /
 * payment / wallet apps (default list + the user's own list), screens showing a password field (it
 * may read them, never type or tap there), or Settai itself. "Risky" buttons (send, pay, delete...)
 * always ask first, even when per-step confirmation is switched off.
 */
object AgentSafety {
    sealed class Verdict { object Ok : Verdict(); class Blocked(val reason: String) : Verdict() }

    /** Well-known banking / UPI / wallet / payment apps (India + global). The user can add more. */
    val DEFAULT_BLOCKED = listOf(
        "com.google.android.apps.nbu.paisa.user",  // Google Pay
        "net.one97.paytm", "com.phonepe.app", "in.org.npci.upiapp", "in.amazon.mShop.android.shopping.pay",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.snapwork.hdfc", "com.hdfcbank.payzapp", "com.csam.icici.bank.imobile",
        "com.axis.mobile", "com.msf.kbank.mobile", "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
        "com.bankofbaroda.mconnect", "com.infrasofttech.indianbank", "com.canarabank.mobility", "com.mobikwik_new", "com.freecharge.android",
        "com.dreamplug.androidapp",  // CRED
        "com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash", "com.revolut.revolut",
        "com.zerodha.kite3", "com.groww", "com.upstox.pro", "com.binance.dev", "com.coinbase.android",
    )
    private val RISKY_PKG_WORDS = listOf("bank", "upi", "wallet", "paytm", "phonepe", "paisa", "banking", "netbank", "mpay", "payzapp", "trading", "crypto")

    /** Words on a button that always need a Yes, whatever the per-step setting says. */
    private val RISKY_WORDS = Regex("\\b(send|pay|paid|payment|buy|purchase|order|checkout|place order|delete|remove|erase|clear all|uninstall|install|post|publish|share|tweet|reply|forward|transfer|confirm|submit|subscribe|unsubscribe|sign out|log ?out|factory|reset|call|accept|decline|block|report|upload|donate|book now|proceed|allow|grant)\\b", RegexOption.IGNORE_CASE)

    fun isBlockedApp(pkg: String, userBlocked: Collection<String> = emptyList(), userAllowed: Collection<String> = emptyList()): Boolean {
        val p = pkg.lowercase()
        if (userAllowed.any { it.equals(pkg, true) }) return false
        if (userBlocked.any { it.isNotBlank() && (p == it.lowercase().trim() || p.startsWith(it.lowercase().trim() + ".")) }) return true
        if (DEFAULT_BLOCKED.any { it.equals(pkg, true) }) return true
        return RISKY_PKG_WORDS.any { w -> p.split('.').any { part -> part.contains(w) } }
    }

    /** May Settai read / act on this screen at all? */
    fun screen(s: ScreenSnapshot, ownPkg: String, userBlocked: Collection<String> = emptyList(), userAllowed: Collection<String> = emptyList()): Verdict = when {
        s.locked -> Verdict.Blocked("the phone is locked. I don't touch the lock screen. Unlock it first")
        s.pkg == ownPkg -> Verdict.Blocked("that's my own app, I can't drive myself")
        s.pkg.contains("keyguard", true) || s.pkg == "com.android.systemui" && s.nodes.any { it.password } -> Verdict.Blocked("that's the lock screen")
        isBlockedApp(s.pkg, userBlocked, userAllowed) -> Verdict.Blocked("${s.app} looks like a banking or payment app. I never drive money apps; you do that one yourself")
        else -> Verdict.Ok
    }

    /** May Settai tap / type on this node? Reading a password screen is fine; touching it is not. */
    fun action(s: ScreenSnapshot, node: UiNode?, typing: Boolean): Verdict = when {
        node?.password == true -> Verdict.Blocked("that's a password field. I never type or tap in password fields")
        typing && s.hasPassword && (node == null || node.focused) -> Verdict.Blocked("this screen has a password field. Type passwords yourself")
        typing && node != null && !node.editable -> Verdict.Blocked("that isn't a text field")
        else -> Verdict.Ok
    }

    fun risky(label: String): Boolean = RISKY_WORDS.containsMatchIn(label)

    /** OTP-looking codes are never typed by the agent (and never sent to the online brain). */
    fun looksLikeOtp(text: String): Boolean = Otp.find(text) != null
}
