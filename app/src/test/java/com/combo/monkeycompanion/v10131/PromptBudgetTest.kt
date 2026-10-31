package com.combo.monkeycompanion.v10131

import com.combo.monkeycompanion.brain.PromptBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBudgetTest {
    private val bigSystem = (1..300).joinToString("\n") { "Rule $it: be a playful monkey companion and keep replies short, kind and helpful." }
    private val history = (1..40).map { PromptBudget.Turn(it % 2 == 1, "message number $it with some chatter about the day and bananas") }

    @Test fun readsContextFromModelName() {
        assertEquals(1280, PromptBudget.contextFromName("qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"))
        assertEquals(4096, PromptBudget.contextFromName("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"))
        assertEquals(4096, PromptBudget.contextFromName("gemma-4-E2B-it.litertlm"))
    }

    @Test fun hiFitsUnder70PercentOfA1280Model() {
        val f = PromptBudget.fit(bigSystem, history, "Hi", 1280)
        assertTrue("tokens ${f.tokens}", f.tokens <= 1280 * 0.7f)
        assertTrue(f.trimmed)
        assertEquals("Hi", f.user)
        assertTrue("system kept its opening", f.system.startsWith("Rule 1:"))
    }

    @Test fun oldestHistoryGoesFirst() {
        val f = PromptBudget.fit("You are Settai.", history, "Hi", 1280)
        assertTrue(f.history.isNotEmpty())
        assertEquals(history.last(), f.history.last())
        assertTrue(f.history.size < history.size)
    }

    @Test fun smallPromptsAreUntouched() {
        val h = history.take(2)
        val f = PromptBudget.fit("You are Settai.", h, "Hi", 4096)
        assertEquals(h, f.history); assertEquals("You are Settai.", f.system); assertTrue(!f.trimmed)
    }

    @Test fun minimalRetryIsTiny() {
        val f = PromptBudget.fit(bigSystem, history, "Hi", 1280, minimal = true)
        assertTrue(f.history.isEmpty()); assertTrue(f.tokens <= 640)
    }

    @Test fun recognisesTheRuntimeError() {
        assertTrue(PromptBudget.isTooLong("Input token ids are too long. Exceeding the maximum number of tokens allowed: 1974 >= 1280"))
        assertTrue(!PromptBudget.isTooLong("network down"))
    }

    @Test fun hugeUserTextIsCut() {
        val f = PromptBudget.fit("You are Settai.", emptyList(), "word ".repeat(3000), 1280)
        assertTrue(f.tokens <= 1280 * 0.7f)
    }
}
