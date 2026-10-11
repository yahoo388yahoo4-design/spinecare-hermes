import Foundation

struct FormRule: Codable { let metric: String; let maxAbs: Double; cueKey: String
    var cue: String { cueKey } }

struct Exercise: Codable, Identifiable {
    let id: String
    let nameKey: String
    let view: String                    // side | front | back
    let requiredLandmarks: [String]
    let minConf: Double
    let signal: String
    let repLow: Double
    let repHigh: Double
    let targetReps: Int
    let formRules: [FormRule]
    let stopConditionKeys: [String]
    let clinicianApproved: Bool
    let descriptionKey: String

    var name: String { L10n.t(nameKey) }
    var descriptionText: String { L10n.t(descriptionKey) }
    var stopConditions: [String] { stopConditionKeys.map { L10n.t($0) } }
}

/// Built-in plan mirrors server/exercises.json and Android Plan.kt.
/// In production this is fetched from the clinician backend and signed;
/// nothing here is self-prescribed. Text is stored as localization keys.
enum Plan {
    static let exercises: [Exercise] = [
        Exercise(
            id: "chin_tuck", nameKey: "ex_chin_tuck_name", view: "side",
            requiredLandmarks: ["lear", "lsho", "lhip"], minConf: 0.5,
            signal: "head_forward_proxy", repLow: 0.02, repHigh: 0.12, targetReps: 10,
            formRules: [FormRule(metric: "shoulder_line_tilt", maxAbs: 8, cueKey: "Relax the raised shoulder; keep shoulders level."),
                        FormRule(metric: "trunk_tilt_frontal", maxAbs: 10, cueKey: "Keep the trunk upright; only the head moves.")],
            stopConditionKeys: ["ex_chin_tuck_stop1", "ex_chin_tuck_stop2", "ex_chin_tuck_stop3"],
            clinicianApproved: false,
            descriptionKey: "ex_chin_tuck_desc"),
        Exercise(
            id: "cervical_rotation_rom", nameKey: "ex_rot_name", view: "front",
            requiredLandmarks: ["nose", "lsho", "rsho"], minConf: 0.5,
            signal: "abs:neck_yaw_est", repLow: 8, repHigh: 35, targetReps: 8,
            formRules: [FormRule(metric: "shoulder_line_tilt", maxAbs: 8, cueKey: "Do not lift the shoulder when turning; rotate through the neck only."),
                        FormRule(metric: "trunk_tilt_frontal", maxAbs: 8, cueKey: "Keep the chest facing forward; isolate neck rotation.")],
            stopConditionKeys: ["ex_rot_stop1", "ex_rot_stop2"],
            clinicianApproved: false,
            descriptionKey: "ex_rot_desc"),
        Exercise(
            id: "thoracic_extension", nameKey: "ex_thor_name", view: "side",
            requiredLandmarks: ["lear", "lsho", "lhip", "lknee"], minConf: 0.5,
            signal: "trunk_tilt_frontal", repLow: 5, repHigh: 18, targetReps: 10,
            formRules: [FormRule(metric: "cervical_sag_proxy", maxAbs: 60, cueKey: "Lead with the chest, not the head; keep the neck long.")],
            stopConditionKeys: ["ex_thor_stop1", "ex_thor_stop2"],
            clinicianApproved: false,
            descriptionKey: "ex_thor_desc"),
        Exercise(
            id: "scapular_retraction", nameKey: "ex_scap_name", view: "back",
            requiredLandmarks: ["lsho", "rsho", "lhip", "rhip"], minConf: 0.5,
            signal: "shoulder_line_tilt", repLow: 0, repHigh: 6, targetReps: 10,
            formRules: [FormRule(metric: "trunk_tilt_frontal", maxAbs: 8, cueKey: "Stay tall; do not lean.")],
            stopConditionKeys: ["ex_scap_stop1"],
            clinicianApproved: false,
            descriptionKey: "ex_scap_desc"),
    ]
    static func byId(_ id: String) -> Exercise? { exercises.first { $0.id == id } }
}
