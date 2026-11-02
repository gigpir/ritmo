import SwiftUI

/// The workout screen: Marea in portrait, Tabellone in landscape (SPEC §5.3–5.4).
/// It opens ready on the first step; Play starts it.
struct TimerView: View {
    @State private var model: TimerModel
    @Environment(\.dismiss) private var dismiss

    init(workout: Workout) {
        _model = State(initialValue: TimerModel(workout: workout))
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !model.clock.isRunning)) { context in
            let face = TimerFace(model: model, now: context.date)
            GeometryReader { geo in
                if geo.size.width > geo.size.height {
                    ScoreboardLayout(face: face, model: model, end: end)
                } else {
                    TideLayout(face: face, model: model, end: end)
                }
            }
        }
        .overlay {
            if model.isFinished { FinishedView(workout: model.workout, done: end) }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onDisappear { model.stop() }
        #if DEBUG
        .task {
            if UserDefaults.standard.bool(forKey: "autostart") { model.togglePause() }
        }
        #endif
    }

    private func end() {
        model.stop()
        dismiss()
    }
}

/// What the timer shows at one moment.
private struct TimerFace {
    let step: Step
    let next: Step?
    let index: Int
    /// Seconds left in the step, rounded up.
    let shown: Int
    /// The part of the step still to go, from 1 down to 0: the height of the tide.
    let level: CGFloat
    /// Seconds left in the whole workout.
    let left: Int
    let hasStarted: Bool
    let isRunning: Bool

    @MainActor init(model: TimerModel, now: Date) {
        let clock = model.clock
        index = clock.stepIndex(at: now)
        step = model.steps[index]
        next = index + 1 < model.steps.count ? model.steps[index + 1] : nil
        let remaining = clock.remaining(at: now)
        shown = Int(remaining.rounded(.up))
        level = CGFloat(max(0, min(1, remaining / Double(step.seconds))))
        left = Int((clock.duration - clock.elapsed(at: now)).rounded(.up))
        hasStarted = model.hasStarted
        isRunning = clock.isRunning
    }

    /// Started, then paused: the countdown dims.
    var isPaused: Bool { hasStarted && !isRunning }
    /// In the last 3 seconds the countdown becomes a single digit.
    var isFinalCountdown: Bool { (1...3).contains(shown) && step.seconds > 3 }
    var digits: String { isFinalCountdown ? "\(shown)" : clockText(shown) }
    var title: String { step.name.uppercased() }
}

// MARK: - Portrait: Marea

private struct TideLayout: View {
    let face: TimerFace
    let model: TimerModel
    let end: () -> Void

