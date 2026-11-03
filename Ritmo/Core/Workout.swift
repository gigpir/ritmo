import Foundation

/// A saved workout: an optional Prepare, then the intervals repeated
/// `rounds` times, and that block repeated `cycles` times with an optional
/// rest between cycles, announced as "Recover".
struct Workout: Hashable, Identifiable {
    var id = UUID()
    var name: String
    var prepare: Int = 10
    var intervals: [Interval]
    var rounds: Int = 1
    var cycles: Int = 1
    /// Seconds of rest between one cycle and the next.
    var cycleRest: Int = 0
}

struct Interval: Hashable, Identifiable {
    enum Kind: String, Codable {
        case work, rest

        var defaultName: String { self == .work ? "Work" : "Rest" }
    }

    var id = UUID()
    var name: String
    var kind: Kind
    var seconds: Int

    static func work(_ seconds: Int, _ name: String = "Work") -> Interval {
        Interval(name: name, kind: .work, seconds: seconds)
    }

    static func rest(_ seconds: Int) -> Interval {
        Interval(name: "Rest", kind: .rest, seconds: seconds)
    }
}

extension Workout: Codable {
    private enum CodingKeys: String, CodingKey {
        case id, name, prepare, intervals, rounds, cycles, cycleRest
        /// The names of `cycles` and `cycleRest` until November 2026, still read from saved lists.
        case sets, recover
    }

    /// Missing ids are made up, so a `.ritmo` file can be written by hand.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        name = try c.decode(String.self, forKey: .name)
        prepare = try c.decodeIfPresent(Int.self, forKey: .prepare) ?? 0
        intervals = try c.decode([Interval].self, forKey: .intervals)
        rounds = try c.decodeIfPresent(Int.self, forKey: .rounds) ?? 1
        cycles = try c.decodeIfPresent(Int.self, forKey: .cycles) ?? c.decodeIfPresent(Int.self, forKey: .sets) ?? 1
        cycleRest = try c.decodeIfPresent(Int.self, forKey: .cycleRest) ?? c.decodeIfPresent(Int.self, forKey: .recover) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(prepare, forKey: .prepare)
        try c.encode(intervals, forKey: .intervals)
        try c.encode(rounds, forKey: .rounds)
        try c.encode(cycles, forKey: .cycles)
        try c.encode(cycleRest, forKey: .cycleRest)
    }
}

extension Interval: Codable {
    private enum CodingKeys: String, CodingKey { case id, name, kind, seconds }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = try c.decode(Kind.self, forKey: .kind)
        seconds = try c.decode(Int.self, forKey: .seconds)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? kind.defaultName
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
    }
}

extension Workout {
    /// What a new workout starts from: a classic Tabata without a name.
    static var blank: Workout {
        Workout(name: "", intervals: [.work(20), .rest(10)], rounds: 8)
    }

    /// The workouts found on first launch.
    static let samples: [Workout] = [
        Workout(name: "Tabata", intervals: [.work(20), .rest(10)], rounds: 8),
        Workout(name: "HIIT 60/15", intervals: [.work(60), .rest(15)], rounds: 6, cycles: 5, cycleRest: 45),
        Workout(name: "HIIT 40/20", intervals: [.work(40), .rest(20)], rounds: 10),
        Workout(name: "Boxing", intervals: [.work(180), .rest(60)], rounds: 12),
        Workout(
            name: "Full-body circuit",
            intervals: [.work(40, "Squats"), .rest(20), .work(40, "Push-ups"), .rest(20), .work(40, "Plank"), .rest(20)],
            cycles: 3,
            cycleRest: 120
        ),
    ]

    /// One line for the list, e.g. "8 rounds · 0:20 / 0:10" or "6 intervals · 3 cycles".
    var summary: String {
        var parts: [String] = []
        if rounds > 1 { parts.append("\(rounds) rounds") }
        if intervals.count == 2, intervals[0].kind == .work, intervals[1].kind == .rest {
            parts.append("\(clockText(intervals[0].seconds)) / \(clockText(intervals[1].seconds))")
        } else {
            parts.append(intervals.count == 1 ? "1 interval" : "\(intervals.count) intervals")
        }
        if cycles > 1 { parts.append("\(cycles) cycles") }
        return parts.joined(separator: " · ")
    }

    /// The same workout under new ids, for a copy or an import.
    func withNewIdentity() -> Workout {
        var copy = self
        copy.id = UUID()
        copy.intervals = intervals.map { interval in
            var interval = interval
            interval.id = UUID()
            return interval
        }
        return copy
    }

    /// Equal apart from the ids, so an import can skip what is already in the list.
    func hasSameContent(as other: Workout) -> Bool {
        func content(_ w: Workout) -> [String] {
            [w.name, "\(w.prepare)", "\(w.rounds)", "\(w.cycles)", "\(w.cycleRest)"]
                + w.intervals.map { "\($0.name)|\($0.kind.rawValue)|\($0.seconds)" }
        }
        return content(self) == content(other)
    }
}

/// Times as the app shows them: "0:09", "12:00", "1:02:30".
func clockText(_ seconds: Int) -> String {
    let h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
}
