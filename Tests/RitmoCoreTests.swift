import AVFoundation
import Foundation
import Testing
@testable import RitmoCore

struct StepTests {
    @Test func tabata() {
        let steps = Workout.samples[0].steps
        #expect(steps.count == 16)
        #expect(steps.first?.phase == .prepare)
        #expect(steps.last?.phase == .work) // the final Rest is dropped
        #expect(steps.map(\.round).suffix(2) == [7, 8]) // Rest of round 7, then the last Work
    }

    @Test func sampleDurations() {
        // Tabata 4:00, HIIT 60/15 39:25, HIIT 40/20 9:50, Boxing 47:10, circuit 12:10 (SPEC §3)
        #expect(Workout.samples.map(\.duration) == [240, 2365, 590, 2830, 730])
    }

    @Test func cyclesWithRestBetween() {
        // Prepare 0:10 · [Work 1:00, Rest 0:15] × 6 rounds · 5 cycles · 0:45 between cycles
        let steps = Workout.samples[1].steps
        #expect(steps.filter { $0.phase == .work }.count == 30)
        #expect(steps.filter { $0.phase == .recover }.map(\.seconds) == [45, 45, 45, 45])
        #expect(steps.last?.cycle == 5)
    }

    @Test func restBeforeRecoverIsDropped() {
        let steps = Workout.samples[4].steps
        #expect(steps.filter { $0.phase == .recover }.count == 2)
        for (a, b) in zip(steps, steps.dropFirst()) {
            #expect(!(a.phase == .rest && b.phase == .recover))
        }
        #expect(steps.last?.name == "Plank")
    }

    @Test func restStaysBetweenCyclesWithoutRecover() {
        let workout = Workout(name: "Two cycles", prepare: 0, intervals: [.work(20), .rest(10)], rounds: 2, cycles: 2)
        #expect(workout.steps.map(\.phase) == [.work, .rest, .work, .rest, .work, .rest, .work])
        #expect(workout.steps.map(\.cycle) == [1, 1, 1, 1, 2, 2, 2])
    }

    @Test func startsFollowEachOther() {
        let steps = Workout.samples[4].steps
        #expect(steps.first?.start == 0)
        for (a, b) in zip(steps, steps.dropFirst()) {
            #expect(b.start == a.end)
        }
    }

    @Test func summaries() {
        #expect(Workout.samples.map(\.summary) == [
            "8 rounds · 0:20 / 0:10",
            "6 rounds · 1:00 / 0:15 · 5 cycles",
            "10 rounds · 0:40 / 0:20",
            "12 rounds · 3:00 / 1:00",
            "6 intervals · 3 cycles",
        ])
    }

    @Test func clockTexts() {
        #expect(clockText(9) == "0:09")
        #expect(clockText(720) == "12:00")
        #expect(clockText(3750) == "1:02:30")
    }
}

struct SavingTests {
    @Test func savedWorkoutsRoundTrip() throws {
        let data = try JSONEncoder().encode(Workout.samples)
        #expect(try JSONDecoder().decode([Workout].self, from: data) == Workout.samples)
    }

    @Test func readsListsSavedBeforeTheRename() throws {
        let saved = """
        [{"id": "6F9619FF-8B86-D011-B42D-00C04FC964FF", "name": "Old", "prepare": 10, "rounds": 2,
          "sets": 3, "recover": 30,
          "intervals": [{"id": "7F9619FF-8B86-D011-B42D-00C04FC964FF", "name": "Work", "kind": "work", "seconds": 20}]}]
        """
        let workout = try #require(try JSONDecoder().decode([Workout].self, from: Data(saved.utf8)).first)
        #expect(workout.cycles == 3)
        #expect(workout.cycleRest == 30)
    }
}

struct SharingTests {
    @Test func fileRoundTrip() throws {
        let data = try WorkoutFile.encode(Workout.samples)
        #expect(try WorkoutFile.decode(data) == Workout.samples)
    }

    @Test func readsAHandWrittenFile() throws {
        let file = """
        {"format": "ritmo", "version": 1, "workouts": [
          {"name": "HIIT 60/15", "prepare": 10, "rounds": 6, "cycles": 5, "cycleRest": 45,
           "intervals": [{"kind": "work", "seconds": 60}, {"kind": "rest", "seconds": 15}]}]}
        """
        let workouts = try WorkoutFile.decode(Data(file.utf8))
        #expect(workouts.count == 1)
        #expect(workouts[0].intervals.map(\.name) == ["Work", "Rest"])
        #expect(workouts[0].duration == 2365) // 39:25
    }

