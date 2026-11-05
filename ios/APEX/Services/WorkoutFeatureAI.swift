import Foundation
import UIKit

struct MachineVisionResult {
    let name: String
    let uncertain: Bool
    let primaryMuscles: [String]
    let assistingMuscles: [String]
    let setupCues: [String]
    let searchQuery: String

    static let prompt = """
    Identify the gym machine in this photo. If it is not clearly identifiable, set uncertain true and say so. Do not infer specific pin numbers, load, brand, or exact anatomy. Return only JSON: {"name":"machine name or unknown","uncertain":true,"primary_muscles":["catalog muscle names"],"assisting_muscles":["catalog muscle names"],"setup_cues":["up to three general safe setup and form cues"],"search_query":"short exercise name"}. Never output decorative pictographs or emoticons. Remind users to check the machine instructions and consult a trainer if unsure.
    """

    static func parse(_ response: String) throws -> MachineVisionResult {
        let text = GeminiService.extractJSON(from: response)
        guard let data = text.data(using: .utf8), let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "APEX.MachineGuide", code: 1, userInfo: [NSLocalizedDescriptionKey: "The machine result could not be read. Try another photo."])
        }
        func items(_ key: String) -> [String] {
            Array((obj[key] as? [String] ?? []).prefix(8)).map(AssistantOutputPolicy.clean).filter { !$0.isEmpty }
        }
        return MachineVisionResult(name: String(AssistantOutputPolicy.clean(obj["name"] as? String ?? "Unknown machine").prefix(120)),
            uncertain: obj["uncertain"] as? Bool ?? true,
            primaryMuscles: items("primary_muscles"), assistingMuscles: items("assisting_muscles"),
            setupCues: Array(items("setup_cues").prefix(3)), searchQuery: String(AssistantOutputPolicy.clean(obj["search_query"] as? String ?? "").prefix(120)))
    }
}
