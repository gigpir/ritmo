import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double(hex >> 16 & 0xFF) / 255,
            green: Double(hex >> 8 & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// The colours of each phase, shared by the app and its Live Activity.
/// Phases are named as in `Step.Phase`: prepare, work, rest, recover.
enum Palette {
    /// The phase colour: tide, digits and strip.
    static func bright(_ phase: String) -> Color {
        switch phase {
        case "prepare": Color(hex: 0xFFC21A)
        case "rest": Color(hex: 0x38BDF8)
        case "recover": Color(hex: 0xA78BFA)
        default: Color(hex: 0xFF5A1F)
        }
    }

    /// The dark tint left behind as the tide goes down.
    static func dark(_ phase: String) -> Color {
        switch phase {
        case "prepare": Color(hex: 0x2B2106)
        case "rest": Color(hex: 0x0A2230)
        case "recover": Color(hex: 0x1D1533)
        default: Color(hex: 0x2B1208)
        }
    }

    /// Text drawn on top of the bright colour.
    static func ink(_ phase: String) -> Color {
        switch phase {
        case "prepare": Color(hex: 0x171103)
        case "rest": Color(hex: 0x04131C)
        case "recover": Color(hex: 0x0F0A1F)
        default: Color(hex: 0x170A04)
        }
    }
}
