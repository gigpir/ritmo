import Foundation
import Observation

/// Every workout in list order, saved to one JSON file.
@Observable final class WorkoutStore {
    private(set) var workouts: [Workout]
    private let url: URL

    init(url: URL = URL.documentsDirectory.appending(path: "workouts.json")) {
        self.url = url
        if let data = try? Data(contentsOf: url),
           let saved = try? JSONDecoder().decode([Workout].self, from: data) {
            workouts = saved
        } else {
            workouts = Workout.samples
        }
    }

    /// Updates a workout, or adds it at the top if it is new.
    func save(_ workout: Workout) {
        if let i = workouts.firstIndex(where: { $0.id == workout.id }) {
            workouts[i] = workout
        } else {
            workouts.insert(workout, at: 0)
        }
        persist()
    }

    func duplicate(_ workout: Workout) {
        var copy = workout.withNewIdentity()
        copy.name += " copy"
        let i = workouts.firstIndex(where: { $0.id == workout.id }).map { $0 + 1 } ?? 0
        workouts.insert(copy, at: i)
        persist()
    }

    /// Adds shared workouts at the top, skipping those already in the list; returns the ones added.
    func add(_ incoming: [Workout]) -> [Workout] {
        let merged = WorkoutFile.merge(incoming, into: workouts)
        if !merged.added.isEmpty {
            workouts = merged.list
            persist()
        }
        return merged.added
    }

    func delete(_ workout: Workout) {
        workouts.removeAll { $0.id == workout.id }
        persist()
    }

    /// The workout just started moves to the top of the list.
    func markUsed(_ workout: Workout) {
        guard let i = workouts.firstIndex(where: { $0.id == workout.id }), i > 0 else { return }
        workouts.insert(workouts.remove(at: i), at: 0)
        persist()
    }

    private func persist() {
        do {
            try JSONEncoder().encode(workouts).write(to: url, options: .atomic)
        } catch {
            print("Could not save workouts: \(error)")
        }
    }
}
