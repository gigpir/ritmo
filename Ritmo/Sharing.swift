import CoreTransferable
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// A `.ritmo` file with one or more workouts, declared in Config/Info.plist.
    static let ritmoWorkouts = UTType(exportedAs: "io.github.gigpir.ritmo.workouts")
}

/// Workouts as a `.ritmo` file for the share sheet: AirDrop, Messages, Mail, Files.
struct SharedWorkouts: Transferable {
    let workouts: [Workout]
    let fileName: String

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .ritmoWorkouts) { shared in
            try WorkoutFile.encode(shared.workouts)
        }
        .suggestedFileName { "\($0.fileName).ritmo" }
    }
}

extension WorkoutStore {
    /// Adds the workouts of a `.ritmo` file opened on this iPhone, and says what happened.
    func importFile(at url: URL) -> String {
        let scoped = url.startAccessingSecurityScopedResource()
        defer {
            if scoped { url.stopAccessingSecurityScopedResource() }
            // iOS hands over a copy in Documents/Inbox: once read, it isn't needed.
            if url.path.contains("/Documents/Inbox/") { try? FileManager.default.removeItem(at: url) }
        }
        guard let data = try? Data(contentsOf: url),
              let incoming = try? WorkoutFile.decode(data), !incoming.isEmpty else {
            return "This file doesn't contain Ritmo workouts."
        }
        let added = add(incoming)
        switch added.count {
        case 0: return incoming.count == 1 ? "\(incoming[0].name) is already in your list." : "These workouts are already in your list."
        case 1: return "Added \(added[0].name)."
        default: return "Added \(added.count) workouts."
        }
    }
}
