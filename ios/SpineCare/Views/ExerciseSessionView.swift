import SwiftUI
import AVFoundation

/// Real-time coaching session: camera -> Vision pose -> metrics/framing/reps,
/// with optional GPU-server enhancement (AUTO mode falls back on drop).
/// Supports front/back camera switching and session recording with the sensor
/// overlay burned into the video (review in the Sessions tab).
struct ExerciseSessionView: View {
    @State var exercise: Exercise
    @StateObject private var tracker = PoseTracker()
    @State private var running = false
    @State private var reps = 0
    @State private var cue = ""
    @State private var metricsText = ""
    @State private var engine = RepEngine(low: 0, high: 0)
    @State private var serverConnected = false
    @State private var frontCamera = ServerConfig.shared.lensFront
    @State private var zoomPercent = ServerConfig.shared.zoomPercent
    @State private var recording = ServerConfig.shared.recordSessions
    private let frameSink = FrameSink()
    @State private var recorder: SessionRecorder?
    @State private var recordedFrames = 0
    @State private var videoURL: URL?
    @StateObject private var voice = VoiceGuide()
    private let client = ServerClient()

    var body: some View {
        ZStack(alignment: .top) {
            CameraPreview(tracker: tracker, frontCamera: frontCamera, zoomPercent: zoomPercent) { buf in
                frameSink.set(buf)
            }
            .ignoresSafeArea()

            // Live skeleton + HUD overlay (same renderer as the recording)
            OverlayCanvas(landmarks: tracker.landmarks, cue: cue, metrics: metricsText,
                          reps: "\(reps)/\(exercise.targetReps)", mirror: frontCamera)
                .ignoresSafeArea()
            VStack {
                Text("\(exercise.name) — \(exercise.view.uppercased()) view\n\(exercise.descriptionText)")
                    .font(.callout).padding(8)
                    .background(.black.opacity(0.6)).foregroundStyle(.white)
                Spacer()
                Text(cue).font(.subheadline).padding(8)
                    .background(.black.opacity(0.7)).foregroundStyle(.yellow)
                Text(metricsText).font(.system(.caption, design: .monospaced))
                    .padding(6).background(.black.opacity(0.7)).foregroundStyle(.white)
                HStack {
                    Button(running ? L10n.t("session_stop") : L10n.t("session_start")) { toggle() }
                        .buttonStyle(.borderedProminent)
                    Button(L10n.t("session_flip")) {
                        frontCamera.toggle(); ServerConfig.shared.lensFront = frontCamera; ServerConfig.shared.save()
                    }
                        .buttonStyle(.bordered)
                    Button(recording ? L10n.t("session_rec_on") : L10n.t("session_rec_off")) {
                        recording.toggle(); ServerConfig.shared.recordSessions = recording
                    }
                    .buttonStyle(.bordered)
                    Button("Mode: \(modeLabel())") { cycleMode() }
                        .buttonStyle(.bordered)
                }.padding()
                HStack {
                    Text(L10n.t("settings_zoom")).font(.caption).foregroundStyle(.white)
                    Slider(value: Binding(
                        get: { Double(zoomPercent) },
                        set: { zoomPercent = Int($0); ServerConfig.shared.zoomPercent = Int($0); ServerConfig.shared.save() }
                    ), in: 0...100)
                    Text(String(format: "%.1fx", 1 + Double(zoomPercent) / 100 * 4))
                        .font(.caption.monospacedDigit()).foregroundStyle(.white)
                }.padding(.horizontal)
            }
            Text("\(reps)/\(exercise.targetReps)")
                .font(.system(size: 44, weight: .bold)).foregroundStyle(.green)
                .shadow(radius: 4)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding()
        }
        .navigationTitle(exercise.name)
        .onAppear {
            cue = L10n.t("session_position_cam", exercise.view.uppercased())
            voice.onCommand = { cmd in handleVoice(cmd) }
            voice.startListening()
        }
        .onDisappear {
            // Never lose a session: if still running, stop + save first.
            if running { toggle() }
            client.disconnect(); stopRecorder(); voice.shutdown()
        }
        .onChange(of: cue) { newValue in voice.speak(newValue) }
        .onChange(of: tracker.landmarks) { lms in score(lms) }
    }

    private func modeLabel() -> String {
        switch ServerConfig.shared.mode {
        case .auto: return L10n.t("session_mode_auto")
        case .onDevice: return L10n.t("session_mode_ondevice")
        case .server: return L10n.t("session_mode_server")
        }
    }

    private func cycleMode() {
        ServerConfig.shared.mode = ServerConfig.Mode(rawValue: (ServerConfig.shared.mode.rawValue + 1) % 3)!
        ServerConfig.shared.save()
    }

