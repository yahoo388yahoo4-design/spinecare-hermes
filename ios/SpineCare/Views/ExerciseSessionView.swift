import SwiftUI
import AVFoundation

/// Real-time coaching session: camera -> Vision pose -> metrics/framing/reps,
/// with optional GPU-server enhancement (AUTO mode falls back on drop).
struct ExerciseSessionView: View {
    let exercise: Exercise
    @StateObject private var tracker = PoseTracker()
    @State private var running = false
    @State private var reps = 0
    @State private var cue = ""
    @State private var metricsText = ""
    @State private var engine = RepEngine(low: 0, high: 0)
    @State private var serverConnected = false
    private let client = ServerClient()

    var body: some View {
        ZStack(alignment: .top) {
            CameraPreview(tracker: tracker).ignoresSafeArea()
            VStack {
                Text("\(exercise.name) — \(exercise.view.uppercased()) view\n\(exercise.description)")
                    .font(.callout).padding(8)
                    .background(.black.opacity(0.6)).foregroundStyle(.white)
                Spacer()
                Text(cue).font(.subheadline).padding(8)
                    .background(.black.opacity(0.7)).foregroundStyle(.yellow)
                Text(metricsText).font(.system(.caption, design: .monospaced))
                    .padding(6).background(.black.opacity(0.7)).foregroundStyle(.white)
                HStack {
                    Button(running ? "Stop & Save" : "Start") { toggle() }
                        .buttonStyle(.borderedProminent)
                    Button("Mode: \(modeLabel())") { cycleMode() }
                        .buttonStyle(.bordered)
                }.padding()
            }
            Text("\(reps)/\(exercise.targetReps)")
                .font(.system(size: 44, weight: .bold)).foregroundStyle(.green)
                .shadow(radius: 4)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding()
        }
        .navigationTitle(exercise.name)
        .onAppear { cue = "Position the camera: \(exercise.view.uppercased()) view." }
        .onDisappear { client.disconnect() }
        .onChange(of: tracker.landmarks) { lms in score(lms) }
    }

    private func modeLabel() -> String {
        switch ServerConfig.shared.mode {
        case .auto: return "Auto"
        case .onDevice: return "On-device"
        case .server: return "GPU server"
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
            cue = "Camera: " + framing.issues.map(FramingGuide.fixFor).joined(separator: " ")
            return
        }
        let metrics = Metrics.compute(lms)
        let sig = signalValue(metrics, exercise.signal)
        let now = Date().timeIntervalSince1970
        if engine.update(sig, now) { reps = engine.reps; cue = "Rep \(reps)/\(exercise.targetReps) ✓" }
        for rule in exercise.formRules {
            if let v = metrics[rule.metric], abs(v) > rule.maxAbs { cue = rule.cue; break }
        }
        metricsText = metrics.map { "\($0.key)=\(String(format: "%.1f", $0.value))" }.joined(separator: "  ")
            + "\npose=\(String(format: "%.0f", tracker.lastMs))ms \(serverConnected ? "GPU" : "on-device")"
    }

    private func toggle() {
        running.toggle()
        if running {
            engine = RepEngine(low: exercise.repLow, high: exercise.repHigh)
            reps = 0
            cue = "Calibrate: adopt neutral posture, then begin. Stop on: \(exercise.stopConditions.joined(separator: ", "))"
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
        }
    }
}

/// UIViewRepresentable wrapping AVCaptureSession + Vision processing.
struct CameraPreview: UIViewRepresentable {
    let tracker: PoseTracker

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let session = AVCaptureSession()
        guard let cam = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: cam),
              session.canAddInput(input) else { return view }
        session.addInput(input)
        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        let interval = max(0.05, Double(ServerConfig.shared.frameIntervalMs) / 1000)
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        let delegate = FrameDelegate(tracker: tracker, minInterval: interval)
        output.setSampleBufferDelegate(delegate, queue: DispatchQueue(label: "pose.queue"))
        context.coordinator.retain = delegate
        if session.canAddOutput(output) { session.addOutput(output) }
        DispatchQueue.global(qos: .userInitiated).async { session.startRunning() }
        return view
    }
    func updateUIView(_ uiView: UIView, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var retain: FrameDelegate? }
}

final class FrameDelegate: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let tracker: PoseTracker
    private let minInterval: TimeInterval
    private var lastT = Date.distantPast
    init(tracker: PoseTracker, minInterval: TimeInterval) { self.tracker = tracker; self.minInterval = minInterval }

    func captureOutput(_ out: AVCaptureOutput, didOutput buf: CMSampleBuffer, from conn: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(lastT) >= minInterval,
              let pb = CMSampleBufferGetImageBuffer(buf) else { return }
        lastT = now
        tracker.process(pb)
    }
}
