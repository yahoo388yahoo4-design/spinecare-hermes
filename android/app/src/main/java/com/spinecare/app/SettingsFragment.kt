package com.spinecare.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.spinecare.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Full configuration page: coaching mode, GPU server host/port/user/password,
 *  frame rates, confidence, haptics/audio, data export. */
class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val ctx = requireContext()
        when (ServerConfig.lang(ctx)) {
            "en" -> b.rbLangEn.isChecked = true
            "ro" -> b.rbLangRo.isChecked = true
            else -> b.rbLangSystem.isChecked = true
        }
        b.etUrl.setText(ServerConfig.url(ctx))
        b.etUser.setText(ServerConfig.user(ctx))
        b.etPass.setText(ServerConfig.pass(ctx))
        b.etFrameMs.setText(ServerConfig.frameIntervalMs(ctx).toString())
        b.etServerFrameMs.setText(ServerConfig.serverFrameIntervalMs(ctx).toString())
        b.etMinConf.setText(ServerConfig.minConfidence(ctx).toString())
        b.swHaptics.isChecked = ServerConfig.haptics(ctx)
        b.swAudio.isChecked = ServerConfig.audioCues(ctx)
        b.swRecord.isChecked = ServerConfig.recordSessions(ctx)
        when (ServerConfig.mode(ctx)) {
            ServerConfig.MODE_ON_DEVICE -> b.rbOnDevice.isChecked = true
            ServerConfig.MODE_SERVER -> b.rbServer.isChecked = true
            else -> b.rbAuto.isChecked = true
        }

        b.btnSave.setOnClickListener {
            val ctx2 = requireContext()
            val newLang = when {
                b.rbLangEn.isChecked -> "en"
                b.rbLangRo.isChecked -> "ro"
                else -> "system"
            }
            val langChanged = newLang != ServerConfig.lang(ctx2)
            ServerConfig.setLang(ctx2, newLang)
            ServerConfig.setUrl(ctx2, b.etUrl.text.toString().trim().ifEmpty { ServerConfig.url(ctx2) })
            ServerConfig.setUser(ctx2, b.etUser.text.toString().trim())
            ServerConfig.setPass(ctx2, b.etPass.text.toString())
            ServerConfig.setFrameIntervalMs(ctx2, b.etFrameMs.text.toString().toIntOrNull() ?: 150)
            ServerConfig.setServerFrameIntervalMs(ctx2, b.etServerFrameMs.text.toString().toIntOrNull() ?: 100)
            ServerConfig.setMinConfidence(ctx2, b.etMinConf.text.toString().toFloatOrNull() ?: 0.5f)
            ServerConfig.setHaptics(ctx2, b.swHaptics.isChecked)
            ServerConfig.setAudioCues(ctx2, b.swAudio.isChecked)
            ServerConfig.setRecordSessions(ctx2, b.swRecord.isChecked)
            ServerConfig.setMode(ctx2, when {
                b.rbOnDevice.isChecked -> ServerConfig.MODE_ON_DEVICE
                b.rbServer.isChecked -> ServerConfig.MODE_SERVER
                else -> ServerConfig.MODE_AUTO
            })
            if (langChanged) {
                // Re-apply the locale override by recreating the host activity.
                activity?.recreate()
            } else {
                Toast.makeText(ctx2, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            }
        }

        b.btnTest.setOnClickListener {
            b.tvServerStatus.text = getString(R.string.settings_testing, ServerConfig.url(requireContext()))
            testConnection()
        }

        b.btnExport.setOnClickListener {
            try {
                val n = LocalStore(requireContext()).exportCsv()
                Toast.makeText(requireContext(), getString(R.string.settings_exported, n), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.settings_export_fail, e.message ?: "?"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun testConnection() {
        val ctx = requireContext()
        val url = ServerConfig.url(ctx).trimEnd('/') + "/health"
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val req = Request.Builder().url(url).apply {
                    val u = ServerConfig.user(ctx)
                    if (u.isNotEmpty()) header("Authorization", Credentials.basic(u, ServerConfig.pass(ctx)))
                }.build()
                http.newCall(req).execute().use { r ->
                    if (r.isSuccessful) "OK — ${r.body?.string()?.take(120)}"
                    else "HTTP ${r.code}: ${r.message}"
                }
            }.getOrElse { "Unreachable: ${it.message}" }
            withContext(Dispatchers.Main) { _b?.tvServerStatus?.text = result }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
