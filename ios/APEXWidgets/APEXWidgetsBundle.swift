import WidgetKit
import SwiftUI

@main
struct APEXWidgetsBundle: WidgetBundle {
    var body: some Widget {
        CalorieWidget()
        ProteinWidget()
        WaterWidget()
        LogFoodWidget()
        if #available(iOS 18.0, *) {
            VoiceAssistantControl()
        }
    }
}
