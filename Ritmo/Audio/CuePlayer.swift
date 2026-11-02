import AVFoundation
import AudioToolbox

/// Beeps, voice and vibration, mixed over the user's music (SPEC §4.4, §6).
final class CuePlayer: NSObject, AVSpeechSynthesizerDelegate {
    var isMuted = false
    /// Called when a phone call or Siri takes over the audio.
    var onInterruption: (() -> Void)?

    private let session = AVAudioSession.sharedInstance()
    private let synthesizer = AVSpeechSynthesizer()
    private let voice = AVSpeechSynthesisVoice(language: "en-US") ?? AVSpeechSynthesisVoice(language: "en-GB")
    private let silence = CuePlayer.player(Tone.silence(seconds: 1), loops: true)
    private let shortBeep = CuePlayer.player(Tone.beep(frequency: 880, seconds: 0.12))
    private let longBeep = CuePlayer.player(Tone.beep(frequency: 1320, seconds: 0.5))
    private var isDucking = false
    private var unduck: DispatchWorkItem?

    override init() {
        super.init()
        synthesizer.delegate = self
        NotificationCenter.default.addObserver(
            self, selector: #selector(interrupted), name: AVAudioSession.interruptionNotification, object: session
        )
    }

    /// Starts the silent loop that keeps the app running while the screen is locked.
    func start() {
        activate(ducking: isDucking)
        silence?.play()
    }

    func stop() {
        unduck?.cancel()
        synthesizer.stopSpeaking(at: .immediate)
        silence?.stop()
        isDucking = false
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }

    func play(_ cue: Cue) {
        if cue != .beep, setting("vibration") {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
        guard !isMuted else { return }
        duck()
        switch cue {
        case .beep:
            restart(shortBeep)
            unduckSoon(after: 1.5)
        case .announce(let text):
            say(text)
        case .finish:
            say("Workout complete")
        }
    }

    private func say(_ text: String) {
        guard setting("voice") else {
            restart(longBeep)
            unduckSoon(after: 1)
            return
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
        utterance.rate = 0.52
        synthesizer.speak(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.unduckSoon(after: 0.3) }
    }

    private func restart(_ player: AVAudioPlayer?) {
        player?.currentTime = 0
        player?.play()
    }

    // MARK: Lowering the music

    /// Lowers other apps' audio from the first beep to the end of the announcement.
    private func duck() {
        unduck?.cancel()
        guard setting("duckMusic"), !isDucking else { return }
        isDucking = true
        reactivate()
    }

    private func unduckSoon(after delay: TimeInterval) {
        guard isDucking else { return }
        unduck?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, !self.synthesizer.isSpeaking else { return }
            self.isDucking = false
            self.reactivate()
        }
        unduck = work
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
    }

    /// Other apps only get louder again when our session deactivates,
    /// so the silent loop stops for a moment and starts again with the new setting.
    private func reactivate() {
        silence?.stop()
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
        start()
    }

    private func activate(ducking: Bool) {
        let options: AVAudioSession.CategoryOptions = ducking ? [.mixWithOthers, .duckOthers] : [.mixWithOthers]
        try? session.setCategory(.playback, mode: .default, options: options)
        try? session.setActive(true)
    }

    @objc private func interrupted(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              AVAudioSession.InterruptionType(rawValue: raw) == .began else { return }
        DispatchQueue.main.async { self.onInterruption?() }
    }

    private func setting(_ key: String) -> Bool {
        UserDefaults.standard.object(forKey: key) as? Bool ?? true
    }

    private static func player(_ data: Data, loops: Bool = false) -> AVAudioPlayer? {
        let player = try? AVAudioPlayer(data: data)
        player?.numberOfLoops = loops ? -1 : 0
        player?.prepareToPlay()
        return player
    }
}
