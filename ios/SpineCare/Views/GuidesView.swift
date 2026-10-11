import SwiftUI
import AVKit

/// Guides tab: offline how-to videos (reference clips processed by the same
/// pose pipeline: skeleton, angles and cues burned in) + intro cards teaching
/// camera placement. Bundled in the app; "Download from server" refreshes them.
struct GuidesView: View {
    struct Guide: Codable { let exercise_id: String; let file: String; let bytes: Int }
    struct Manifest: Codable { let version: Int; let guides: [Guide] }

    @State private var guides: [Guide] = []
    @State private var status = ""

    var body: some View {
        NavigationStack {
            List {
                Section(header: Text(L10n.t("guides_title"))) {
                    Text(L10n.t("guides_intro")).font(.callout).foregroundStyle(.secondary)
                }
                ForEach(guides, id: \.file) { g in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(Plan.byId(g.exercise_id)?.name ?? g.exercise_id).font(.headline)
                        if let url = guideURL(g.file) {
                            NavigationLink(L10n.t("guides_open", Int64(g.bytes / 1000))) {
                                VideoPlayer(player: AVPlayer(url: url))
                            }
                        } else {
                            Text(L10n.t("sessions_missing")).foregroundStyle(.red)
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section {
                    Button(L10n.t("guides_download")) { downloadFromServer() }
                    Text(status).font(.caption).foregroundStyle(.secondary)
                    Text(L10n.t("guides_offline_note"))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(L10n.t("tab_guides"))
            .onAppear { loadBundled() }
        }
    }

    private func loadBundled() {
        guard let url = Bundle.main.url(forResource: "guides", withExtension: "json",
                                        subdirectory: "guides")
            ?? Bundle.main.url(forResource: "guides", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let mf = try? JSONDecoder().decode(Manifest.self, from: data)
        else { guides = []; return }
        guides = mf.guides
    }

    private func guideURL(_ name: String) -> URL? {
        // server-refreshed copies in Documents win; bundle copy is the fallback
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("guides/\(name)")
        if FileManager.default.fileExists(atPath: docs.path) { return docs }
        return Bundle.main.url(forResource: name, withExtension: nil, subdirectory: "guides")
            ?? Bundle.main.url(forResource: name.replacingOccurrences(of: ".mp4", with: ""),
                               withExtension: "mp4")
    }

    private func downloadFromServer() {
        let cfg = ServerConfig.shared
        guard let base = URL(string: cfg.url.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/guides") else { return }
        status = L10n.t("settings_testing", cfg.url)
        var req = URLRequest(url: base)
        if !cfg.user.isEmpty, let cred = "\(cfg.user):\(cfg.pass)".data(using: .utf8) {
            req.setValue("Basic \(cred.base64EncodedString())", forHTTPHeaderField: "Authorization")
        }
        URLSession.shared.dataTask(with: req) { data, _, err in
            guard err == nil, let data,
                  let mf = try? JSONDecoder().decode(Manifest.self, from: data) else {
                DispatchQueue.main.async { status = L10n.t("guides_dl_failed") }
                return
            }
            let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("guides", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var ok = true
            let sem = DispatchSemaphore(value: 0)
            for g in mf.guides {
                guard let u = URL(string: base.absoluteString + "/" + g.file) else { continue }
                var fr = URLRequest(url: u)
                if !cfg.user.isEmpty, let cred = "\(cfg.user):\(cfg.pass)".data(using: .utf8) {
                    fr.setValue("Basic \(cred.base64EncodedString())", forHTTPHeaderField: "Authorization")
                }
                URLSession.shared.dataTask(with: fr) { d, _, e in
                    if e == nil, let d { try? d.write(to: dir.appendingPathComponent(g.file)) }
                    else { ok = false }
                    sem.signal()
                }.resume()
                sem.wait()
            }
            DispatchQueue.main.async {
                status = ok ? L10n.t("guides_downloaded") : L10n.t("guides_dl_failed")
                loadBundled()
            }
        }.resume()
    }
}
