package com.spinecare.hermes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val store = LocalStore(ctx)
        val root = ScrollView(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        root.addView(col)

        fun h(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFF38BDF8.toInt()); textSize = 20f; setPadding(0, 16, 0, 8) }
        fun p(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFFE5E7EB.toInt()); textSize = 14f; setPadding(0, 4, 0, 4) }

        col.addView(h(getString(R.string.app_name)))
        col.addView(p(getString(R.string.disclaimer)))

        // Clinical follow-up reminders (clinician-set, not self-prescribed)
        col.addView(h(getString(R.string.followup_title)))
        col.addView(p(getString(R.string.followup_c6)))
        col.addView(p(getString(R.string.followup_physio)))
        col.addView(p(getString(R.string.followup_redflags)))

        // Daily check-in
        col.addView(h(getString(R.string.checkin_title)))
        val neck = SeekBar(ctx).apply { max = 10; progress = 3 }
        val arm = SeekBar(ctx).apply { max = 10; progress = 2 }
        val sleep = SeekBar(ctx).apply { max = 10; progress = 6 }
        col.addView(p(getString(R.string.checkin_neck))); col.addView(neck)
        col.addView(p(getString(R.string.checkin_arm))); col.addView(arm)
        col.addView(p(getString(R.string.checkin_sleep))); col.addView(sleep)
        val save = android.widget.Button(ctx).apply { text = getString(R.string.checkin_save) }
        val status = TextView(ctx).apply { setTextColor(0xFF34D399.toInt()) }
        save.setOnClickListener {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            store.saveCheckin(today, neck.progress, arm.progress, sleep.progress, "")
            status.text = getString(R.string.checkin_saved, today)
        }
        col.addView(save); col.addView(status)

        // Recent sessions
        col.addView(h(getString(R.string.recent_title)))
        val recent = store.allSessions().takeLast(8).reversed()
        if (recent.isEmpty()) col.addView(p(getString(R.string.recent_empty)))
        recent.forEach { s ->
            val exName = Plan.byId(s.optString("exercise"))?.let { getString(it.nameRes) } ?: s.optString("exercise")
            col.addView(p("${s.optString("date")}  $exName  reps=${s.optInt("reps")}  [${s.optString("source")}]"))
        }
        return root
    }
}
