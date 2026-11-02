import Foundation

/// The `.ritmo` file for sharing workouts (SPEC §5.7): JSON with a format name and a
/// version, so a later Ritmo can still read the files written today.
struct WorkoutFile: Codable {
    var format: String? = "ritmo"
    var version: Int? = 1
    var workouts: [Workout]

    static func encode(_ workouts: [Workout]) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(WorkoutFile(workouts: workouts))
    }

    /// Reads a `.ritmo` file, or plain JSON with a list of workouts or a single one.
    static func decode(_ data: Data) throws -> [Workout] {
        let decoder = JSONDecoder()
        if let file = try? decoder.decode(WorkoutFile.self, from: data) { return file.workouts }
        if let list = try? decoder.decode([Workout].self, from: data) { return list }
        return [try decoder.decode(Workout.self, from: data)]
    }

    /// Adds the incoming workouts at the top of the list, under new ids,
    /// skipping any that is already there. Also returns the ones added.
    static func merge(_ incoming: [Workout], into list: [Workout]) -> (list: [Workout], added: [Workout]) {
        var added: [Workout] = []
        for workout in incoming
        where !list.contains(where: { $0.hasSameContent(as: workout) }) && !added.contains(where: { $0.hasSameContent(as: workout) }) {
            added.append(workout.withNewIdentity())
        }
        return (added + list, added)
    }
}
