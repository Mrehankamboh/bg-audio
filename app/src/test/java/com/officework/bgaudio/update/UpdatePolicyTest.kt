package com.officework.bgaudio.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val json = """
        {"tag_name":"v4","assets":[
          {"name":"notes.txt","browser_download_url":"https://github.com/Mrehankamboh/bg-audio/releases/download/v4/notes.txt"},
          {"name":"bg-audio.apk","browser_download_url":"https://github.com/Mrehankamboh/bg-audio/releases/download/v4/bg-audio.apk"}
        ]}
    """.trimIndent()

    @Test
    fun readsVersionAndApk() {
        assertEquals("v4", UpdatePolicy.tagName(json))
        assertEquals(4, UpdatePolicy.versionCode(UpdatePolicy.tagName(json)))
        assertEquals(
            "https://github.com/Mrehankamboh/bg-audio/releases/download/v4/bg-audio.apk",
            UpdatePolicy.apkUrl(json)
        )
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(UpdatePolicy.versionCode("v1.2"))
        assertFalse(UpdatePolicy.allowedReleaseUrl("https://example.com/app.apk"))
        assertTrue(UpdatePolicy.allowedDownloadHost("release-assets.githubusercontent.com"))
        assertFalse(UpdatePolicy.allowedDownloadHost("example.com"))
    }
}
