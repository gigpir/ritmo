import ActivityKit
import Foundation

/// Shows the running workout on the lock screen and in the Dynamic Island.
@MainActor final class LiveActivityController {
    private var activity: Activity<WorkoutActivity>?

    func show(_ state: WorkoutActivity.ContentState, workout: String) {
        let content = ActivityContent(state: state, staleDate: nil)
        if let activity {
            Task { await activity.update(content) }
        } else if ActivityAuthorizationInfo().areActivitiesEnabled {
            Self.endAll()
            activity = try? Activity.request(attributes: WorkoutActivity(workout: workout), content: content)
        }
    }

    func end() {
        guard let activity else { return }
        self.activity = nil
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }

    /// Ends every Ritmo Live Activity on screen. Outside a running workout they are leftovers
    /// from an app closed abruptly (swiped away, or stopped from Xcode), which iOS keeps for hours.
    nonisolated static func endAll() {
        for activity in Activity<WorkoutActivity>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }

    /// For when iOS is about to close the app, for example swiped away during a workout:
    /// waits up to a second for the Live Activity to end, so it doesn't stay behind.
    nonisolated static func endAllBeforeExit() {
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            for activity in Activity<WorkoutActivity>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            done.signal()
        }
        _ = done.wait(timeout: .now() + 1)
    }
}
