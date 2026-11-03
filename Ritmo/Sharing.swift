import CoreTransferable
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// A `.ritmo` file with one or more workouts, declared in Config/Info.plist.
    static let ritmoWorkouts = UTType(exportedAs: "io.github.gigpir.ritmo.workouts")
}

/// Workouts as a `.ritmo` file for the share sheet: AirDrop, Messages, Mail, Files, chat apps.
/// It hands over a real file: given only its data, WhatsApp and Telegram wait for one forever.
struct SharedWorkouts: Transferable {
    let workouts: [Workout]
    let fileName: String

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .ritmoWorkouts) { shared in
            SentTransferredFile(try shared.writeFile())
        }
    }

    /// Writes the file under a name a file can have ("HIIT 60/15" becomes "HIIT 60-15.ritmo"), in a temporary folder
    /// of its own: the share sheet and the receiving app can ask for it at the same time.
    private func writeFile() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let name = fileName.replacingOccurrences(of: "/", with: "-").replacingOccurrences(of: ":", with: "-")
        let url = folder.appending(path: "\(name).ritmo")
        try WorkoutFile.encode(workouts).write(to: url)
        return url
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
