package com.spinecare.app

import androidx.annotation.StringRes

data class FormRule(val metric: String, val maxAbs: Double, val cue: String)

data class Exercise(
    val id: String,
    @StringRes val nameRes: Int,
    val view: String,                 // side | front | back
    val requiredLandmarks: List<String>,
    val minConf: Float,
    val signal: String,
    val repLow: Double,
    val repHigh: Double,
    val targetReps: Int,
    val formRules: List<FormRule>,
    @StringRes val stopConditionsRes: List<Int>,
    val clinicianApproved: Boolean,
    @StringRes val descriptionRes: Int,
)

/** Built-in plan mirrors server/exercises.json. In production this is fetched
 *  from the clinician backend and signed; nothing here is self-prescribed.
 *  Names/descriptions/stop-conditions are resource keys so they localize. */
object Plan {
    val exercises = listOf(
        Exercise(
            "chin_tuck", R.string.ex_chin_tuck_name, "side",
            listOf("lear", "lsho", "lhip"), 0.5f, "head_forward_proxy", 0.02, 0.12, 10,
            listOf(FormRule("shoulder_line_tilt", 8.0, "Relax the raised shoulder; keep shoulders level."),
                   FormRule("trunk_tilt_frontal", 10.0, "Keep the trunk upright; only the head moves.")),
            listOf(R.string.ex_chin_tuck_stop1, R.string.ex_chin_tuck_stop2, R.string.ex_chin_tuck_stop3), false,
            R.string.ex_chin_tuck_desc
        ),
        Exercise(
            "cervical_rotation_rom", R.string.ex_rot_name, "front",
            listOf("nose", "lsho", "rsho"), 0.5f, "abs:neck_yaw_est", 8.0, 35.0, 8,
            listOf(FormRule("shoulder_line_tilt", 8.0, "Do not lift the shoulder when turning; rotate through the neck only."),
                   FormRule("trunk_tilt_frontal", 8.0, "Keep the chest facing forward; isolate neck rotation.")),
            listOf(R.string.ex_rot_stop1, R.string.ex_rot_stop2), false,
            R.string.ex_rot_desc
        ),
        Exercise(
            "thoracic_extension", R.string.ex_thor_name, "side",
            listOf("lear", "lsho", "lhip", "lkne"), 0.5f, "trunk_tilt_frontal", 5.0, 18.0, 10,
            listOf(FormRule("cervical_sag_proxy", 60.0, "Lead with the chest, not the head; keep the neck long.")),
            listOf(R.string.ex_thor_stop1, R.string.ex_thor_stop2), false,
            R.string.ex_thor_desc
        ),
        Exercise(
            "scapular_retraction", R.string.ex_scap_name, "back",
            listOf("lsho", "rsho", "lhip", "rhip"), 0.5f, "shoulder_line_tilt", 0.0, 6.0, 10,
            listOf(FormRule("trunk_tilt_frontal", 8.0, "Stay tall; do not lean.")),
            listOf(R.string.ex_scap_stop1), false,
            R.string.ex_scap_desc
        ),
    )
    fun byId(id: String) = exercises.firstOrNull { it.id == id }
}
