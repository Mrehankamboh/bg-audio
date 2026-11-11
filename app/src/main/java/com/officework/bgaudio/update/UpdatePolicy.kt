package com.officework.bgaudio.update

object UpdatePolicy {
    fun tagName(json: String): String? = jsonString(json, "tag_name")

    fun versionCode(tag: String?): Int? {
        if (tag.isNullOrBlank()) return null
        return tag.trim().removePrefix("v").removePrefix("V").toIntOrNull()
    }

    fun apkUrl(json: String): String? {
        var from = 0
        val token = "\"browser_download_url\""
        while (from < json.length) {
            val i = json.indexOf(token, from)
            if (i < 0) return null
            val value = readStringAfter(json, i + token.length) ?: return null
            if (value.endsWith(".apk", ignoreCase = true)) return value
            from = i + token.length
        }
        return null
    }

    fun allowedReleaseUrl(url: String): Boolean {
        return url.startsWith("https://github.com/Mrehankamboh/bg-audio/releases/download/")
    }

    fun allowedDownloadHost(host: String): Boolean {
        return host == "github.com" ||
            host == "release-assets.githubusercontent.com" ||
            host == "objects.githubusercontent.com" ||
            host == "github-releases.githubusercontent.com"
    }

    private fun jsonString(json: String, key: String): String? {
        val token = "\"$key\""
        val i = json.indexOf(token)
        if (i < 0) return null
        return readStringAfter(json, i + token.length)
    }

    private fun readStringAfter(json: String, start: Int): String? {
        val colon = json.indexOf(':', start)
        if (colon < 0) return null
        val open = json.indexOf('"', colon + 1)
        if (open < 0) return null
        val close = json.indexOf('"', open + 1)
        if (close < 0) return null
        return json.substring(open + 1, close)
    }
}
