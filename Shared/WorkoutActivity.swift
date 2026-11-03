import ActivityKit
import Foundation

/// What the lock screen and the Dynamic Island show during a workout.
struct WorkoutActivity: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// The step's name, e.g. "Work".
        var step: String
        /// prepare, work, rest or recover: picks the colours.
        var phase: String
        /// When the step started and when it ends, so the countdown runs by itself.
        var starts: Date
        var ends: Date
        /// Set while paused: the countdown stops at this moment.
        var pausedAt: Date?
        /// The step after this one, e.g. "Rest 0:10"; nil on the last step.
        var next: String?
        var nextPhase: String?
        /// e.g. "Round 3/8".
        var progress: String
    }

    var workout: String
}
