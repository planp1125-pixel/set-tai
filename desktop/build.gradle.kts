plugins {
    id("org.jetbrains.kotlin.jvm")
    // D3: Compose for Desktop for the app window (the floating character still draws itself with Java2D)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Shares the platform-neutral Settai code with the Android app; android.graphics is a Java2D shim here.
sourceSets["main"].kotlin.srcDir("../shared/src/main/kotlin")

kotlin { jvmToolchain(21) }

/** Windows-only libraries (OSHI + JNA). compileOnly for everyone; bundled only into the Windows jar. */
val windowsLibs by configurations.creating

dependencies {
    compileOnly("com.github.oshi:oshi-core:6.6.5")
    windowsLibs("com.github.oshi:oshi-core:6.6.5")
    windowsLibs("org.slf4j:slf4j-nop:2.0.16")
    implementation(compose.desktop.linux_x64)
    implementation(compose.material3)
    windowsLibs(compose.desktop.windows_x64)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
}

val mainClassName = "com.combo.settai.desktop.MainKt"

/** D1 (v10.50.3): the desktop app has the same version number as the phone app. */
val appVersion: String = Regex("versionName = \"([^\"]+)\"").find(rootProject.file("app/build.gradle.kts").readText())?.groupValues?.get(1) ?: "0.0"

tasks.jar {
    manifest { attributes("Main-Class" to mainClassName, "Implementation-Version" to appVersion) }
}

/** Single runnable jar with all dependencies (input for jpackage). */
val fatJar by tasks.registering(Jar::class) {
    archiveBaseName.set("settai-desktop")
    archiveClassifier.set("all")
    archiveVersion.set("")
    manifest { attributes("Main-Class" to mainClassName, "Implementation-Version" to appVersion) }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets["main"].output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
    }
}

/** Windows build: everything in fatJar plus OSHI/JNA (JNA natives trimmed to win32-x86-64). */
val windowsJar by tasks.registering(Jar::class) {
    archiveBaseName.set("settai-desktop")
    archiveClassifier.set("windows")
    archiveVersion.set("")
    manifest { attributes("Main-Class" to mainClassName, "Implementation-Version" to appVersion) }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets["main"].output)
    dependsOn(configurations.runtimeClasspath, windowsLibs)
    from({ (configurations.runtimeClasspath.get() + windowsLibs).filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
        exclude("com/sun/jna/aix-*/**", "com/sun/jna/darwin*/**", "com/sun/jna/linux-*/**", "com/sun/jna/freebsd-*/**",
            "com/sun/jna/openbsd-*/**", "com/sun/jna/sunos-*/**", "com/sun/jna/dragonflybsd-*/**", "com/sun/jna/win32-x86/**", "com/sun/jna/win32-aarch64/**",
            "libskiko-linux-*.so", "libskiko-linux-*.so.sha256")
    }
}
