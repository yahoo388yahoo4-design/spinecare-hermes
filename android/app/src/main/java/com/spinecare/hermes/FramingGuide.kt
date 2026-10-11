package com.spinecare.hermes

import android.content.Context

/** Maps exercise view + required landmarks to concrete camera-placement guidance. */
object FramingGuide {
    data class Guide(val headline: String, val steps: List<String>)

    fun forView(ctx: Context, view: String): Guide = when (view) {
        "side" -> Guide(
            ctx.getString(R.string.frame_side_head),
            listOf(
                ctx.getString(R.string.frame_side_1),
                ctx.getString(R.string.frame_side_2),
                ctx.getString(R.string.frame_side_3),
                ctx.getString(R.string.frame_side_4),
            )
        )
        "front" -> Guide(
            ctx.getString(R.string.frame_front_head),
            listOf(
                ctx.getString(R.string.frame_front_1),
                ctx.getString(R.string.frame_front_2),
                ctx.getString(R.string.frame_front_3),
            )
        )
        "back" -> Guide(
            ctx.getString(R.string.frame_back_head),
            listOf(
                ctx.getString(R.string.frame_back_1),
                ctx.getString(R.string.frame_back_2),
                ctx.getString(R.string.frame_back_3),
            )
        )
        else -> Guide(ctx.getString(R.string.frame_generic), emptyList())
    }

    /** Human-readable correction for a framing issue. */
    fun fixFor(ctx: Context, issue: String): String = when {
        issue.endsWith(":low_conf") -> ctx.getString(R.string.frame_low_conf)
        issue.endsWith(":out_of_frame") -> ctx.getString(R.string.frame_out_of_frame)
        issue == "no_person" -> ctx.getString(R.string.frame_no_person)
        else -> ctx.getString(R.string.frame_generic)
    }
}