    var body: some View {
        let phase = face.step.phase
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: -28) {
                ZStack {
                    GeometryReader { geo in
                        let fill = geo.size.height * face.level
                        ZStack(alignment: .bottom) {
                            phase.dark
                            phase.bright.frame(height: fill)
                            countdown(color: phase.bright)
                            countdown(color: phase.ink)
                                .mask(alignment: .bottom) { Rectangle().frame(height: fill) }
                                .accessibilityHidden(true)
                        }
                    }
                    .ignoresSafeArea(edges: .top)
                    VStack {
                        HStack(spacing: 12) {
                            EndButton(end: end, requiresHold: face.hasStarted)
                            Spacer(minLength: 0)
                            Text("\(model.workout.name) · \(clockText(face.left)) left")
                                .font(.system(size: 18, weight: .semibold))
                                .monospacedDigit()
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 12)
                                .background(.black.opacity(0.6), in: Capsule())
                            Spacer(minLength: 0)
                            MuteButton(model: model)
                        }
                        Spacer()
                        NextBanner(face: face)
                            .padding(.bottom, 52)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
                ControlPanel(face: face, model: model)
            }
        }
        .foregroundStyle(Theme.text)
    }

    private func countdown(color: Color) -> some View {
        VStack(spacing: 0) {
            Text(face.title)
                .font(.condensed(60))
                .tracking(3)
            Text(face.digits)
                .font(.digits(face.isFinalCountdown ? 320 : 240))
                .monospacedDigit()
        }
        .lineLimit(1)
        .minimumScaleFactor(0.3)
        .foregroundStyle(color)
        .opacity(face.isPaused ? 0.55 : 1)
        .padding(.horizontal, 16)
        .padding(.top, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct NextBanner: View {
    let face: TimerFace

    var body: some View {
        if let next = face.next {
            if face.isFinalCountdown {
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    Text("NEXT")
                        .font(.system(size: 15, weight: .bold))
                        .tracking(1.5)
                    Text(next.name.uppercased())
                        .font(.condensed(36))
                        .tracking(1)
                    Spacer()
                    Text(clockText(next.seconds))
                        .font(.condensed(36))
                        .monospacedDigit()
                }
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .foregroundStyle(next.phase.ink)
                .padding(.horizontal, 22)
                .frame(height: 76)
                .background(next.phase.bright, in: RoundedRectangle(cornerRadius: 20))
            } else {
                HStack(spacing: 12) {
                    Circle().fill(next.phase.bright).frame(width: 14, height: 14)
                    Text("Next: \(next.name) \(clockText(next.seconds))")
                        .monospacedDigit()
                }
                .font(.system(size: 22, weight: .semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.horizontal, 22)
                .padding(.vertical, 13)
                .background(Theme.background.opacity(0.88), in: Capsule())
            }
        }
    }
}

private struct ControlPanel: View {
    let face: TimerFace
    let model: TimerModel

    var body: some View {
        VStack(spacing: 22) {
            StripView(steps: model.steps, current: face.index, height: 14, spacing: 3)
            HStack(spacing: 0) {
                Counter(value: "\(face.step.round)/\(model.workout.rounds)", caption: "round")
                Spacer(minLength: 4)
                StepButtons(face: face, model: model, small: 60, large: 88)
                Spacer(minLength: 4)
                Counter(value: "\(face.step.cycle)/\(model.workout.cycles)", caption: "cycle")
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 22)
        .padding(.bottom, 8)
        .background(Theme.background, in: UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28))
    }
}

private struct Counter: View {
    let value: String
    let caption: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.condensed(38))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            Text(caption)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Theme.secondary)
        }
        .frame(minWidth: 52, maxWidth: 72)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Landscape: Tabellone

private struct ScoreboardLayout: View {
    let face: TimerFace
    let model: TimerModel
    let end: () -> Void

    var body: some View {
        let phase = face.step.phase
        HStack(spacing: 0) {
            GeometryReader { geo in
                VStack(alignment: .leading, spacing: 0) {
                    Text(face.title)
                        .font(.condensed(44))
                        .tracking(4)
                    // As tall as the screen allows, leaving room for the name and the bar.
                    Text(face.digits)
                        .font(.digits(min(300, geo.size.height * 0.56)))
                        .monospacedDigit()
                    GeometryReader { bar in
                        ZStack(alignment: .leading) {
                            Capsule().fill(phase.dark)
                            Capsule().fill(phase.bright).frame(width: bar.size.width * face.level)
                        }
                    }
                    .frame(height: 10)
                    .padding(.top, 16)
                    .accessibilityHidden(true)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.3)
                .foregroundStyle(phase.bright)
                .opacity(face.isPaused ? 0.55 : 1)
                .padding(.horizontal, 32)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }

            Rectangle().fill(Theme.divider).frame(width: 2).ignoresSafeArea()

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    EndButton(end: end, requiresHold: face.hasStarted, background: Theme.divider)
                    Spacer()
                    MuteButton(model: model, background: Theme.divider)
                }
                Spacer(minLength: 8)
                HStack(alignment: .top) {
                    Stat(label: "ROUND") { fraction(face.step.round, model.workout.rounds) }
                    Spacer()
                    Stat(label: "CYCLE") { fraction(face.step.cycle, model.workout.cycles) }
                }
                Spacer(minLength: 8)
                Stat(label: "NEXT") {
                    Text(face.next.map { "\($0.name.uppercased()) \(clockText($0.seconds))" } ?? "END")
                        .font(.condensed(32))
                        .foregroundStyle(face.next?.phase.bright ?? Theme.text)
                }
                Spacer(minLength: 8)
                Stat(label: "TIME LEFT") {
                    Text(clockText(face.left))
                        .font(.condensed(32))
                        .monospacedDigit()
                }
                Spacer(minLength: 8)
                StepButtons(face: face, model: model, small: 54, large: 70)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .padding(.horizontal, 22)
            .padding(.vertical, 14)
            .frame(width: 270)
        }
        .foregroundStyle(Theme.text)
        .background(Color.black.ignoresSafeArea())
    }

    private func fraction(_ value: Int, _ total: Int) -> some View {
        (Text("\(value)") + Text("/\(total)").foregroundColor(Theme.secondary))
            .font(.condensed(52))
            .monospacedDigit()
    }
}

