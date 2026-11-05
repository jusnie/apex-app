import Testing
@testable import APEX

@MainActor
struct MachineVisionResultTests {
    @Test func candidateDefaultsToUncertain() throws {
        let value = try MachineVisionResult.parse(#"{"name":"Chest press","primary_muscles":["chest"],"setup_cues":["Adjust the seat"],"search_query":"chest press"}"#)
        #expect(value.name == "Chest press")
        #expect(value.primaryMuscles == ["chest"])
        #expect(value.uncertain)
    }
    @Test func malformedResponseThrows() {
        #expect(throws: (any Error).self) { try MachineVisionResult.parse("not json") }
    }
}
