package com.officework.bgaudio.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.officework.bgaudio.R

data class AppRow(val packageName: String, val label: String)

class AppListAdapter(
    private val onToggle: (packageName: String, allowed: Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.Holder>() {
    private var all = emptyList<AppRow>()
    private var shown = emptyList<AppRow>()
    private var query = ""
    private var allowAll = false
    private var allowed = emptySet<String>()
    var onEmptyChanged: ((Boolean) -> Unit)? = null

    fun submit(apps: List<AppRow>, allowAll: Boolean, allowed: Set<String>) {
        all = apps
        this.allowAll = allowAll
        this.allowed = allowed
        applyFilter()
    }

    fun setAccess(allowAll: Boolean, allowed: Set<String>) {
        this.allowAll = allowAll
        this.allowed = allowed
        notifyDataSetChanged()
    }

    fun filter(text: String) {
        query = text.trim()
        applyFilter()
    }

    override fun getItemCount(): Int = shown.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = shown[position]
        holder.label.text = row.label
        holder.pkg.text = row.packageName
        holder.toggle.contentDescription = row.label
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = allowAll || allowed.contains(row.packageName)
        holder.toggle.isEnabled = !allowAll
        holder.toggle.setOnCheckedChangeListener { _, on ->
            onToggle(row.packageName, on)
        }
        val pm = holder.icon.context.packageManager
        try {
            holder.icon.setImageDrawable(pm.getApplicationIcon(row.packageName))
        } catch (_: PackageManager.NameNotFoundException) {
            holder.icon.setImageResource(R.drawable.ic_stat)
        }
    }

    private fun applyFilter() {
        val q = query.lowercase()
        shown = if (q.isEmpty()) {
            all
        } else {
            all.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
        onEmptyChanged?.invoke(shown.isEmpty())
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
        val pkg: TextView = view.findViewById(R.id.appPackage)
        val toggle: SwitchMaterial = view.findViewById(R.id.appToggle)
    }

    companion object {
        fun load(pm: PackageManager, selfPackage: String): List<AppRow> {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            return resolved.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == selfPackage) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                AppRow(pkg, label)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
    }
}
