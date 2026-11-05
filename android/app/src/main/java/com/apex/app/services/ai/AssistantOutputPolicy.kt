package com.apex.app.services.ai

/** Presentation policy for assistant-authored text, never user input or structured tool data. */
object AssistantOutputPolicy {
    const val INSTRUCTION = "For every assistant reply and transcript, do not use Unicode emoji, emoticons, decorative pictographs, reaction graphics, or inline images. Use words and ordinary text formatting instead, even if requested otherwise."

    // Covers common pictographic ranges, keycaps, flags, modifiers and joined sequences.
    // Keep ordinary digits, punctuation, non-Latin writing and zero-width joiners outside
    // pictographic sequences intact. This is a safeguard in addition to the instruction.
    private const val PICTOGRAPH = "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}\\x{2B00}-\\x{2BFF}]"
    private const val EXTEND = "(?:\\uFE0F|[\\x{1F3FB}-\\x{1F3FF}]|[\\x{E0020}-\\x{E007F}])*"
    private val visualSymbols = Regex(
        "(?:[#*0-9]\\uFE0F?\\u20E3|$PICTOGRAPH$EXTEND(?:\\u200D$PICTOGRAPH$EXTEND)*|[\\u00A9\\u00AE\\u203C\\u2049\\u2122\\u2139\\x{2190}-\\x{21FF}]\\uFE0F)"
    )

    fun clean(text: String): String = visualSymbols.replace(text, "")
}
