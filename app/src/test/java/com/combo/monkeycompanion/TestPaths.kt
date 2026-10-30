package com.combo.monkeycompanion

import java.io.File

/** Where tests write previews/reports and read project files (set in app/build.gradle.kts; works on any machine). */
object TestPaths {
    private val rootDir = File(System.getProperty("settai.root") ?: "..")
    private val outDir = File(System.getProperty("settai.out") ?: "build/test-out")

    /** A file under the test output folder (parent dirs created). */
    fun out(name: String): File = File(outDir, name).also { it.parentFile?.mkdirs() }

    /** A file in the project, relative to the settai-next root. */
    fun root(path: String): File = File(rootDir, path)
}
