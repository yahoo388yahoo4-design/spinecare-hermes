package com.spinecare.hermes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class SleepFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val root = ScrollView(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        root.addView(col)
        fun h(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFF38BDF8.toInt()); textSize = 19f; setPadding(0, 20, 0, 6) }
        fun p(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFFE5E7EB.toInt()); textSize = 13f; setPadding(0, 2, 0, 2) }

        col.addView(h(getString(R.string.sleep_title)))
        p(getString(R.string.sleep_1))
        p(getString(R.string.sleep_2))
        p(getString(R.string.sleep_3))
        p(getString(R.string.sleep_4))
        p(getString(R.string.sleep_5))
        p(getString(R.string.sleep_6))

        col.addView(h(getString(R.string.sleep_track_title)))
        p(getString(R.string.sleep_track_1))
        p(getString(R.string.sleep_track_2))
        p(getString(R.string.sleep_track_3))
        p(getString(R.string.sleep_track_4))

        col.addView(h(getString(R.string.sleep_pillow_title)))
        p(getString(R.string.sleep_pillow_1))
        p(getString(R.string.sleep_pillow_2))

        col.addView(h(getString(R.string.tab_sleep)))
        val hc = HealthConnectHelper(ctx)
        p(getString(if (hc.isAvailable()) R.string.sleep_hc_yes else R.string.sleep_hc_no))
        return root
    }
}
