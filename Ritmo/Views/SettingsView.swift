import SwiftUI

/// Three switches, all on by default, and the workouts to share or import.
struct SettingsView: View {
    @AppStorage("voice") private var voice = true
    @AppStorage("vibration") private var vibration = true
    @AppStorage("duckMusic") private var duckMusic = true
    @Environment(WorkoutStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var importing = false
    @State private var importMessage: String?

    /// "1.0 (20261103.1045)": install.sh and ipa.sh stamp the build with its date and time.
    private static let version: String = {
        let info = Bundle.main.infoDictionary ?? [:]
        return "\(info["CFBundleShortVersionString"] as? String ?? "?") (\(info["CFBundleVersion"] as? String ?? "?"))"
    }()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Voice", isOn: $voice)
                } footer: {
                    Text("Reads each phase aloud: Prepare, Work, Rest, Recover. When off, a long beep marks the change.")
                }
                Section {
                    Toggle("Vibration", isOn: $vibration)
                }
                Section {
                    Toggle("Lower music during cues", isOn: $duckMusic)
                } footer: {
                    Text("Music from other apps gets quieter during beeps and announcements.")
                }
                Section {
                    ShareLink(
                        item: SharedWorkouts(workouts: store.workouts, fileName: "Ritmo workouts"),
                        preview: SharePreview("Ritmo workouts")
                    ) {
                        Label("Share all workouts", systemImage: "square.and.arrow.up")
                    }
                    Button("Import from Files", systemImage: "square.and.arrow.down") { importing = true }
                } header: {
                    Text("Workouts")
                } footer: {
                    Text("Workouts travel as .ritmo files: share them, keep a copy in Files, and open one on an iPhone with Ritmo to add its workouts.")
                }
                Section {} footer: {
                    Text("Ritmo \(Self.version)")
                        .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.ritmoWorkouts, .json]) { result in
                if case .success(let url) = result { importMessage = store.importFile(at: url) }
            }
            .alert(importMessage ?? "", isPresented: Binding { importMessage != nil } set: { if !$0 { importMessage = nil } }) {
                Button("OK") {}
            }
        }
    }
}
