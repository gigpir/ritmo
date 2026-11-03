import SwiftUI

/// The neutral colours of the dark interface; the phase colours are in `Palette`.
enum Theme {
    static let background = Color(hex: 0x0E0E10)
    static let surface = Color(hex: 0x17171A)
    static let control = Color(hex: 0x1F1F23)
    static let divider = Color(hex: 0x1C1C1F)
    static let text = Color(hex: 0xF5F5F7)
    static let secondary = Color(hex: 0xA1A1AA)
}

extension Step.Phase {
    var bright: Color { Palette.bright(rawValue) }
    var dark: Color { Palette.dark(rawValue) }
    var ink: Color { Palette.ink(rawValue) }
}

extension Interval.Kind {
    var color: Color { self == .work ? Step.Phase.work.bright : Step.Phase.rest.bright }
}

extension Font {
    /// SF Pro Compressed, for the countdown.
    static func digits(_ size: CGFloat) -> Font {
        .system(size: size, weight: .black).width(.compressed)
    }

    /// SF Pro Condensed, for phase names, titles and counters.
    static func condensed(_ size: CGFloat, weight: Font.Weight = .heavy) -> Font {
        .system(size: size, weight: weight).width(.condensed)
    }
}

/// The whole workout as a row of blocks, one per step, as wide as its duration.
struct StripView: View {
    let steps: [Step]
    var current: Int?
    var height: CGFloat = 8
    var spacing: CGFloat = 2

    var body: some View {
        GeometryReader { geo in
            let total = CGFloat(max(1, steps.last?.end ?? 1))
            let free = max(0, geo.size.width - spacing * CGFloat(max(0, steps.count - 1)))
            HStack(spacing: spacing) {
                ForEach(Array(steps.enumerated()), id: \.offset) { i, step in
                    RoundedRectangle(cornerRadius: height / 4)
                        .fill(step.phase.bright)
                        .opacity(current.map { i < $0 ? 0.35 : 1 } ?? 1)
                        .overlay {
                            if i == current {
                                RoundedRectangle(cornerRadius: height / 4).stroke(Theme.text, lineWidth: 2)
                            }
                        }
                        .frame(width: max(1, free * CGFloat(step.seconds) / total))
                }
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

/// A round icon button face.
struct RoundIcon: View {
    let systemImage: String
    var size: CGFloat = 44
    var background: Color = .black.opacity(0.6)
    var foreground: Color = Theme.text

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: size * 0.4, weight: .bold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(background, in: Circle())
            .contentShape(Circle())
    }
}
