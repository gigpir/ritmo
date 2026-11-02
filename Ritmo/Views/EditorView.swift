import SwiftUI

/// One workout as a single form (SPEC §5.2).
struct EditorView: View {
    @State private var workout: Workout
    let isNew: Bool
    let onSave: (Workout) -> Void
    @Environment(\.dismiss) private var dismiss

    init(workout: Workout, isNew: Bool, onSave: @escaping (Workout) -> Void) {
        _workout = State(initialValue: workout)
        self.isNew = isNew
        self.onSave = onSave
    }

    private var canSave: Bool {
        !workout.name.trimmingCharacters(in: .whitespaces).isEmpty && !workout.steps.isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section { preview }
                Section {
                    TextField("Name", text: $workout.name)
                }
                Section {
                    DurationRow(title: "Prepare", color: Step.Phase.prepare.bright, seconds: $workout.prepare, allowsNone: true)
                }
                Section("Intervals") {
                    ForEach($workout.intervals) { $interval in
                        NavigationLink {
                            IntervalEditor(interval: $interval)
                        } label: {
                            HStack(spacing: 12) {
                                Circle().fill(interval.kind.color).frame(width: 12, height: 12)
                                Text(interval.name.isEmpty ? interval.kind.defaultName : interval.name)
                                Spacer()
                                Text(clockText(interval.seconds))
                                    .font(.condensed(20))
                                    .monospacedDigit()
                            }
                        }
                    }
                    .onMove { workout.intervals.move(fromOffsets: $0, toOffset: $1) }
                    .onDelete { workout.intervals.remove(atOffsets: $0) }
                    Button("Add interval", systemImage: "plus", action: addInterval)
                    Stepper(value: $workout.rounds, in: 1...99) {
                        countLabel("Rounds", workout.rounds)
                    }
                }
                Section {
                    Stepper(value: $workout.cycles, in: 1...20) {
                        countLabel("Cycles", workout.cycles)
                    }
                    DurationRow(title: "Rest between cycles", color: Step.Phase.recover.bright, seconds: $workout.cycleRest, allowsNone: true)
                        .disabled(workout.cycles == 1)
                        .opacity(workout.cycles == 1 ? 0.4 : 1)
                } header: {
                    Text("Cycles")
                } footer: {
                    Text("A cycle is all the rounds above. The rest between cycles is announced as \"Recover\".")
                }
            }
            .navigationTitle(isNew ? "New workout" : "Edit workout")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(workout)
                        dismiss()
                    }
                    .disabled(!canSave)
                }
            }
        }
    }

    private var preview: some View {
        let steps = workout.steps
        let work = steps.filter { $0.phase == .work }.reduce(0) { $0 + $1.seconds }
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("TOTAL")
                    .font(.footnote.weight(.semibold))
                    .tracking(1)
                    .foregroundStyle(Theme.secondary)
                Spacer()
                Text(clockText(workout.duration))
                    .font(.condensed(34))
                    .monospacedDigit()
            }
            StripView(steps: steps, height: 14)
            Text("\(steps.count) steps · \(clockText(work)) of work")
                .font(.footnote)
                .foregroundStyle(Theme.secondary)
        }
        .padding(.vertical, 4)
    }

    private func countLabel(_ title: String, _ value: Int) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text("\(value)")
                .font(.condensed(22))
                .monospacedDigit()
        }
    }

    /// A Rest after a Work, a Work after a Rest.
    private func addInterval() {
        let kind: Interval.Kind = workout.intervals.last?.kind == .work ? .rest : .work
        workout.intervals.append(Interval(name: kind.defaultName, kind: kind, seconds: kind == .work ? 20 : 10))
    }
}

private struct IntervalEditor: View {
    @Binding var interval: Interval

    var body: some View {
        Form {
            Section {
                TextField("Name", text: $interval.name)
            }
            Section {
                Picker("Type", selection: $interval.kind) {
                    Text("Work").tag(Interval.Kind.work)
                    Text("Rest").tag(Interval.Kind.rest)
                }
                .pickerStyle(.segmented)
                DurationRow(title: "Duration", color: interval.kind.color, seconds: $interval.seconds, isOpen: true)
            }
        }
        .navigationTitle(interval.name.isEmpty ? "Interval" : interval.name)
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: interval.kind) { old, new in
            if interval.name == old.defaultName { interval.name = new.defaultName }
        }
    }
}

/// A duration such as "0:20" that opens minute and second wheels when tapped.
struct DurationRow: View {
    let title: String
    let color: Color
    @Binding var seconds: Int
    var allowsNone = false
    @State var isOpen = false

    var body: some View {
        Button {
            withAnimation { isOpen.toggle() }
        } label: {
            HStack(spacing: 12) {
                Circle().fill(color).frame(width: 12, height: 12)
                Text(title).foregroundStyle(Theme.text)
                Spacer()
                if seconds == 0 {
                    Text("None").foregroundStyle(Theme.secondary)
                } else {
                    Text(clockText(seconds))
                        .font(.condensed(20))
                        .monospacedDigit()
                        .foregroundStyle(isOpen ? color : Theme.text)
                }
            }
        }
        if isOpen {
            HStack(spacing: 0) {
                Picker("Minutes", selection: minutes) {
                    ForEach(0..<100, id: \.self) { Text("\($0) min").tag($0) }
                }
                .frame(maxWidth: .infinity)
                .clipped()
                Picker("Seconds", selection: secondsPart) {
                    ForEach(0..<60, id: \.self) { Text("\($0) s").tag($0) }
                }
                .frame(maxWidth: .infinity)
                .clipped()
            }
            .pickerStyle(.wheel)
            .frame(height: 160)
        }
    }

    private var minutes: Binding<Int> {
        Binding { seconds / 60 } set: { seconds = clamped($0 * 60 + seconds % 60) }
    }

    private var secondsPart: Binding<Int> {
        Binding { seconds % 60 } set: { seconds = clamped(seconds / 60 * 60 + $0) }
    }

    private func clamped(_ value: Int) -> Int { allowsNone ? value : max(1, value) }
}
