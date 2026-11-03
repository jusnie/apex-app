import AppIntents
import SwiftUI
import WidgetKit

/// A Control Center (and Lock Screen) control that opens APEX straight into a
/// voice conversation. `TalkToAPEXIntent` is shared with the app target, which
/// runs it when the app opens.
@available(iOS 18.0, *)
struct VoiceAssistantControl: ControlWidget {
    static let kind = "com.apex.app.voiceAssistant"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: TalkToAPEXIntent()) {
                Label("Talk to APEX", systemImage: "waveform.circle.fill")
            }
        }
        .displayName("Talk to APEX")
        .description("Opens APEX straight into a voice conversation.")
    }
}
