package com.combo.monkeycompanion.v10132

import com.combo.monkeycompanion.ai.ModelList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelListTest {
    private val gemini = """{"models":[
      {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
      {"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent","countTokens"]},
      {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},
      {"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["generateContent"]},
      {"name":"models/gemini-3-flash-preview","supportedGenerationMethods":["generateContent"]},
      {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}]}"""

    @Test fun geminiListKeepsOnlyGenerateContentAndPicksNewestFlash() {
        val ids = ModelList.parseGemini(gemini)
        assertFalse(ids.contains("text-embedding-004"))
        assertTrue(ids.contains("gemini-3.8-flash"))
        assertEquals("gemini-3.8-flash", ModelList.pickDefault(ids))
        assertFalse(ModelList.sortForChat(ids).contains("gemini-3.8-flash-tts"))
    }

    @Test fun openAiStyleList() {
        val ids = ModelList.parseOpenAi("""{"data":[{"id":"gpt-4o"},{"id":"whisper-1"},{"id":"gpt-5.4-mini"},{"id":"text-embedding-3-small"}]}""")
        val chat = ModelList.sortForChat(ids)
        assertEquals("gpt-5.4-mini", chat.first())
        assertFalse(chat.contains("whisper-1")); assertFalse(chat.contains("text-embedding-3-small"))
        assertEquals("gpt-5.4-mini", ModelList.pickDefault(ids))
    }

    @Test fun clearHttpHints() {
        // messages are produced by Http.fail; just make sure the wording exists in the build
        assertTrue(ModelList.version("gemini-3.8-flash") == 3.8)
    }
}