private struct Stat<Value: View>: View {
    let label: String
    @ViewBuilder let value: Value

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 14, weight: .bold))
                .tracking(1.6)
                .foregroundStyle(Theme.secondary)
            value
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Controls shared by both layouts

private struct StepButtons: View {
    let face: TimerFace
    let model: TimerModel
    let small: CGFloat
    let large: CGFloat

    var body: some View {
        HStack(spacing: 14) {
            Button(action: model.previous) {
                RoundIcon(systemImage: "backward.end.fill", size: small, background: Theme.control)
            }
            .accessibilityLabel("Previous step")
            Button(action: model.togglePause) {
                RoundIcon(
                    systemImage: face.isRunning ? "pause.fill" : "play.fill",
                    size: large,
                    background: Theme.text,
                    foreground: Theme.background
                )
            }
            .accessibilityLabel(!face.hasStarted ? "Start" : face.isRunning ? "Pause" : "Resume")
            Button(action: model.next) {
                RoundIcon(systemImage: "forward.end.fill", size: small, background: Theme.control)
            }
            .accessibilityLabel("Next step")
        }
        .buttonStyle(.plain)
    }
}

/// The X closes the screen. Once the workout has started it has to be held,
/// so a sweaty hand can't end it by mistake.
private struct EndButton: View {
    let end: () -> Void
    let requiresHold: Bool
    var background: Color = .black.opacity(0.6)
    @State private var showsHint = false

    var body: some View {
        RoundIcon(systemImage: "xmark", size: 52, background: background)
            .overlay(alignment: .topLeading) {
                if showsHint {
                    Text("Hold to end")
                        .font(.system(size: 15, weight: .semibold))
                        .fixedSize()
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .foregroundStyle(Theme.background)
                        .background(Theme.text, in: Capsule())
                        .offset(y: 60)
                        .transition(.opacity)
                }
            }
            .onTapGesture {
                guard requiresHold else { return end() }
                withAnimation { showsHint = true }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    withAnimation { showsHint = false }
                }
            }
            .onLongPressGesture(minimumDuration: 0.6, perform: end)
            .accessibilityElement()
            .accessibilityLabel(requiresHold ? "End workout" : "Close")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { end() } // a VoiceOver double tap is deliberate enough
    }
}

private struct MuteButton: View {
    let model: TimerModel
    var background: Color = .black.opacity(0.6)

    var body: some View {
        Button(action: model.toggleMute) {
            RoundIcon(systemImage: model.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill", size: 52, background: background)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(model.isMuted ? "Sound off" : "Sound on")
    }
}

private struct FinishedView: View {
    let workout: Workout
    let done: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 72))
                .foregroundStyle(Step.Phase.work.bright)
            Text("Workout complete")
                .font(.condensed(44))
            Text("\(workout.name) · \(clockText(workout.duration))")
                .font(.title2)
                .monospacedDigit()
                .foregroundStyle(Theme.secondary)
            Spacer()
            Button(action: done) {
                Text("Done")
                    .font(.title3.weight(.semibold))
                    .frame(maxWidth: 420)
                    .frame(height: 60)
                    .foregroundStyle(Theme.background)
                    .background(Theme.text, in: RoundedRectangle(cornerRadius: 18))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .foregroundStyle(Theme.text)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background.ignoresSafeArea())
    }
}
