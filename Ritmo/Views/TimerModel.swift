import SwiftUI
import UIKit

/// One workout on screen: it opens ready, and from the first Play it runs the clock,
/// the cues, the Live Activity and keeps the screen awake.
@MainActor @Observable final class TimerModel {
    let workout: Workout
    let steps: [Step]
    private(set) var clock: WorkoutClock
    private(set) var hasStarted = false
    private(set) var isMuted = false
    private(set) var isFinished = false

    @ObservationIgnored private let player = CuePlayer()
    @ObservationIgnored private let live = LiveActivityController()
    @ObservationIgnored private var ticker: Timer?
    @ObservationIgnored private var last: TimerSnapshot?
    /// The step and running state last sent to the Live Activity.
    @ObservationIgnored private var liveKey: [Int]?

    init(workout: Workout) {
        self.workout = workout
        steps = workout.steps
        clock = WorkoutClock(steps: steps)
    }

    func stop() {
        ticker?.invalidate()
        player.stop()
        live.end()
        UIApplication.shared.isIdleTimerDisabled = false
    }

    func togglePause() {
        if !hasStarted {
            begin()
        } else if clock.isRunning {
            pause()
        } else {
            resume()
        }
    }

    func pause() {
        clock.pause(at: .now)
        updateLive(force: true)
    }

    func next() {
        clock.next(at: .now)
        tick()
        updateLive(force: true)
    }

    func previous() {
        clock.previous(at: .now)
        tick()
        updateLive(force: true)
    }

    func toggleMute() {
        isMuted.toggle()
        player.isMuted = isMuted
    }

    private func begin() {
        hasStarted = true
        UIApplication.shared.isIdleTimerDisabled = true
        player.onInterruption = { [weak self] in self?.pause() }
        // Cues are checked ten times a second; the screen redraws on its own schedule.
        let ticker = Timer(timeInterval: 0.1, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick() }
        }
        RunLoop.main.add(ticker, forMode: .common)
        self.ticker = ticker
        resume()
    }

    private func resume() {
        guard !isFinished else { return }
        player.start()
        clock.resume(at: .now)
        tick()
        updateLive(force: true)
    }

    private func tick() {
        guard hasStarted, !steps.isEmpty, !isFinished else { return }
        let snapshot = clock.snapshot(at: .now)
        for cue in Cue.due(from: last, to: snapshot, steps: steps) {
            player.play(cue)
        }
        last = snapshot
        if snapshot.finished {
            isFinished = true
            ticker?.invalidate()
            live.end()
            UIApplication.shared.isIdleTimerDisabled = false
            // Let "Workout complete" finish, then give the music back.
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) { [weak self] in self?.player.stop() }
        } else {
            updateLive()
        }
    }

    /// Sends the Live Activity what it needs when the step changes, the clock jumps or pauses;
    /// between those moments iOS counts down by itself.
    private func updateLive(force: Bool = false) {
        guard hasStarted, !isFinished, !steps.isEmpty else { return }
        let now = Date.now
        let index = clock.stepIndex(at: now)
        let key = [index, clock.isRunning ? 1 : 0]
        guard force || key != liveKey else { return }
        liveKey = key

        let step = steps[index]
        let next = index + 1 < steps.count ? steps[index + 1] : nil
        let ends = now.addingTimeInterval(clock.remaining(at: now))
        var progress = "Round \(step.round)/\(workout.rounds)"
        if workout.cycles > 1 { progress += " · Cycle \(step.cycle)/\(workout.cycles)" }
        live.show(
            WorkoutActivity.ContentState(
                step: step.name,
                phase: step.phase.rawValue,
                starts: ends.addingTimeInterval(-Double(step.seconds)),
                ends: ends,
                pausedAt: clock.isRunning ? nil : now,
                next: next.map { "\($0.name) \(clockText($0.seconds))" },
                nextPhase: next?.phase.rawValue,
                progress: progress
            ),
            workout: workout.name
        )
    }
}
