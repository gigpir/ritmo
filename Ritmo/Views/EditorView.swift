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
            ScrollViewReader { proxy in
                Form {
                    Section { preview }
                    Section {
                        TextField("Name", text: $workout.name)
                    }
                    Section {
                        DurationRow(title: "Prepare", color: Step.Phase.prepare.bright, seconds: $workout.prepare, allowsNone: true)
                    }
                    Section {
                        ForEach(workout.intervals) { interval in
                            IntervalRow(interval: binding(for: interval.id)) {
                                workout.intervals.removeAll { $0.id == interval.id }
                            }
                        }
                        .onMove { workout.intervals.move(fromOffsets: $0, toOffset: $1) }
                        .onDelete { workout.intervals.remove(atOffsets: $0) }
                        Button("Add interval", systemImage: "plus", action: addInterval)
                        Stepper(value: $workout.rounds, in: 1...99) {
                            countLabel("Rounds", workout.rounds)
                        }
                    } header: {
                        Text("Intervals")
                    } footer: {
                        Text("Tap an interval to set its time. Hold it to rename it, switch between Work and Rest, or delete it.")
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
                .environment(\.editorScroll, proxy)
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

    /// The interval with this id, looked up on every read and write,
    /// so an edit can only ever reach that interval.
    private func binding(for id: Interval.ID) -> Binding<Interval> {
        Binding {
            workout.intervals.first { $0.id == id } ?? .work(0)
        } set: { interval in
            if let i = workout.intervals.firstIndex(where: { $0.id == id }) { workout.intervals[i] = interval }
        }
    }

    /// A Rest after a Work, a Work after a Rest.
    private func addInterval() {
        let kind: Interval.Kind = workout.intervals.last?.kind == .work ? .rest : .work
        workout.intervals.append(Interval(name: kind.defaultName, kind: kind, seconds: kind == .work ? 20 : 10))
    }
}

extension EnvironmentValues {
    /// The editor's form, which a row scrolls to show its wheels when they open.
    @Entry var editorScroll: ScrollViewProxy? = nil
}

private extension ScrollViewProxy {
    /// Scrolls the least it can to show the view with this id. Rows call it from onChange,
    /// while the form updates: their opened wheels are one of its rows only after that.
    func reveal(_ id: String) {
        DispatchQueue.main.async { withAnimation { scrollTo(id) } }
    }
}

/// An interval edited in place: a tap opens its wheels, holding it offers the rest.
private struct IntervalRow: View {
    @Binding var interval: Interval
    let delete: () -> Void
    @State private var isOpen = false
    @State private var isRenaming = false
    @State private var newName = ""
    @Environment(\.editorScroll) private var editorScroll

    var body: some View {
        Button {
            withAnimation { isOpen.toggle() }
        } label: {
            DurationLabel(
                title: interval.name.isEmpty ? interval.kind.defaultName : interval.name,
                color: interval.kind.color,
                seconds: interval.seconds,
                isOpen: isOpen
            )
        }
        .contextMenu {
            Button("Rename", systemImage: "pencil") {
                newName = interval.name
                isRenaming = true
            }
            Button(interval.kind == .work ? "Make it Rest" : "Make it Work", systemImage: "arrow.left.arrow.right", action: switchKind)
            Button("Delete", systemImage: "trash", role: .destructive, action: delete)
        }
        .alert("Rename interval", isPresented: $isRenaming) {
            TextField(interval.kind.defaultName, text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Save") { interval.name = newName.trimmingCharacters(in: .whitespaces) }
        }
        .onChange(of: isOpen) { if isOpen { editorScroll?.reveal(wheelsID) } }
        if isOpen {
            DurationWheels(seconds: $interval.seconds, allowsNone: false)
                .frame(maxWidth: .infinity)
                .frame(height: 160)
                .id(wheelsID)
        }
    }

    private var wheelsID: String { "\(interval.id) wheels" }

    /// A Work becomes a Rest and the other way round; a default name follows the kind.
    private func switchKind() {
        let old = interval.kind
        interval.kind = old == .work ? .rest : .work
        if interval.name == old.defaultName { interval.name = interval.kind.defaultName }
    }
}

/// A duration such as "0:20" that opens minute and second wheels when tapped.
struct DurationRow: View {
    let title: String
    let color: Color
    @Binding var seconds: Int
    var allowsNone = false
    @State private var isOpen = false
    @Environment(\.editorScroll) private var editorScroll

    var body: some View {
        Button {
            withAnimation { isOpen.toggle() }
        } label: {
            DurationLabel(title: title, color: color, seconds: seconds, isOpen: isOpen)
        }
        .onChange(of: isOpen) { if isOpen { editorScroll?.reveal(wheelsID) } }
        if isOpen {
            DurationWheels(seconds: $seconds, allowsNone: allowsNone)
                .frame(maxWidth: .infinity)
                .frame(height: 160)
                .id(wheelsID)
        }
    }

    private var wheelsID: String { "\(title) wheels" }
}

/// A dot in the phase colour, a name and a duration, or "None".
private struct DurationLabel: View {
    let title: String
    let color: Color
    let seconds: Int
    let isOpen: Bool

    var body: some View {
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
}

/// Minute and second wheels in one UIKit picker, like the Clock app's timer:
/// one control, so a swipe can't move the wrong wheel, and it writes only when the user turns it.
private struct DurationWheels: UIViewRepresentable {
    @Binding var seconds: Int
    let allowsNone: Bool

    func makeUIView(context: Context) -> UIPickerView {
        let picker = UIPickerView()
        picker.dataSource = context.coordinator
        picker.delegate = context.coordinator
        picker.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return picker
    }

    func updateUIView(_ picker: UIPickerView, context: Context) {
        context.coordinator.wheels = self
        let rows = [seconds / 60, seconds % 60]
        for (component, row) in rows.enumerated() where picker.selectedRow(inComponent: component) != row {
            picker.selectRow(row, inComponent: component, animated: false)
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIPickerViewDataSource, UIPickerViewDelegate {
        var wheels: DurationWheels

        init(_ wheels: DurationWheels) { self.wheels = wheels }

        func numberOfComponents(in pickerView: UIPickerView) -> Int { 2 }

        func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
            component == 0 ? 100 : 60
        }

        func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
            component == 0 ? "\(row) min" : "\(row) s"
        }

        func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
            let total = pickerView.selectedRow(inComponent: 0) * 60 + pickerView.selectedRow(inComponent: 1)
            wheels.seconds = wheels.allowsNone ? total : max(1, total)
        }
    }
}
