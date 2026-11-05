package com.apex.app.services.ai

import org.json.JSONObject

/** Only an identification candidate; never a substitute for the machine label or a trainer. */
data class MachineVisionResult(
    val name: String,
    val uncertain: Boolean,
    val primaryMuscles: List<String>,
    val assistingMuscles: List<String>,
    val setupCues: List<String>,
    val searchQuery: String
) {
    companion object {
        const val PROMPT = """Identify the gym machine in this photo. If it is not clearly identifiable, set uncertain true and say so. Do not infer specific pin numbers, load, brand, or exact anatomy. Return only JSON: {"name":"machine name or unknown","uncertain":true,"primary_muscles":["catalog muscle names"],"assisting_muscles":["catalog muscle names"],"setup_cues":["up to three general safe setup and form cues"],"search_query":"short exercise name"}. Never output decorative pictographs or emoticons. Remind users to check the machine instructions and consult a trainer if unsure."""
        fun parse(text: String): MachineVisionResult {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            require(start >= 0 && end > start) { "The machine result could not be read. Try another photo." }
            val obj = JSONObject(text.substring(start, end + 1))
            fun list(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return (0 until minOf(arr.length(), 8)).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(AssistantOutputPolicy::clean)
                }
            }
            val name = AssistantOutputPolicy.clean(obj.optString("name", "Unknown machine")).take(120)
            return MachineVisionResult(name, obj.optBoolean("uncertain", true), list("primary_muscles"), list("assisting_muscles"), list("setup_cues").take(3), AssistantOutputPolicy.clean(obj.optString("search_query", "")).take(120))
        }
    }
}
