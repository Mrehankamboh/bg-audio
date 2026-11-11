package com.officework.bgaudio.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.officework.bgaudio.R
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.keep.KeepCoordinator
import com.officework.bgaudio.service.KeepAliveService
import com.officework.bgaudio.service.PackageWatchService
import com.officework.bgaudio.service.SessionKeepService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var adapter: AppListAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var rendering = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        renderPermissions()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderSwitches()
            renderDebug()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        val list = findViewById<RecyclerView>(R.id.appList)
        val empty = findViewById<TextView>(R.id.emptyApps)
        adapter = AppListAdapter { pkg, on ->
            prefs.setAllowed(pkg, on)
            adapter.setAccess(prefs.allowAll, prefs.allowedPackages())
        }
        adapter.onEmptyChanged = { isEmpty ->
            empty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<SwitchMaterial>(R.id.switchEnabled).setOnCheckedChangeListener { _, on ->
            if (rendering) return@setOnCheckedChangeListener
            prefs.enabled = on
            if (on) {
                try {
                    KeepAliveService.start(this)
                } catch (e: RuntimeException) {
                    prefs.enabled = false
                    prefs.lastPlayResult = "service start failed: ${e.message}"
                    renderDebug()
                    renderSwitches()
                }
            } else {
                startService(Intent(this, KeepAliveService::class.java).setAction(KeepAliveService.ACTION_STOP))
            }
        }
        findViewById<SwitchMaterial>(R.id.switchAllowAll).setOnCheckedChangeListener { _, on ->
            if (rendering) return@setOnCheckedChangeListener
            prefs.allowAll = on
            adapter.setAccess(on, prefs.allowedPackages())
        }
        findViewById<EditText>(R.id.filterApps).addTextChangedListener(SimpleText { adapter.filter(it) })

        findViewById<android.view.View>(R.id.listenerButton).setOnClickListener {
            openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<android.view.View>(R.id.accessibilityButton).setOnClickListener {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<android.view.View>(R.id.batteryButton).setOnClickListener { openBattery() }
        findViewById<android.view.View>(R.id.overlayButton).setOnClickListener {
            openSettings(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<android.view.View>(R.id.notificationButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        executor.execute {
            val apps = AppListAdapter.load(packageManager, packageName)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submit(apps, prefs.allowAll, prefs.allowedPackages())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(KeepCoordinator.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        renderSwitches()
        renderPermissions()
        renderDebug()
        if (::adapter.isInitialized) adapter.setAccess(prefs.allowAll, prefs.allowedPackages())
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun renderSwitches() {
        rendering = true
        findViewById<SwitchMaterial>(R.id.switchEnabled).isChecked = prefs.enabled
        findViewById<SwitchMaterial>(R.id.switchAllowAll).isChecked = prefs.allowAll
        rendering = false
    }

    private fun renderPermissions() {
        status(R.id.listenerStatus, notificationListenerOn(), getString(R.string.perm_listener_hint))
        status(R.id.accessibilityStatus, accessibilityOn(), getString(R.string.perm_accessibility_hint))
        status(R.id.batteryStatus, batteryIgnored(), getString(R.string.perm_battery_hint))
        status(R.id.overlayStatus, Settings.canDrawOverlays(this), getString(R.string.perm_overlay_hint))
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            status(R.id.notificationStatus, granted, getString(R.string.perm_notifications_hint))
        } else {
            findViewById<TextView>(R.id.notificationStatus).text = getString(R.string.status_not_needed)
            findViewById<android.view.View>(R.id.notificationButton).visibility = android.view.View.GONE
        }
    }

    private fun renderDebug() {
        val fg = prefs.lastForeground.ifBlank { "—" }
        val session = prefs.lastSessionState.ifBlank { "—" }
        val result = prefs.lastPlayResult.ifBlank { "—" }
        findViewById<TextView>(R.id.debugText).text = "Foreground: $fg\nSession: $session\nResult: $result"
    }

    private fun status(viewId: Int, on: Boolean, hint: String) {
        val label = if (on) getString(R.string.status_on) else getString(R.string.status_off)
        val view = findViewById<TextView>(viewId)
        view.text = "$label · $hint"
        view.setTextColor(getColor(if (on) R.color.accent else R.color.warn))
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            prefs.lastPlayResult = "settings screen missing"
            renderDebug()
        }
    }

    private fun openBattery() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: ActivityNotFoundException) {
            openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun notificationListenerOn(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return componentListed(flat, SessionKeepService::class.java)
    }

    private fun accessibilityOn(): Boolean {
        val master = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!master) return false
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return componentListed(flat, PackageWatchService::class.java)
    }

    private fun componentListed(flat: String, type: Class<*>): Boolean {
        val name = ComponentName(this, type)
        return flat.split(':').any {
            it.equals(name.flattenToString(), ignoreCase = true) ||
                it.equals(name.flattenToShortString(), ignoreCase = true)
        }
    }

    private fun batteryIgnored(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(packageName)
    }
}
