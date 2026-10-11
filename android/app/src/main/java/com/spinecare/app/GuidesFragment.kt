package com.spinecare.app

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Guides tab: offline how-to videos (reference clips processed by the same
 *  pose pipeline: skeleton, angles and cues burned in) + intro cards that teach
 *  camera placement. Bundled in assets/guides; "Download from server" refreshes
 *  them (and can add more) when the GPU server is reachable. */
class GuidesFragment : Fragment() {

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val root = ScrollView(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        root.addView(col)

        fun h(t: String) = TextView(ctx).apply { text = t; setTextColor(0xFF38BDF8.toInt()); textSize = 20f; setPadding(0, 16, 0, 8) }
        fun p(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFFE5E7EB.toInt()); textSize = 13f; setPadding(0, 4, 0, 4)
        }

        h(getString(R.string.guides_title))
        p(getString(R.string.guides_intro))

        val manifest = readManifest(ctx)
        if (manifest == null) {
            p("guides.json missing from assets")
            return root
        }
        val guides = manifest.getJSONArray("guides")
        for (gi in 0 until guides.length()) {
            val g = guides.getJSONObject(gi)
            val file = guideFile(ctx, g.getString("file"))
            val exId = g.getString("exercise_id")
            val ex = Plan.byId(exId.substringBeforeLast("_nhs", exId))
            val title = ex?.let { getString(it.nameRes) } ?: exId
            col.addView(h(title))
            if (file.exists()) {
                val vv = VideoView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 240))
                    setVideoURI(Uri.fromFile(file))
                }
                val mc = MediaController(ctx); mc.setAnchorView(vv); vv.setMediaController(mc)
                vv.setOnPreparedListener { mp: MediaPlayer -> mp.isLooping = false; vv.start() }
                col.addView(vv)
                p("${g.getInt("bytes") / 1000} KB")
            } else p("guide file missing: ${g.getString("file")}")
        }

        col.addView(Button(ctx).apply {
            text = getString(R.string.guides_download)
            setOnClickListener { downloadFromServer() }
        })
        p(getString(R.string.guides_offline_note))
        return root
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun readManifest(ctx: Context): JSONObject? = try {
        JSONObject(ctx.assets.open("guides/guides.json").reader().readText())
    } catch (e: Exception) { null }

    /** Guides live in filesDir (server-refreshed copies win); assets are the
     *  offline fallback copied on first use. */
    private fun guideFile(ctx: Context, name: String): File {
        val f = File(ctx.filesDir, "guides/$name")
        if (!f.exists()) try {
            f.parentFile?.mkdirs()
            ctx.assets.open("guides/$name").use { input ->
                FileOutputStream(f).use { out -> input.copyTo(out) }
            }
        } catch (_: Exception) { }
        return f
    }

    private fun downloadFromServer() {
        val ctx = context ?: return
        val url = ServerConfig.url(ctx).trimEnd('/') + "/guides"
        Thread {
            try {
                val con = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                con.connectTimeout = 5000; con.readTimeout = 10000
                con.requestMethod = "GET"
                val u = ServerConfig.user(ctx); val pw = ServerConfig.pass(ctx)
                if (u.isNotEmpty()) con.setRequestProperty("Authorization",
                    "Basic " + android.util.Base64.encodeToString(
                        "$u:$pw".toByteArray(), android.util.Base64.NO_WRAP))
                val manifest = JSONObject(con.inputStream.reader().readText())
                val arr: JSONArray = manifest.getJSONArray("guides")
                val dir = File(ctx.filesDir, "guides").apply { mkdirs() }
                for (gi in 0 until arr.length()) {
                    val g = arr.getJSONObject(gi)
                    val name = g.getString("file")
                    java.net.URL("${url}/$name").openConnection().also { c ->
                        c.connectTimeout = 5000; c.readTimeout = 60000
                        if (u.isNotEmpty()) c.setRequestProperty("Authorization",
                            "Basic " + android.util.Base64.encodeToString(
                                "$u:$pw".toByteArray(), android.util.Base64.NO_WRAP))
                    }.inputStream.use { input ->
                        FileOutputStream(File(dir, name)).use { out -> input.copyTo(out) }
                    }
                }
                activity?.runOnUiThread {
                    if (activity != null) android.widget.Toast.makeText(
                        ctx, getString(R.string.guides_downloaded), android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (activity != null) android.widget.Toast.makeText(
                        ctx, getString(R.string.guides_dl_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