    /// Local scoring: framing gate -> metrics -> rep engine -> form rules.
    private func score(_ lms: [String: Metrics.LM]) {
        guard running else { return }
        let framing = FramingGuide.check(lms.isEmpty ? nil : lms,
                                         required: exercise.requiredLandmarks,
                                         minConf: exercise.minConf, frameW: 1, frameH: 1)
        guard framing.ready else {
            cue = L10n.t("session_camera_prefix") + framing.issues.map(FramingGuide.fixFor).joined(separator: " ")
            return
        }
        let metrics = Metrics.compute(lms)
        let sig = signalValue(metrics, exercise.signal)
        let now = Date().timeIntervalSince1970
        if engine.update(sig, now) { reps = engine.reps; cue = L10n.t("session_rep_done", Int64(reps), Int64(exercise.targetReps)) }
        for rule in exercise.formRules {
            if let v = metrics[rule.metric], abs(v) > rule.maxAbs { cue = rule.cue; break }
        }
        metricsText = metrics.map { "\($0.key)=\(String(format: "%.1f", $0.value))" }.joined(separator: "  ")
            + "\npose=\(String(format: "%.0f", tracker.lastMs))ms \(serverConnected ? "GPU" : "on-device")"
        // Burn the current frame + overlay into the recording
        if let rec = recorder, let buf = frameSink.get(),
           let pb = CMSampleBufferGetImageBuffer(buf) {
            rec.append(pb, landmarks: tracker.landmarks, cue: cue, metrics: metricsText,
                      reps: "\(reps)/\(exercise.targetReps)", mirror: frontCamera,
                      timestamp: CMSampleBufferGetPresentationTimeStamp(buf))
            recordedFrames += 1
        }
    }

    private func handleVoice(_ cmd: String) {
        switch cmd {
        case "start": if !running { toggle() }
        case "stop": if running { toggle() }
        case "next":
            let i = Plan.indexOf(exercise.id)
            if running { toggle() }
            exercise = Plan.exercises[(i + 1) % Plan.exercises.count]
            engine = RepEngine(low: exercise.repLow, high: exercise.repHigh)
            reps = 0
            cue = L10n.t("session_position_cam", exercise.view.uppercased())
        case "repeat": voice.speak(exercise.descriptionText)
        default: break
        }
    }

    private func toggle() {
        running.toggle()
        if running {
            engine = RepEngine(low: exercise.repLow, high: exercise.repHigh)
            reps = 0; recordedFrames = 0
            cue = L10n.t("session_calibrate", exercise.stopConditions.joined(separator: ", "))
            if recording { startRecorder() }
            if ServerConfig.shared.mode != .onDevice {
                client.connect(ServerConfig.shared.url, user: ServerConfig.shared.user,
                               pass: ServerConfig.shared.pass) { [self] msg in
                    if (msg["type"] as? String) == "pose" {
                        serverConnected = true
                        if let r = msg["reps"] as? Int { reps = r }
                        if let cues = msg["cues"] as? [[String: Any]], let c = cues.first?["cue"] as? String { cue = c }
                    }
                }
                client.send(["type": "session", "exercise_id": exercise.id])
            }
        } else {
            client.disconnect(); serverConnected = false
            stopRecorder()
            SessionStore.shared.save(exerciseId: exercise.id, reps: reps,
                                     metrics: lastMetricsSnapshot(), cues: [:],
                                     frames: tracker.landmarks.count,
                                     source: serverConnected ? "gpu-server" : "on-device",
                                     videoURL: videoURL, recordedFrames: recordedFrames)
        }
    }

    private func lastMetricsSnapshot() -> [String: Double] {
        // Parse the latest metrics line back into values for the store.
        var out: [String: Double] = [:]
        for token in metricsText.split(separator: " ") {
            let parts = token.split(separator: "=")
            if parts.count == 2, let v = Double(parts[1]) { out[String(parts[0])] = v }
        }
        return out
    }

    private func startRecorder() {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("recordings", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("session_\(exercise.id)_\(Int(Date().timeIntervalSince1970)).mp4")
        recorder = SessionRecorder(url: url, width: 720, height: 1280)
        videoURL = recorder == nil ? nil : url
    }

    private func stopRecorder() {
        recorder?.finish()
        if recordedFrames == 0 { videoURL = nil }
        recorder = nil
    }
}

/// Thread-safe holder for the latest camera sample buffer (written on the
/// camera queue, read on the main thread when scoring a frame).
final class FrameSink {
    private let lock = NSLock()
    private var buf: CMSampleBuffer?
    func set(_ b: CMSampleBuffer) { lock.lock(); buf = b; lock.unlock() }
    func get() -> CMSampleBuffer? { lock.lock(); defer { lock.unlock() }; return buf }
}

/// SwiftUI overlay drawing the skeleton + HUD, mirroring the camera preview.
struct OverlayCanvas: View {
    let landmarks: [String: Metrics.LM]
    let cue: String
    let metrics: String
    let reps: String
    let mirror: Bool

