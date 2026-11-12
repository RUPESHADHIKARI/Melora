package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import com.metrolist.music.updater.UpdateDownloader
import com.metrolist.music.updater.UpdateInfo
import com.metrolist.music.updater.UpdateInstaller
import com.metrolist.music.updater.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Unit coverage for the MELORA updater's version logic:
 * the trusted `latest.json` manifest shape, the "no downgrade/only update"
 * versionCode rule and the guarded download-host allowlist.
 */
@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    private fun manifest(versionCode: Long, versionName: String = "1.0.$versionCode") =
        UpdateManifest(
            versionName = versionName,
            versionCode = versionCode,
            apkUrl = "https://github.com/RUPESHADHIKARI/Melora/releases/download/v$versionName/Melora-v$versionName.apk",
            sha256 = "a".repeat(64),
            releaseNotes = listOf("A", "B"),
        )

    @Test
    fun parsesLatestJsonManifest() {
        val json =
            """
            {
              "versionName": "1.0.3",
              "versionCode": 4,
              "apkUrl": "https://github.com/RUPESHADHIKARI/Melora/releases/download/v1.0.3/Melora-v1.0.3.apk",
              "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
              "releaseNotes": ["Improved music player", "Fixed playback bugs", "Improved performance"],
              "mandatory": false,
              "someFutureField": true
            }
            """.trimIndent()

        val parsed = Updater.json.decodeFromString(UpdateManifest.serializer(), json)

        assertEquals("1.0.3", parsed.versionName)
        assertEquals(4L, parsed.versionCode)
        assertEquals(
            "https://github.com/RUPESHADHIKARI/Melora/releases/download/v1.0.3/Melora-v1.0.3.apk",
            parsed.apkUrl,
        )
        assertEquals(3, parsed.releaseNotes.size)
        assertFalse(parsed.mandatory)
    }

    @Test
    fun updateNeverOffersDowngrade() {
        // Installed build is at least as new as the manifest: no update.
        val same = manifest(BuildConfig.VERSION_CODE.toLong())
        assertFalse(Updater.hasUpdate(info(same)))

        // Manifest is newer: update.
        val newer = manifest(BuildConfig.VERSION_CODE.toLong() + 1)
        assertTrue(Updater.hasUpdate(info(newer)))
    }

    @Test
    fun compareVersionsOrdersTagsForChangelog() {
        assertEquals(0, Updater.compareVersions("1.0.2", "1.0.2"))
        assertEquals(1, Updater.compareVersions("1.0.3", "1.0.2"))
        assertEquals(-1, Updater.compareVersions("1.0.2", "1.0.10"))
        assertEquals(1, Updater.compareVersions("v1.1.0", "v1.0.9"))
    }

    @Test
    fun apkDownloadTargetsOnlyTrustedGithubHosts() {
        // Real release asset URLs are allowed.
        assertTrue(
            UpdateDownloader.isTargetUrlAllowed(
                "https://github.com/RUPESHADHIKARI/Melora/releases/download/v1.0.3/Melora-v1.0.3.apk",
            ),
        )

        // Anything else (http, foreign hosts, HTML pages, signed CDN URLs)
        // is rejected before a single byte is fetched.
        assertFalse(UpdateDownloader.isTargetUrlAllowed("http://github.com/a/b.apk"))
        assertFalse(UpdateDownloader.isTargetUrlAllowed("https://evil.example.com/melora.apk"))
        assertFalse(UpdateDownloader.isTargetUrlAllowed("https://github.com/RUPESHADHIKARI/Melora/releases"))
        assertFalse(UpdateDownloader.isTargetUrlAllowed("https://objects.githubusercontent.com/a/b/c.apk"))
        assertFalse(UpdateDownloader.isTargetUrlAllowed("not a url"))
    }

    @Test
    fun cleanupKeepsPendingNewerApkAndRemovesStaleArtifacts() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.filesDir, "updates").apply { mkdirs() }

        // Pending install for a newer build: must survive the restart so the
        // "ready to install" offer can be restored after process death.
        val pending = File(dir, "Melora-v161.apk").apply { writeText("apk") }
        // Artifacts at or below the installed versionCode (and unknown
        // names) can never be installed again — stale.
        val older = File(dir, "Melora-v155.apk").apply { writeText("apk") }
        val junk = File(dir, "leftover.apk").apply { writeText("apk") }
        // Partial downloads never survive a restart.
        val partial = File(dir, "Melora-v161.apk.part").apply { writeText("part") }

        UpdateInstaller.cleanupOldApks(context, installedVersionCode = 155L)

        assertTrue("pending newer APK must be kept", pending.exists())
        assertFalse("older APK must be removed", older.exists())
        assertFalse("unparseable name must be removed", junk.exists())
        assertFalse("partial download must be removed", partial.exists())
    }

    private fun info(manifest: UpdateManifest) =
        UpdateInfo(
            manifest = manifest,
            releaseTitle = "v${manifest.versionName}",
            publishedAt = null,
            releaseBody = null,
            tag = "v${manifest.versionName}",
        )
}
