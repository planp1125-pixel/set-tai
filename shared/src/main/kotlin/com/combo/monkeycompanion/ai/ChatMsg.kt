package com.combo.monkeycompanion.ai

/** One chat line (shared by Android and desktop). [note] = context line that wasn't a direct reply. */
data class ChatMsg(val fromUser: Boolean, val text: String, val time: Long = System.currentTimeMillis(), val note: Boolean = false)
