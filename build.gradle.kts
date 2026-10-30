plugins {
    id("com.android.application") version "9.4.1" apply false
    // Puts Kotlin Gradle Plugin 2.4.21 on the classpath so AGP's built-in Kotlin can read
    // LiteRT-LM's Kotlin 2.4 metadata.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // Desktop (Ubuntu) app: plain Kotlin/JVM + Swing, sharing ../shared with Android.
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
}
