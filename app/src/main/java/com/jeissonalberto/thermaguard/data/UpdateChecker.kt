package com.jeissonalberto.thermaguard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isForced: Boolean = false
)

/**
 * UpdateChecker — Consulta GitHub Releases para verificar si hay
 * una versión nueva de ThermaGuard disponible.
 *
 * Flujo:
 *   1. Consulta GET /repos/JeissonAlberto/ThermaGuard/releases/latest
 *   2. Compara tag_name con la versión instalada
 *   3. Si hay nueva versión, devuelve AppUpdate con URL de descarga
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/JeissonAlberto/ThermaGuard/releases/latest"

    private const val PREFS_KEY       = "therma_updates"
    private const val KEY_LAST_CHECK  = "update_last_check"
    private const val KEY_AUTO_UPDATE = "update_auto_check"

    // Intervalo mínimo entre checks: 6 horas
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun isAutoCheckEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun setAutoCheck(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    /**
     * Verifica actualizaciones si pasaron más de 6h desde el último check.
     * Devuelve null si no hay actualización disponible o si aún no es hora.
     */
    suspend fun checkIfDue(context: Context): AppUpdate? {
        if (!isAutoCheckEnabled(context)) return null
        val prefs     = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return null
        val result = check(context)
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        return result
    }

    /** Verifica actualizaciones inmediatamente (llamada manual). */
    suspend fun check(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8_000
                readTimeout    = 8_000
            }
            if (conn.responseCode != 200) return@withContext null

            val json    = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag     = json.getString("tag_name")          // e.g. "v3.9.22"
            val notes   = json.optString("body", "Nueva versión disponible")
            val assets  = json.optJSONArray("assets")
            val current = getCurrentVersion(context)

            // Comparar versiones
            if (!isNewer(tag, current)) return@withContext null

            // Buscar APK en assets
            val apkUrl = if (assets != null && assets.length() > 0) {
                (0 until assets.length())
                    .mapNotNull { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.getString("browser_download_url")
                    ?: json.optString("html_url", "")
            } else {
                json.optString("html_url", "")
            }

            AppUpdate(
                version      = tag,
                releaseNotes = notes.take(500),
                downloadUrl  = apkUrl,
                isForced     = notes.contains("[FORCED]", ignoreCase = true)
            )
        } catch (e: Exception) {
            android.util.Log.w("ThermaGuard", "UpdateCheck error: ${e.message}")
            null
        }
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) { "0.0.0" }
    }

    /** Compara semver: devuelve true si remoteTag es más nuevo que localVersion */
    private fun isNewer(remoteTag: String, local: String): Boolean {
        return try {
            val r = remoteTag.trimStart('v').split(".").map { it.toInt() }
            val l = local.trimStart('v').split(".").map { it.toInt() }
            for (i in 0..2) {
                val ri = r.getOrElse(i) { 0 }
                val li = l.getOrElse(i) { 0 }
                if (ri > li) return true
                if (ri < li) return false
            }
            false
        } catch (_: Exception) { false }
    }
}