    @Test func readsASingleWorkout() throws {
        let data = try JSONEncoder().encode(Workout.samples[0])
        #expect(try WorkoutFile.decode(data) == [Workout.samples[0]])
    }

    @Test func importSkipsWhatIsAlreadyThereAndUsesNewIds() {
        let mine = Array(Workout.samples.prefix(2))
        let incoming = [Workout.samples[1], Workout.samples[3], Workout.samples[3]]
        let result = WorkoutFile.merge(incoming, into: mine)
        #expect(result.added.map(\.name) == ["Boxing"])
        #expect(result.list.map(\.name) == ["Boxing", "Tabata", "HIIT 60/15"])
        #expect(result.added[0].id != Workout.samples[3].id)
        #expect(result.added[0].hasSameContent(as: Workout.samples[3]))
    }
}

struct ClockTests {
    let t0 = Date(timeIntervalSinceReferenceDate: 0)

    func tabataClock() -> WorkoutClock {
        var clock = WorkoutClock(steps: Workout.samples[0].steps)
        clock.resume(at: t0)
        return clock
    }

    @Test func countsDownRoundingUp() {
        let clock = tabataClock()
        #expect(clock.snapshot(at: t0) == TimerSnapshot(step: 0, shown: 10, finished: false))
        #expect(clock.snapshot(at: t0 + 10) == TimerSnapshot(step: 1, shown: 20, finished: false))
        #expect(clock.snapshot(at: t0 + 29.5).shown == 1)
        #expect(clock.isFinished(at: t0 + 240))
        #expect(!clock.isFinished(at: t0 + 239.9))
    }

    @Test func pauseAndResume() {
        var clock = tabataClock()
        clock.pause(at: t0 + 15)
        #expect(clock.snapshot(at: t0 + 100).shown == 15)
        clock.resume(at: t0 + 100)
        #expect(clock.snapshot(at: t0 + 105).shown == 10)
    }

    @Test func nextAndPrevious() {
        var clock = tabataClock()
        clock.next(at: t0 + 15) // from the first Work to the first Rest
        #expect(clock.elapsed(at: t0 + 15) == 30)
        clock.previous(at: t0 + 16) // 1 s into the Rest: back to the Work
        #expect(clock.elapsed(at: t0 + 16) == 10)
        clock.previous(at: t0 + 20) // 4 s into the Work: restart it
        #expect(clock.elapsed(at: t0 + 20) == 10)
    }

    @Test func nextOnLastStepFinishes() {
        var clock = tabataClock()
        clock.next(at: t0 + 235)
        #expect(clock.isFinished(at: t0 + 235))
    }
}

struct CueTests {
    @Test func tabataCues() {
        let steps = Workout.samples[0].steps
        var clock = WorkoutClock(steps: steps)
        let t0 = Date(timeIntervalSinceReferenceDate: 0)
        clock.resume(at: t0)

        var cues: [Cue] = []
        var last: TimerSnapshot?
        for tick in 0...2410 { // every 0.1 s, a little past the end
            let snapshot = clock.snapshot(at: t0 + Double(tick) / 10)
            cues += Cue.due(from: last, to: snapshot, steps: steps)
            last = snapshot
        }

        var expected = ["Prepare"]
        for _ in 0..<8 { expected += ["Work", "Rest"] }
        expected.removeLast()
        let names = cues.compactMap { cue -> String? in
            if case .announce(let name) = cue { return name }
            return nil
        }
        #expect(names == expected)
        #expect(cues.filter { $0 == .beep }.count == 16 * 3)
        #expect(cues.last == .finish)
    }

    @Test func restartAnnouncesAgain() {
        let steps = Workout.samples[0].steps
        let before = TimerSnapshot(step: 1, shown: 5, finished: false)
        let after = TimerSnapshot(step: 1, shown: 20, finished: false)
        #expect(Cue.due(from: before, to: after, steps: steps) == [.announce("Work")])
    }

    @Test func noBeepAtTheStartOfAShortStep() {
        let steps = Workout(name: "Short", prepare: 0, intervals: [.work(3)]).steps
        #expect(Cue.due(from: nil, to: TimerSnapshot(step: 0, shown: 3, finished: false), steps: steps) == [.announce("Work")])
    }
}

struct ToneTests {
    @Test func beepIsPlayableWAV() throws {
        let player = try AVAudioPlayer(data: Tone.beep(frequency: 880, seconds: 0.12))
        #expect(abs(player.duration - 0.12) < 0.01)
    }

    @Test func silenceIsPlayableWAV() throws {
        let player = try AVAudioPlayer(data: Tone.silence(seconds: 1))
        #expect(abs(player.duration - 1) < 0.01)
    }
}
