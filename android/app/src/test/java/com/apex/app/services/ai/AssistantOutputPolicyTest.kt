package com.apex.app.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantOutputPolicyTest {
    @Test fun removesPictographsAndJoinedSequences() {
        val sample = "Ready \uD83D\uDE00 \u2764\uFE0F \uD83D\uDC69\u200D\uD83D\uDCBB \uD83C\uDDF5\uD83C\uDDED 1\uFE0F\u20E3."
        assertEquals("Ready     .", AssistantOutputPolicy.clean(sample))
    }

    @Test fun preservesNumbersLanguagesAndOrdinaryJoiners() {
        val text = "3 sets × 10 kg. العربية فارسی می\u200Cروم हिन्दी नमस्ते. 50%"
        assertEquals(text, AssistantOutputPolicy.clean(text))
    }
}
