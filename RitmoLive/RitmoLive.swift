import ActivityKit
import SwiftUI
import WidgetKit

@main
struct RitmoLiveBundle: WidgetBundle {
    var body: some Widget {
        WorkoutLiveActivity()
    }
}

/// The running workout on the lock screen and in the Dynamic Island (SPEC §5.6).
struct WorkoutLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: WorkoutActivity.self) { context in
            LockScreenView(workout: context.attributes.workout, state: context.state)
                .activityBackgroundTint(Palette.dark(context.state.phase))
                .activitySystemActionForegroundColor(Palette.bright(context.state.phase))
        } dynamicIsland: { context in
            let state = context.state
            let color = Palette.bright(state.phase)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(state.step.uppercased())
                        .font(.system(size: 24, weight: .heavy).width(.condensed))
                        .foregroundStyle(color)
                        .lineLimit(1)
                        .padding(.leading, 6)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Countdown(state: state)
                        .font(.system(size: 30, weight: .black).width(.compressed))
                        .foregroundStyle(color)
                        .padding(.trailing, 6)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        if let next = state.next { Text("Next: \(next)") }
                        Spacer()
                        Text(state.pausedAt == nil ? state.progress : "Paused")
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.75))
                    .padding(.horizontal, 6)
                }
            } compactLeading: {
                Text(state.step.uppercased())
                    .font(.system(size: 14, weight: .heavy).width(.condensed))
                    .foregroundStyle(color)
                    .lineLimit(1)
            } compactTrailing: {
                Countdown(state: state)
                    .font(.system(size: 16, weight: .heavy).width(.condensed))
                    .foregroundStyle(color)
                    .frame(width: 44)
            } minimal: {
                Countdown(state: state)
                    .font(.system(size: 13, weight: .heavy).width(.condensed))
                    .foregroundStyle(color)
            }
            .keylineTint(color)
        }
    }
}

private struct LockScreenView: View {
    let workout: String
    let state: WorkoutActivity.ContentState

    var body: some View {
        let color = Palette.bright(state.phase)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("\(state.step.uppercased()) · \(workout)")
                    .font(.system(size: 16, weight: .heavy))
                    .tracking(1)
                    .foregroundStyle(color)
                    .lineLimit(1)
                Spacer()
                Text(state.pausedAt == nil ? state.progress : "Paused")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.75))
            }
            Countdown(state: state)
                .font(.system(size: 72, weight: .black).width(.compressed))
                .foregroundStyle(color)
            if let next = state.next, let nextPhase = state.nextPhase {
                HStack(spacing: 8) {
                    Circle().fill(Palette.bright(nextPhase)).frame(width: 10, height: 10)
                    Text("Next: \(next)")
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white.opacity(0.85))
            }
        }
        .padding(16)
    }
}

/// The step's countdown. iOS runs it by itself, so the app doesn't have to update it every second.
private struct Countdown: View {
    let state: WorkoutActivity.ContentState

    var body: some View {
        Text(timerInterval: state.starts...max(state.starts, state.ends), pauseTime: state.pausedAt, countsDown: true)
            .monospacedDigit()
            .multilineTextAlignment(.trailing)
    }
}