    var body: some View {
        GeometryReader { geo in
            Canvas { ctx, size in
                OverlayRenderer.draw(ctx: &ctx, size: size, lms: landmarks,
                                     cue: cue, metrics: metrics, reps: reps, mirror: mirror)
            }
        }
    }
}

/// UIViewRepresentable wrapping AVCaptureSession + Vision processing.
/// Attaches a video preview layer (fixes the black-preview bug), requests
/// camera permission, and supports front/back switching.
struct CameraPreview: UIViewRepresentable {
    let tracker: PoseTracker
    let frontCamera: Bool
    let zoomPercent: Int
    let onFrame: (CMSampleBuffer) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = PreviewContainerView()
        AVCaptureCamera.shared.requestPermission { granted in
            if granted { DispatchQueue.main.async { context.coordinator.reconfigure() } }
        }
        context.coordinator.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.frontCamera = frontCamera
        context.coordinator.zoomPercent = zoomPercent
        context.coordinator.applyZoom()
        context.coordinator.reconfigure()
    }

    func makeCoordinator() -> Coordinator { Coordinator(tracker: tracker, onFrame: onFrame) }

    final class Coordinator: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
        let session = AVCaptureSession()
        let previewLayer = AVCaptureVideoPreviewLayer()
        let tracker: PoseTracker
        let onFrame: (CMSampleBuffer) -> Void
        var frontCamera = false
        var zoomPercent = 0
        private var input: AVCaptureDeviceInput?
        private var device: AVCaptureDevice?
        private var configured = false

        init(tracker: PoseTracker, onFrame: @escaping (CMSampleBuffer) -> Void) {
            self.tracker = tracker; self.onFrame = onFrame
        }

        func attach(to view: UIView) {
            previewLayer.session = session
            previewLayer.videoGravity = .resizeAspectFill
            previewLayer.frame = view.bounds
            view.layer.insertSublayer(previewLayer, at: 0)
            if let c = view as? PreviewContainerView { c.onLayout = { [weak self] bounds in
                self?.previewLayer.frame = bounds
            } }
            reconfigure()
        }

        func applyZoom() {
            guard let cam = device else { return }
            let minZ = max(1, cam.minAvailableVideoZoomFactor)
            let maxZ = min(12, cam.maxAvailableVideoZoomFactor)
            let f = minZ + (maxZ - minZ) * (Double(zoomPercent) / 100.0)
            do { try cam.lockForConfiguration(); cam.videoZoomFactor = CGFloat(f); cam.unlockForConfiguration() } catch {}
        }

        func reconfigure() {
            guard AVCaptureCamera.shared.authorized else { return }
            let position: AVCaptureDevice.Position = frontCamera ? .front : .back
            guard let cam = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position)
                    ?? AVCaptureDevice.default(for: .video) else { return }
            session.beginConfiguration()
            if let old = input { session.removeInput(old) }
            guard let newInput = try? AVCaptureDeviceInput(device: cam) else {
                session.commitConfiguration(); return
            }
            if session.canAddInput(newInput) {
                session.addInput(newInput); input = newInput; device = cam
                applyZoom()
            }
            if session.outputs.isEmpty {
                let output = AVCaptureVideoDataOutput()
                output.alwaysDiscardsLateVideoFrames = true
                output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                let interval = max(0.05, Double(ServerConfig.shared.frameIntervalMs) / 1000)
                output.setSampleBufferDelegate(FrameDelegate(tracker: tracker, minInterval: interval,
                                                             onFrame: onFrame),
                                               queue: DispatchQueue(label: "pose.queue"))
                if session.canAddOutput(output) { session.addOutput(output) }
            }
            // Mirror the preview for the front camera (WYSIWYG)
            previewLayer.connection?.automaticallyAdjustsVideoMirroring = false
            previewLayer.connection?.isVideoMirrored = frontCamera
            session.commitConfiguration()
            if !configured {
                configured = true
                DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
            }
        }
    }
}

/// Permission helper shared by all camera users.
final class AVCaptureCamera {
    static let shared = AVCaptureCamera()
    var authorized = false
    func requestPermission(_ done: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: authorized = true; done(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] ok in
                self?.authorized = ok
                DispatchQueue.main.async { done(ok) }
            }
        default: authorized = false; done(false)
        }
    }
}

/// UIView that forwards its bounds to a callback on layout, so the
/// AVCaptureVideoPreviewLayer tracks size changes on rotation.
final class PreviewContainerView: UIView {
    var onLayout: ((CGRect) -> Void)?
    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?(bounds)
    }
}

final class FrameDelegate: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let tracker: PoseTracker
    private let minInterval: TimeInterval
    private let onFrame: (CMSampleBuffer) -> Void
    private var lastT = Date.distantPast
    init(tracker: PoseTracker, minInterval: TimeInterval, onFrame: @escaping (CMSampleBuffer) -> Void) {
        self.tracker = tracker; self.minInterval = minInterval; self.onFrame = onFrame
    }

    func captureOutput(_ out: AVCaptureOutput, didOutput buf: CMSampleBuffer, from conn: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(lastT) >= minInterval,
              CMSampleBufferGetImageBuffer(buf) != nil else { return }
        lastT = now
        onFrame(buf)
        tracker.process(buf)
    }
}
