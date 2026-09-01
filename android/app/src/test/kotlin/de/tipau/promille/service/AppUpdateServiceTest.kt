package de.tipau.promille.service

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateServiceTest {

    @Test
    fun cleanVersion_stripsPrefixesAndWhitespace() {
        assertEquals("1.2.3", AppUpdateService.cleanVersion("v1.2.3"))
        assertEquals("0.1.0", AppUpdateService.cleanVersion("V0.1.0"))
        assertEquals("2.0.0", AppUpdateService.cleanVersion("  v2.0.0  "))
        assertEquals("3.4.5", AppUpdateService.cleanVersion("3.4.5"))
    }

    @Test
    fun isNewerVersion_correctlyComparesSemVer() {
        // Newer patch
        assertTrue(AppUpdateService.isNewerVersion("v0.1.1", "0.1.0"))
        // Newer minor
        assertTrue(AppUpdateService.isNewerVersion("v0.2.0", "0.1.9"))
        // Newer major
        assertTrue(AppUpdateService.isNewerVersion("v2.0.0", "1.9.9"))
        // Multi-digit minor comparison
        assertTrue(AppUpdateService.isNewerVersion("v1.10.0", "1.9.0"))
        // Same version
        assertFalse(AppUpdateService.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateService.isNewerVersion("0.1.0", "0.1.0"))
        // Older version
        assertFalse(AppUpdateService.isNewerVersion("v0.1.0", "0.2.0"))
        assertFalse(AppUpdateService.isNewerVersion("1.0.0", "1.0.1"))
        // Pre-release tags
        assertTrue(AppUpdateService.isNewerVersion("v1.0.1-beta1", "1.0.0"))
    }

    @Test
    fun jsonParsing_extractsApkAssetAndReleaseDetails() {
        val sampleJson = """
        {
          "tag_name": "v0.2.0",
          "name": "Version 0.2.0 - Jam & QuickAdd Polish",
          "body": "* Added auto-update\n* Performance improvements",
          "html_url": "https://github.com/tipau9/Alcoholtracker/releases/tag/v0.2.0",
          "assets": [
            {
              "name": "app-release.apk",
              "browser_download_url": "https://github.com/tipau9/Alcoholtracker/releases/download/v0.2.0/app-release.apk",
              "size": 14923841,
              "content_type": "application/vnd.android.package-archive"
            },
            {
              "name": "checksums.txt",
              "browser_download_url": "https://github.com/tipau9/Alcoholtracker/releases/download/v0.2.0/checksums.txt",
              "size": 128,
              "content_type": "text/plain"
            }
          ]
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val release = json.decodeFromString<GitHubRelease>(sampleJson)

        assertEquals("v0.2.0", release.tagName)
        assertEquals("Version 0.2.0 - Jam & QuickAdd Polish", release.name)
        assertEquals(2, release.assets.size)

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        assertNotNull(apkAsset)
        assertEquals("app-release.apk", apkAsset?.name)
        assertEquals(14923841L, apkAsset?.size)
        assertEquals("https://github.com/tipau9/Alcoholtracker/releases/download/v0.2.0/app-release.apk", apkAsset?.downloadUrl)
    }
}
