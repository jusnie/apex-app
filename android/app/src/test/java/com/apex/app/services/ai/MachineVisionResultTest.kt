package com.apex.app.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineVisionResultTest {
    @Test fun parsesCandidateAndDefaultsToUncertain() {
        val value = MachineVisionResult.parse("""{"name":"Chest press","primary_muscles":["chest"],"setup_cues":["Adjust the seat"],"search_query":"chest press"}""")
        assertEquals("Chest press", value.name)
        assertEquals(listOf("chest"), value.primaryMuscles)
        assertTrue(value.uncertain)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedResponse() { MachineVisionResult.parse("not json") }
}
