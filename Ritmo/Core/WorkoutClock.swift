import Foundation

/// Where a workout stands, computed from timestamps instead of counted ticks (SPEC §4.3),
/// so it stays right after the screen locks or the app sleeps.
struct WorkoutClock {
    let steps: [Step]
    let duration: TimeInterval
    /// Elapsed time at the last pause or jump.
    private var elapsedBefore: TimeInterval = 0
    /// When the clock last started running; nil while paused.
    private var runningSince: Date?

    init(steps: [Step]) {
        self.steps = steps
        duration = TimeInterval(steps.last?.end ?? 0)
    }

    var isRunning: Bool { runningSince != nil }

    func elapsed(at now: Date) -> TimeInterval {
        let running = runningSince.map { now.timeIntervalSince($0) } ?? 0
        return min(max(elapsedBefore + running, 0), duration)
    }

    func isFinished(at now: Date) -> Bool { elapsed(at: now) >= duration }

    /// The step running at `now`; the last one once the workout is over.
    func stepIndex(at now: Date) -> Int {
        let elapsed = elapsed(at: now)
        return steps.lastIndex { TimeInterval($0.start) <= elapsed } ?? 0
    }

    /// Seconds left in the current step.
    func remaining(at now: Date) -> TimeInterval {
        guard !steps.isEmpty else { return 0 }
        return TimeInterval(steps[stepIndex(at: now)].end) - elapsed(at: now)
    }

    func snapshot(at now: Date) -> TimerSnapshot {
        TimerSnapshot(
            step: stepIndex(at: now),
            shown: Int(remaining(at: now).rounded(.up)),
            finished: isFinished(at: now)
        )
    }

    mutating func pause(at now: Date) {
        elapsedBefore = elapsed(at: now)
        runningSince = nil
    }

    mutating func resume(at now: Date) {
        guard runningSince == nil, elapsedBefore < duration else { return }
        runningSince = now
    }

    /// Jumps to the start of the next step, or to the end from the last one.
    mutating func next(at now: Date) {
        let i = stepIndex(at: now)
        jump(to: i + 1 < steps.count ? TimeInterval(steps[i + 1].start) : duration, at: now)
    }

    /// Restarts the current step, or goes back one step during its first 2 seconds.
    mutating func previous(at now: Date) {
        guard !steps.isEmpty else { return }
        let i = stepIndex(at: now)
        let intoStep = elapsed(at: now) - TimeInterval(steps[i].start)
        let target = intoStep > 2 || i == 0 ? steps[i].start : steps[i - 1].start
        jump(to: TimeInterval(target), at: now)
    }

    private mutating func jump(to elapsed: TimeInterval, at now: Date) {
        elapsedBefore = elapsed
        if runningSince != nil { runningSince = now }
    }
}

/// What the screen shows at one moment, enough to decide which cues are due.
struct TimerSnapshot: Equatable {
    var step: Int
    /// Seconds left in the step, rounded up as displayed.
    var shown: Int
    var finished: Bool
}

/// Sounds and announcements of the timer (SPEC §4.4).
enum Cue: Equatable {
    case beep
    case announce(String)
    case finish

    /// The cues due when the timer moves from `old` to `new`: the name of every step
    /// that starts (or restarts), a beep at 3, 2 and 1 seconds left, and the end.
    static func due(from old: TimerSnapshot?, to new: TimerSnapshot, steps: [Step]) -> [Cue] {
        if new.finished { return old?.finished == true ? [] : [.finish] }
        guard let old, old.step == new.step, new.shown <= old.shown else {
            return [.announce(steps[new.step].name)]
        }
        if new.shown < old.shown, (1...3).contains(new.shown), new.shown < steps[new.step].seconds {
            return [.beep]
        }
        return []
    }
}
