import SwiftUI
import AVKit

@main
struct SpineCareApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

struct RootView: View {
    @ObservedObject var cfg = ServerConfig.shared   // language changes re-render tabs
    var body: some View {
        TabView {
            HomeView().tabItem { Label(L10n.t("tab_home"), systemImage: "house") }
            ExercisesView().tabItem { Label(L10n.t("tab_exercises"), systemImage: "figure.walk") }
            SessionsView().tabItem { Label(L10n.t("tab_sessions"), systemImage: "film") }
            SensorsView().tabItem { Label(L10n.t("tab_sensors"), systemImage: "dot.radiowaves.left.and.right") }
            SettingsView().tabItem { Label(L10n.t("tab_settings"), systemImage: "gearshape") }
        }
        .id(cfg.lang)   // force full re-render when the language changes
    }
}

struct HomeView: View {
    var body: some View {
        NavigationStack {
            List {
                Section(L10n.t("home_plan_title")) {
                    Text(L10n.t("home_plan_body"))
                        .font(.callout).foregroundStyle(.secondary)
                }
                Section(L10n.t("home_safety_title")) {
                    Text(L10n.t("home_safety_body"))
                        .font(.callout).foregroundStyle(.red)
                }
                Section(L10n.t("home_followup_title")) {
                    Text(L10n.t("home_followup_body"))
                        .font(.callout)
                }
                Section {
                    Text(L10n.t("disclaimer"))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("SpineCare")
        }
    }
}

/// Sessions tab: saved sessions with per-session metrics and the recorded
/// video (sensor overlay burned in) playable in-app.
struct SessionsView: View {
    @ObservedObject var store = SessionStore.shared

    var body: some View {
        NavigationStack {
            Group {
                if store.sessions.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "film").font(.largeTitle).foregroundStyle(.secondary)
                        Text(L10n.t("sessions_empty_title")).font(.headline)
                        Text(L10n.t("sessions_empty_body"))
                            .font(.callout).foregroundStyle(.secondary)
                            .multilineTextAlignment(.center).padding()
                    }
                } else {
                    List(store.sessions) { rec in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(rec.date) · \(Plan.byId(rec.exercise)?.name ?? rec.exercise)")
                                .font(.headline)
                            Text("reps=\(rec.reps) · frames=\(rec.frames) · [\(rec.source)]")
                                .font(.caption).foregroundStyle(.secondary)
                            if !rec.avg.isEmpty {
                                Text(rec.avg.map { "\($0.key)=\(String(format: "%.1f", $0.value))" }
                                        .joined(separator: "  "))
                                    .font(.system(.caption2, design: .monospaced))
                                    .foregroundStyle(.secondary)
                            }
                            if let v = rec.video, store.videoExists(rec) {
                                NavigationLink(L10n.t("sessions_review", Int64(rec.recordedFrames ?? 0))) {
                                    VideoReviewView(url: URL(fileURLWithPath: v))
                                }
                            } else if rec.video != nil {
                                Text(L10n.t("sessions_missing")).font(.caption).foregroundStyle(.red)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle(L10n.t("sessions_title"))
            .onAppear { store.load() }
        }
    }
}

struct VideoReviewView: View {
    let url: URL
    var body: some View {
        VideoPlayer(player: AVPlayer(url: url))
            .ignoresSafeArea()
            .navigationTitle(L10n.t("sessions_review_title"))
            .onDisappear {}
    }
}

struct ExercisesView: View {
    var body: some View {
        NavigationStack {
            List(Plan.exercises) { ex in
                NavigationLink(value: ex.id) {
                    VStack(alignment: .leading) {
                        Text(ex.name).font(.headline)
                        Text(L10n.t("ex_meta_fmt", ex.view.uppercased(), Int64(ex.targetReps),
                                     ex.clinicianApproved ? L10n.t("ex_approved") : L10n.t("ex_pending")))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle(L10n.t("tab_exercises"))
            .navigationDestination(for: String.self) { id in
                if let ex = Plan.byId(id) { ExerciseSessionView(exercise: ex) }
            }
        }
    }
}
