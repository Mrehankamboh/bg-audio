package com.officework.bgaudio.data

import android.content.Context

class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val selfPackage = appContext.packageName

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) {
            sp.edit().putBoolean(KEY_ENABLED, value).commit()
        }

    var allowAll: Boolean
        get() = sp.getBoolean(KEY_ALLOW_ALL, false)
        set(value) {
            sp.edit().putBoolean(KEY_ALLOW_ALL, value).commit()
        }

    var lastForeground: String
        get() = sp.getString(KEY_FG, "").orEmpty()
        set(value) {
            sp.edit().putString(KEY_FG, value).commit()
        }

    var lastSessionState: String
        get() = sp.getString(KEY_SESSION, "").orEmpty()
        set(value) {
            sp.edit().putString(KEY_SESSION, value).commit()
        }

    var lastPlayResult: String
        get() = sp.getString(KEY_RESULT, "").orEmpty()
        set(value) {
            sp.edit().putString(KEY_RESULT, value).commit()
        }

    var warnPackage: String
        get() = sp.getString(KEY_WARN, "").orEmpty()
        set(value) {
            sp.edit().putString(KEY_WARN, value).commit()
        }

    fun allowedPackages(): Set<String> = sp.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet()

    fun isAllowed(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == selfPackage) return false
        if (allowAll) return true
        return allowedPackages().contains(pkg)
    }

    fun setAllowed(pkg: String, on: Boolean) {
        val next = allowedPackages().toMutableSet()
        if (on) next.add(pkg) else next.remove(pkg)
        sp.edit().putStringSet(KEY_PACKAGES, next).commit()
    }

    fun writeStatus(session: String, result: String, warn: String?) {
        val editor = sp.edit()
            .putString(KEY_SESSION, session)
            .putString(KEY_RESULT, result)
        if (warn != null) editor.putString(KEY_WARN, warn)
        editor.commit()
    }

    companion object {
        private const val FILE = "bg_audio"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALLOW_ALL = "allow_all"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_FG = "last_foreground"
        private const val KEY_SESSION = "last_session"
        private const val KEY_RESULT = "last_result"
        private const val KEY_WARN = "warn_package"
    }
}
