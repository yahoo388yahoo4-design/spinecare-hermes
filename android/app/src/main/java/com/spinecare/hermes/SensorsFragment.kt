package com.spinecare.hermes

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class SensorsFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val root = ScrollView(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        root.addView(col)

        fun h(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFF38BDF8.toInt()); textSize = 19f; setPadding(0, 20, 0, 6) }
        fun p(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFFE5E7EB.toInt()); textSize = 13f; setPadding(0, 2, 0, 2) }

        col.addView(h(getString(R.string.sensors_server_title)))
        val url = EditText(ctx).apply {
            setText(ServerConfig.url(ctx)); setTextColor(0xFFE5E7EB.toInt()); setSingleLine()
        }
        val status = TextView(ctx).apply { setTextColor(0xFF94A3B8.toInt()) }
        col.addView(url)
        col.addView(Button(ctx).apply {
            text = getString(R.string.sensors_test)
            setOnClickListener {
                ServerConfig.setUrl(ctx, url.text.toString())
                Thread {
                    val ok = try {
                        val conn = java.net.URL(ServerConfig.url(ctx) + "/health").openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 3000; conn.readTimeout = 3000
                        conn.responseCode == 200
                    } catch (e: Exception) { false }
                    activity?.runOnUiThread {
                        status.text = getString(if (ok) R.string.sensors_ok else R.string.sensors_fail)
                    }
                }.start()
            }
        })
        col.addView(status)

        col.addView(h(getString(R.string.sensors_phone_title)))
        p(getString(R.string.sensors_phone_cam))
        p(getString(R.string.sensors_phone_depth))
        p(getString(R.string.sensors_phone_imu))
        p(getString(R.string.sensors_phone_mic))

        col.addView(h(getString(R.string.sensors_ble_title)))
        p(getString(R.string.sensors_ble_1))
        p(getString(R.string.sensors_ble_2))
        p(getString(R.string.sensors_ble_3))
        p(getString(R.string.sensors_ble_4))
        col.addView(Button(ctx).apply {
            text = getString(R.string.sensors_scan)
            setOnClickListener {
                status.text = getString(R.string.sensors_scanning)
                val mgr = ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                val adapter = mgr.adapter
                if (adapter != null && adapter.isEnabled) {
                    @SuppressLint("MissingPermission")
                    adapter.startDiscovery()
                } else status.text = getString(R.string.sensors_bt_off)
            }
        })

        col.addView(h(getString(R.string.sensors_can_title)))
        p(getString(R.string.sensors_can))
        p(getString(R.string.sensors_cannot))

        return root
    }
}
