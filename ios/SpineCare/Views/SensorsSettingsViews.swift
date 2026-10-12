import SwiftUI

struct SensorsView: View {
    var body: some View {
        NavigationStack {
            List {
                Section(L10n.t("sensors_phone_title")) {
                    Label(L10n.t("sensors_phone_cam"), systemImage: "camera")
                    Label(L10n.t("sensors_phone_depth"), systemImage: "cube.transparent")
                    Label(L10n.t("sensors_phone_imu"), systemImage: "iphone")
                    Label(L10n.t("sensors_phone_health"), systemImage: "heart")
                }
                Section(L10n.t("sensors_ble_title")) {
                    Text(L10n.t("sensors_ble_body"))
                        .font(.callout)
                    Text(L10n.t("sensors_ble_fw"))
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section(L10n.t("sensors_can_title")) {
                    Text(L10n.t("sensors_can"))
                    Text(L10n.t("sensors_cannot"))
                        .foregroundStyle(.red)
                }
            }
            .navigationTitle(L10n.t("tab_sensors"))
        }
    }
}

struct SettingsView: View {
    @ObservedObject var cfg = ServerConfig.shared
    @State private var status = ""

    var body: some View {
        NavigationStack {
            Form {
                Section(L10n.t("settings_language")) {
                    Picker(L10n.t("settings_language"), selection: $cfg.lang) {
                        Text(L10n.t("lang_system")).tag("system")
                        Text(L10n.t("lang_en")).tag("en")
                        Text(L10n.t("lang_ro")).tag("ro")
                    }
                    Button(L10n.t("settings_save")) { cfg.save() }
                }
                Section(L10n.t("settings_mode_title")) {
                    Picker(L10n.t("settings_mode_title"), selection: $cfg.mode) {
                        Text(L10n.t("settings_mode_auto")).tag(ServerConfig.Mode.auto)
                        Text(L10n.t("settings_mode_ondevice")).tag(ServerConfig.Mode.onDevice)
                        Text(L10n.t("settings_mode_server")).tag(ServerConfig.Mode.server)
                    }
                }
                Section(L10n.t("settings_server_title")) {
                    TextField(L10n.t("settings_server_url"), text: $cfg.url,
                              prompt: Text("http://192.168.1.188:8300"))
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField(L10n.t("settings_user"), text: $cfg.user)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    SecureField(L10n.t("settings_pass"), text: $cfg.pass)
                    Button(L10n.t("settings_test")) {
                        status = L10n.t("settings_testing", cfg.url)
                        cfg.testConnection { status = $0 }
                    }
                    Text(status).font(.caption).foregroundStyle(.secondary)
                }
                Section(L10n.t("settings_tuning_title")) {
                    Stepper(L10n.t("settings_frame_interval", Int64(cfg.frameIntervalMs)),
                            value: $cfg.frameIntervalMs, in: 50...1000, step: 25)
                    Stepper(L10n.t("settings_server_interval", Int64(cfg.serverFrameIntervalMs)),
                            value: $cfg.serverFrameIntervalMs, in: 50...1000, step: 25)
                    VStack(alignment: .leading) {
                        Text(L10n.t("settings_min_conf", cfg.minConfidence))
                        Slider(value: $cfg.minConfidence, in: 0.1...0.9)
                    }
                    Toggle(L10n.t("settings_haptics"), isOn: $cfg.haptics)
                    Toggle(L10n.t("settings_audio"), isOn: $cfg.audioCues)
                    Toggle(L10n.t("settings_record"), isOn: $cfg.recordSessions)
                    Picker(L10n.t("settings_lens"), selection: $cfg.lensFront) {
                        Text(L10n.t("settings_lens_back")).tag(false)
                        Text(L10n.t("settings_lens_front")).tag(true)
                    }.pickerStyle(.segmented)
                    VStack(alignment: .leading) {
                        Text(L10n.t("settings_zoom") + ": " + String(format: "%.1fx", 1 + Double(cfg.zoomPercent) / 100 * 4))
                        Slider(value: Binding(get: { Double(cfg.zoomPercent) },
                                              set: { cfg.zoomPercent = Int($0) }), in: 0...100)
                    }
                    Toggle(L10n.t("settings_voice_guidance"), isOn: $cfg.voiceGuidance)
                    Toggle(L10n.t("settings_voice_control"), isOn: $cfg.voiceControl)
                }
                Section {
                    Button(L10n.t("settings_save")) { cfg.save(); status = L10n.t("settings_saved") }
                }
            }
            .navigationTitle(L10n.t("settings_title"))
        }
    }
}
