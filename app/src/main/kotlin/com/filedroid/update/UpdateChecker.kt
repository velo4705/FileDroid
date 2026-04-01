package com.filedroid.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val latestVersion: String,
    val latestVersionCode: Int,
    val releaseUrl: String,
    val releaseNotes: String,
    val downloadUrl: String? = null,
    val isUpdateAvailable: Boolean = false
)

/**
 * Checks GitHub releases for updates.
 * Uses the public GitHub API: https://api.github.com/repos/velo4705/FileDroid/releases/latest
 */
@Singleton
class UpdateChecker @Inject constructor() {

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/velo4705/FileDroid/releases/latest"
        private const val RELEASES_PAGE = "https://github.com/velo4705/FileDroid/releases/latest"
    }

    /**
     * Check for updates against the latest GitHub release.
     * @param currentVersionCode the app's current versionCode from BuildConfig
     * @param currentVersionName the app's current versionName from BuildConfig
     */
    suspend fun checkForUpdates(
        currentVersionCode: Int,
        currentVersionName: String
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val json = URL(RELEASES_API).readText()
            val release = JSONObject(json)

            val tagName = release.optString("tag_name", "").removePrefix("v").trim()
            val releaseNotes = release.optString("body", "No release notes.")
            val htmlUrl = release.optString("html_url", RELEASES_PAGE)

            // Find the APK asset download URL
            val assets = release.optJSONArray("assets")
            val apkUrl = findApkDownloadUrl(assets)

            // Parse version from tag (e.g., "2.0.0" from "v2.0.0")
            val latestVersionCode = parseVersionCode(tagName)
            val currentVersionCodeParsed = parseVersionCode(currentVersionName)
            val isUpdateAvailable = latestVersionCode > currentVersionCodeParsed

            UpdateInfo(
                latestVersion = tagName,
                latestVersionCode = latestVersionCode,
                releaseUrl = htmlUrl,
                releaseNotes = releaseNotes,
                downloadUrl = apkUrl,
                isUpdateAvailable = isUpdateAvailable
            )
        }
    }

    /** Open the releases page in the browser. */
    fun openReleasesPage(context: Context, url: String = RELEASES_PAGE) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Download and install the APK (opens browser to the download URL). */
    fun openDownload(context: Context, downloadUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun findApkDownloadUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk")) return url
        }
        return null
    }

    /**
     * Parse a version string like "2.0.0" into an comparable integer code.
     * Format: major * 10000 + minor * 100 + patch (e.g., 2.0.0 = 20000)
     */
    private fun parseVersionCode(version: String): Int {
        val parts = version.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }
}
