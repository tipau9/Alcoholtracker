package de.tipau.promille.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.tipau.promille.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList()
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val release: GitHubRelease,
        val apkAsset: GitHubAsset,
        val currentVersion: String,
        val newVersion: String
    ) : UpdateCheckResult()

    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class NoApkFound(val release: GitHubRelease, val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String, val cause: Throwable? = null) : UpdateCheckResult()
}

/**
 * Service managing GitHub Releases auto-updates for tipau9/Alcoholtracker.
 * Handles querying latest releases, semver version comparison, chunked downloading
 * with progress callbacks, and launching the system package installer.
 */
object AppUpdateService {

    const val GITHUB_REPO_OWNER = "tipau9"
    const val GITHUB_REPO_NAME = "Alcoholtracker"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Checks GitHub API for the latest release and compares with the local [currentVersion].
     */
    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Alcoholtracker-Android-App")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("GitHub API Fehler: HTTP $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val release = jsonParser.decodeFromString<GitHubRelease>(responseBody)

            val cleanRemote = cleanVersion(release.tagName)
            val cleanLocal = cleanVersion(currentVersion)

            if (isNewerVersion(cleanRemote, cleanLocal)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset != null) {
                    UpdateCheckResult.UpdateAvailable(
                        release = release,
                        apkAsset = apkAsset,
                        currentVersion = cleanLocal,
                        newVersion = cleanRemote
                    )
                } else {
                    UpdateCheckResult.NoApkFound(release, cleanLocal)
                }
            } else {
                UpdateCheckResult.UpToDate(cleanLocal)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Unbekannter Netzwerkfehler", e)
        }
    }

    /**
     * Compares semantic versions. Returns true if [remoteVersion] > [currentVersion].
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(cleanVersion(remoteVersion))
        val localParts = parseVersionParts(cleanVersion(currentVersion))
        val maxLen = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun cleanVersion(v: String): String {
        return v.trim().removePrefix("v").removePrefix("V").trim()
    }

    private fun parseVersionParts(v: String): List<Int> {
        val numericPart = v.split("-")[0].split("+")[0]
        return numericPart.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Downloads the APK file to [destinationFile] and emits progress.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) destinationFile.delete()

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            val maxRedirects = 5

            // Follow redirects for GitHub release asset downloads (e.g. S3 / CDN)
            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Alcoholtracker-Android-App")
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    val loc = connection.getHeaderField("Location") ?: break
                    currentUrl = loc
                    redirectCount++
                    if (redirectCount > maxRedirects) throw IllegalStateException("Zu viele Weiterleitungen")
                } else {
                    break
                }
            }

            val totalBytes = connection.contentLengthLong
            val input = connection.inputStream
            val output = FileOutputStream(destinationFile)

            val buffer = ByteArray(8 * 1024)
            var downloadedBytes = 0L
            var bytesRead: Int

            input.use { inStream ->
                output.use { outStream ->
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                }
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the app has permission to install unknown apps (Android 8.0+).
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the system settings screen to allow installing unknown apps.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Creates an Intent to launch the system package installer for the downloaded APK.
     */
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}

