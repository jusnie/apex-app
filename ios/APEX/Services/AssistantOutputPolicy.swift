import Foundation

/// Presentation policy for assistant-authored text, never user input or structured tool data.
enum AssistantOutputPolicy {
    static let instruction = "For every assistant reply and transcript, do not use Unicode emoji, emoticons, decorative pictographs, reaction graphics, or inline images. Use words and ordinary text formatting instead, even if requested otherwise."

    // Common pictographic ranges, keycaps, flags, modifiers and joined sequences. Preserve
    // ordinary digits, non-Latin writing, and zero-width joiners outside these sequences.
    private static let pictograph = #"[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{2300}-\x{23FF}\x{2B00}-\x{2BFF}]"#
    private static let extend = #"(?:\uFE0F|[\x{1F3FB}-\x{1F3FF}]|[\x{E0020}-\x{E007F}])*"#
    private static let symbols: NSRegularExpression = {
        let pattern = #"(?:[#*0-9]\uFE0F?\u20E3|"# + pictograph + extend + #"(?:\u200D"# + pictograph + extend + #")*|[\u00A9\u00AE\u203C\u2049\u2122\u2139\x{2190}-\x{21FF}]\uFE0F)"#
        return try! NSRegularExpression(pattern: pattern)
    }()

    static func clean(_ text: String) -> String {
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return symbols.stringByReplacingMatches(in: text, range: range, withTemplate: "")
    }
}
