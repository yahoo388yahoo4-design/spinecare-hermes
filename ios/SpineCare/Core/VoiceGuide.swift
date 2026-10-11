import Foundation
import Speech
import AVFoundation

/// Voice guidance (TTS cues) + voice control (start/stop/next/repeat) on iOS.
/// Commands are recognized in English on purpose: short words, robust across
/// accents; the UI explains this when the app language is Romanian.
final class VoiceGuide: ObservableObject {
    @Published var listeningStatus: String? = nil
    var onCommand: ((String) -> Void)?

    private let synth = AVSpeechSynthesizer()
    private var recognizer: SFSpeechRecognizer?
    private var req: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()

    init() {
        if ServerConfig.shared.voiceGuidance {
            // AVSpeechSynthesizer picks the voice at speak() time from language.
        }
        if ServerConfig.shared.voiceControl {
            SFSpeechRecognizer.requestAuthorization { _ in }
        }
    }

    private func langCode() -> String {
        switch ServerConfig.shared.lang {
        case "ro": return "ro-RO"
        case "en": return "en-US"
        default:
            return Locale.current.language.languageCode?.identifier ?? "en"
        }
    }

    func speak(_ text: String) {
        guard ServerConfig.shared.voiceGuidance else { return }
        let u = AVSpeechUtterance(string: text)
        let code = langCode()
        u.voice = AVSpeechSynthesisVoice(language: code) ?? AVSpeechSynthesisVoice(language: "en-US")
        u.rate = AVSpeechUtteranceDefaultSpeechRate
        synth.stopSpeaking(at: .immediate)
        synth.speak(u)
    }

    func startListening() {
        guard ServerConfig.shared.voiceControl else { return }
        guard recognizer == nil else { return }
        recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        guard recognizer != nil else { listeningStatus = L10n.t("voice_unavailable"); return }
        SFSpeechRecognizer.requestAuthorization { [weak self] auth in
            guard auth == .authorized else { self?.listeningStatus = L10n.t("voice_unavailable"); return }
            DispatchQueue.main.async { self?.beginSession() }
        }
    }

    private func beginSession() {
        let reqNew = SFSpeechAudioBufferRecognitionRequest()
        reqNew.shouldReportPartialResults = false
        if #available(iOS 17, *) { reqNew.requiresOnDeviceRecognition = true }
        req = reqNew
        let input = engine.inputNode
        let fmt = input.outputFormat(forBus: 0)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: fmt) { buf, _ in reqNew.append(buf) }
        engine.prepare()
        try? engine.start()
        task = recognizer?.recognitionTask(with: reqNew) { [weak self] res, err in
            guard let self else { return }
            if let res, res.isFinal, let frag = res.bestTranscription.segments.last?.substring {
                let text = frag.lowercased()
                for cmd in ["start", "stop", "next", "repeat"] where text.contains(cmd) {
                    self.onCommand?(cmd); break
                }
            }
            if err != nil || (res?.isFinal ?? false) {
                self.restart()
            }
        }
        listeningStatus = L10n.t("voice_listening")
    }

    private func restart() {
        inputStop()
        guard ServerConfig.shared.voiceControl else { listeningStatus = nil; return }
        // small delay to avoid hot-looping the recognizer on errors
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
            guard let self, ServerConfig.shared.voiceControl else { return }
            self.beginSession()
        }
    }

    private func inputStop() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        task?.cancel(); task = nil
        req = nil
    }

    func stopListening() {
        inputStop()
        listeningStatus = nil
    }

    func shutdown() {
        stopListening()
        synth.stopSpeaking(at: .immediate)
    }
}
