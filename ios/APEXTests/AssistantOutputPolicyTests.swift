import Testing
@testable import APEX

struct AssistantOutputPolicyTests {
    @Test func removesPictographsAndJoinedSequences() {
        let sample = "Ready \u{1F600} \u{2764}\u{FE0F} \u{1F469}\u{200D}\u{1F4BB} \u{1F1F5}\u{1F1ED} 1\u{FE0F}\u{20E3}."
        #expect(AssistantOutputPolicy.clean(sample) == "Ready     .")
    }

    @Test func preservesNumbersLanguagesAndOrdinaryJoiners() {
        let text = "3 sets × 10 kg. العربية فارسی می\u{200C}روم हिन्दी नमस्ते. 50%"
        #expect(AssistantOutputPolicy.clean(text) == text)
    }
}
