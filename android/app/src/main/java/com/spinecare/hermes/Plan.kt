package com.spinecare.hermes

import androidx.annotation.StringRes

data class FormRule(val metric: String, val maxAbs: Double, @StringRes val cueRes: Int)

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
 *  Names/descriptions/stop-conditions/cues are resource keys so they localize. */
object Plan {
    val exercises = listOf(
        Exercise(
            "chin_tuck", R.string.ex_chin_tuck_name, "side",
            listOf("lear", "lsho", "lhip"), 0.5f, "head_forward_proxy", 0.02, 0.12, 10,
            listOf(FormRule("shoulder_line_tilt", 8.0, R.string.cue_shoulder_level),
                   FormRule("trunk_tilt_frontal", 10.0, R.string.cue_trunk_upright)),
            listOf(R.string.ex_chin_tuck_stop1, R.string.ex_chin_tuck_stop2, R.string.ex_chin_tuck_stop3), false,
            R.string.ex_chin_tuck_desc
        ),
        Exercise(
            "cervical_rotation_rom", R.string.ex_rot_name, "front",
            listOf("nose", "lsho", "rsho"), 0.5f, "abs:neck_yaw_est", 8.0, 35.0, 8,
            listOf(FormRule("shoulder_line_tilt", 8.0, R.string.cue_no_sho_lift),
                   FormRule("trunk_tilt_frontal", 8.0, R.string.cue_chest_forward)),
            listOf(R.string.ex_rot_stop1, R.string.ex_rot_stop2), false,
            R.string.ex_rot_desc
        ),
        Exercise(
            "cervical_lateral_flexion", R.string.ex_lat_flex_name, "front",
            listOf("lear", "rear", "lsho", "rsho"), 0.5f, "abs:cervical_tilt", 5.0, 20.0, 8,
            listOf(FormRule("shoulder_line_tilt", 8.0, R.string.cue_shoulders_level2),
                   FormRule("trunk_tilt_frontal", 8.0, R.string.cue_bend_neck_only)),
            listOf(R.string.ex_lat_flex_stop1, R.string.ex_lat_flex_stop2), false,
            R.string.ex_lat_flex_desc
        ),
        Exercise(
            "shoulder_shrug", R.string.ex_shrug_name, "side",
            listOf("lear", "lsho", "lhip"), 0.5f, "ear_sho_dist", 0.26, 0.32, 10,
            listOf(FormRule("trunk_tilt_frontal", 10.0, R.string.cue_sho_only)),
            listOf(R.string.ex_shrug_stop1, R.string.ex_shrug_stop2), false,
            R.string.ex_shrug_desc
        ),
        Exercise(
            "neck_isometric_hold", R.string.ex_iso_name, "side",
            listOf("lear", "lsho", "lhip"), 0.5f, "head_forward_proxy", 0.0, 0.03, 5,
            listOf(FormRule("cervical_sag_proxy", 45.0, R.string.cue_head_still)),
            listOf(R.string.ex_iso_stop1, R.string.ex_iso_stop2), false,
            R.string.ex_iso_desc
        ),
        Exercise(
            "thoracic_extension", R.string.ex_thor_name, "side",
            listOf("lear", "lsho", "lhip", "lkne"), 0.5f, "trunk_tilt_frontal", 5.0, 18.0, 10,
            listOf(FormRule("cervical_sag_proxy", 60.0, R.string.cue_chest_lead)),
            listOf(R.string.ex_thor_stop1, R.string.ex_thor_stop2), false,
            R.string.ex_thor_desc
        ),
        Exercise(
            "scapular_retraction", R.string.ex_scap_name, "back",
            listOf("lsho", "rsho", "lhip", "rhip"), 0.5f, "shoulder_line_tilt", 0.0, 6.0, 10,
            listOf(FormRule("trunk_tilt_frontal", 8.0, R.string.cue_stay_tall)),
            listOf(R.string.ex_scap_stop1), false,
            R.string.ex_scap_desc
        ),
    )
    fun byId(id: String) = exercises.firstOrNull { it.id == id }
    fun indexOf(id: String) = exercises.indexOfFirst { it.id == id }
}
