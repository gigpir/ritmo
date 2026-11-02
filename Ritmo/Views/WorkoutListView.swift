import SwiftUI

/// The home screen: every saved workout, the last one used first (SPEC §5.1).
struct WorkoutListView: View {
    @Environment(WorkoutStore.self) private var store
    @State private var editing: Workout?
    @State private var running: Workout?
    @State private var showsSettings = false
    @State private var importMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(store.workouts) { workout in
                        Button { open(workout) } label: { WorkoutCard(workout: workout) }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("Edit", systemImage: "pencil") { editing = workout }
                                Button("Duplicate", systemImage: "plus.square.on.square") { store.duplicate(workout) }
                                ShareLink(
                                    item: SharedWorkouts(workouts: [workout], fileName: workout.name),
                                    preview: SharePreview(workout.name)
                                ) {
                                    Label("Share", systemImage: "square.and.arrow.up")
                                }
                                Button("Delete", systemImage: "trash", role: .destructive) { store.delete(workout) }
                            }
                    }
                    Text("Tap to open · hold for Edit, Duplicate, Delete")
                        .font(.subheadline)
                        .foregroundStyle(Theme.secondary)
                        .padding(.top, 6)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.background)
            .navigationTitle("Workouts")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("Settings", systemImage: "slider.horizontal.3") { showsSettings = true }
                    Button("New workout", systemImage: "plus") { editing = .blank }
                }
            }
        }
        .sheet(item: $editing) { workout in
            EditorView(workout: workout, isNew: !store.workouts.contains { $0.id == workout.id }) { store.save($0) }
        }
        .sheet(isPresented: $showsSettings) { SettingsView() }
        .fullScreenCover(item: $running) { TimerView(workout: $0) }
        // A .ritmo file opened from AirDrop, Messages, Mail or Files.
        .onOpenURL { importMessage = store.importFile(at: $0) }
        .alert(importMessage ?? "", isPresented: Binding { importMessage != nil } set: { if !$0 { importMessage = nil } }) {
            Button("OK") {}
        }
        #if DEBUG
        .task {
            // Screenshots from the terminal: `xcrun simctl launch … -openWorkout 0 [-autostart YES]`.
            if UserDefaults.standard.object(forKey: "openWorkout") != nil {
                let i = UserDefaults.standard.integer(forKey: "openWorkout")
                if store.workouts.indices.contains(i) { running = store.workouts[i] }
            }
        }
        #endif
    }

    /// Opens the workout full screen, ready to start.
    private func open(_ workout: Workout) {
        guard !workout.steps.isEmpty else { return }
        store.markUsed(workout)
        running = workout
    }
}

private struct WorkoutCard: View {
    let workout: Workout

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(workout.name)
                        .font(.condensed(30))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text(workout.summary)
                        .font(.body)
                        .foregroundStyle(Theme.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                Spacer(minLength: 0)
                Text(clockText(workout.duration))
                    .font(.condensed(34, weight: .bold))
                    .monospacedDigit()
            }
            StripView(steps: workout.steps, height: 10)
        }
        .foregroundStyle(Theme.text)
        .padding(16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 22))
        .contentShape(RoundedRectangle(cornerRadius: 22))
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens the workout")
    }
}
