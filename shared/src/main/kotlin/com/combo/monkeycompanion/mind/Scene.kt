package com.combo.monkeycompanion.mind

/** What kind of app is in front (from the package name only: cheap, no screen reading). */
enum class AppCat(val label: String) {
    VIDEO("video"), FOOD("food delivery"), CALCULATOR("calculator"), BANKING("banking / payments"), MAPS("maps"), NEWS("news"),
    CAMERA("camera"), SOCIAL("social"), CHAT("chat"), MEETING("meeting"), EMAIL("email"), READING("reading / docs"),
    BROWSER("browser"), SETTINGS("settings"), SHOPPING("shopping"), GAME("game"), MUSIC("music"), HOME("home screen"), OTHER("app");

    companion object {
        private val rules: List<Pair<AppCat, List<String>>> = listOf(
            BANKING to listOf("bank", "sbi", "hdfc", "icici", "axis", "kotak", "paytm", "phonepe", "nbu.paisa", "upi", "bhim", "yono", "cred", "groww", "zerodha", "kite"),
            MEETING to listOf("zoom", "teams", "apps.meetings", "webex", "gotomeeting", "slack", "skype"),
            CALCULATOR to listOf("calculator", "calc"),
            VIDEO to listOf("youtube", "netflix", "hotstar", "primevideo", "avod", "mxplayer", "jiocinema", "sonyliv", "zee5", "vlc"),
            FOOD to listOf("swiggy", "zomato", "eatsure", "dominos", "ubereats", "pizzahut", "kfc", "zepto", "blinkit", "instamart"),
            MAPS to listOf("maps", "ola", "uber", "rapido", "waze", "namma"),
            NEWS to listOf("news", "inshorts", "thehindu", "timesofindia", "dailyhunt", "ndtv", "hindustantimes", "manorama"),
            CAMERA to listOf("camera", "snapchat", "gcam", "retrica"),
            SOCIAL to listOf("instagram", "facebook", "katana", "twitter", "com.x.android", "threads", "sharechat", "moj", "reddit", "linkedin", "pinterest"),
            CHAT to listOf("whatsapp", "telegram", "messaging", "mms", "signal", "messenger", "discord"),
            EMAIL to listOf("gmail", "outlook", "email", "mail"),
            READING to listOf("docs", "office", "word", "kindle", "notion", "drive", "pdf", "keep", "evernote", "sheets"),
            BROWSER to listOf("chrome", "browser", "firefox", "opera", "brave", "edge", "duckduckgo"),
            SETTINGS to listOf("settings"),
            SHOPPING to listOf("amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "bigbasket"),
            MUSIC to listOf("spotify", "music", "gaana", "jiosaavn", "wynk"),
            HOME to listOf("launcher", "home", "nexuslauncher", "trebuchet", "lawnchair"),
        )
        private val exact = mapOf(
            "com.google.android.gm" to EMAIL, "com.google.android.apps.messaging" to CHAT, "com.google.android.dialer" to OTHER,
            "com.google.android.apps.photos" to CAMERA, "com.google.android.apps.nbu.paisa.user" to BANKING,
            "com.google.android.apps.tachyon" to MEETING, "com.google.android.apps.meetings" to MEETING,
            "com.google.android.calculator" to CALCULATOR, "com.android.vending" to SHOPPING,
        )
        fun of(pkg: String, isGame: Boolean = false): AppCat {
            if (isGame) return GAME
            val p = pkg.lowercase()
            if (p.isBlank()) return HOME
            exact[p]?.let { return it }
            return rules.firstOrNull { (_, keys) -> keys.any { it in p } }?.first ?: OTHER
        }
        /** A readable app name from a package ("com.application.zomato" -> "Zomato"). */
        fun appName(pkg: String): String {
            val parts = pkg.split('.').filter { it.isNotBlank() && it !in setOf("com", "android", "app", "apps", "in", "org", "net", "google", "application", "mobile", "client") }
            return (parts.lastOrNull() ?: pkg).replaceFirstChar { it.uppercase() }
        }
    }
}

/** A compact snapshot of what the human is doing, perceived on app changes (never polled). */
data class Scene(
    val pkg: String = "",
    val cat: AppCat = AppCat.HOME,
    val keyboard: Boolean = false,
    val fullscreen: Boolean = false,
    val call: Boolean = false,
    val dnd: Boolean = false,
    val quietHours: Boolean = false,
    /** minutes since midnight */
    val minute: Int = 12 * 60,
    /** seconds the human has stayed in this app without switching */
    val dwellSecs: Int = 0,
    val batteryLow: Boolean = false,
    /** typing bursts in the last minute */
    val typingBursts: Int = 0,
) {
    val hour get() = minute / 60
    val app get() = if (pkg.isBlank()) "home screen" else AppCat.appName(pkg)
}
