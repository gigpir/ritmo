import Foundation

/// One step of a running workout.
struct Step: Hashable {
    /// `recover` is the rest between cycles.
    enum Phase: String { case prepare, work, rest, recover }

    var name: String
    var phase: Phase
    var seconds: Int
    var round: Int
    var cycle: Int
    /// Seconds from the start of the workout.
    var start: Int

    var end: Int { start + seconds }
}

extension Workout {
    /// The workout as a flat list of steps, with the last-Rest rule applied.
    var steps: [Step] {
        var raw: [Step] = []
        func add(_ name: String, _ phase: Step.Phase, _ seconds: Int, round: Int, cycle: Int) {
            guard seconds > 0 else { return }
            raw.append(Step(name: name, phase: phase, seconds: seconds, round: round, cycle: cycle, start: 0))
        }

        let roundCount = max(1, rounds), cycleCount = max(1, cycles)
        add("Prepare", .prepare, prepare, round: 1, cycle: 1)
        for cycle in 1...cycleCount {
            for round in 1...roundCount {
                for interval in intervals {
                    add(interval.name, interval.kind == .work ? .work : .rest, interval.seconds, round: round, cycle: cycle)
                }
            }
            if cycle < cycleCount {
                add("Recover", .recover, cycleRest, round: roundCount, cycle: cycle)
            }
        }

        // A Rest right before a Recover or the end of the workout is dropped.
        var kept: [Step] = []
        var boundaryAhead = true
        for step in raw.reversed() {
            if step.phase == .rest && boundaryAhead { continue }
            kept.append(step)
            boundaryAhead = step.phase == .recover
        }

        var start = 0
        return kept.reversed().map { step in
            var step = step
            step.start = start
            start += step.seconds
            return step
        }
    }

    var duration: Int { steps.last?.end ?? 0 }
}
