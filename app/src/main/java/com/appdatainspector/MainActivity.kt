package com.appdatainspector

import android.app.AppOpsManager
import android.app.AlertDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var apps: List<AppEntry> = emptyList()
    private var usageByPackage: Map<String, Long> = emptyMap()

    data class AppEntry(
        val label: String,
        val packageName: String,
        val version: String,
        val requestedPermissions: List<String>,
        val isSystem: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshData()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refreshData()
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(0xFF0B1220.toInt())
        }
        val header = TextView(this).apply {
            text = "App Data Inspector"
            textSize = 25f
            setTextColor(0xFFF4F7FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(header)
        val subtitle = TextView(this).apply {
            text = "Installed apps • Usage • Permissions • Privacy"
            textSize = 13f
            setTextColor(0xFF9EACC4.toInt())
            setPadding(0, dp(5), 0, dp(14))
        }
        root.addView(subtitle)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refresh = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refreshData() }
        }
        val usage = Button(this).apply {
            text = "Enable Usage Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        actions.addView(refresh, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(usage, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)

        search = EditText(this).apply {
            hint = "Search app name or package"
            singleLine = true
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8795AD.toInt())
            setPadding(dp(12), 0, dp(12), 0)
            addTextChangedListener(SimpleTextWatcher { renderApps() })
        }
        root.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(12); bottomMargin = dp(8) })

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun refreshData() {
        try {
            val pm = packageManager
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val result = installed.mapNotNull { info ->
                try {
                    @Suppress("DEPRECATION")
                    val pkg = pm.getPackageInfo(info.packageName, PackageManager.GET_PERMISSIONS)
                    val label = pm.getApplicationLabel(info).toString()
                    AppEntry(
                        label = label,
                        packageName = info.packageName,
                        version = pkg.versionName ?: "Unknown",
                        requestedPermissions = pkg.requestedPermissions?.toList() ?: emptyList(),
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                } catch (_: Exception) { null }
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }
            apps = result
            usageByPackage = readUsageStats()
            renderApps()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read app inventory: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun readUsageStats(): Map<String, Long> {
        if (!hasUsageAccess()) return emptyMap()
        return try {
            val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24L * 60L * 60L * 1000L
            manager.queryAndAggregateUsageStats(start, end).mapValues { it.value.totalTimeInForeground }
        } catch (_: Exception) { emptyMap() }
    }

    private fun renderApps() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = search.text.toString().trim().lowercase(Locale.getDefault())
        val filtered = apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.lowercase(Locale.getDefault()).contains(query)
        }
        val summary = TextView(this).apply {
            text = "${filtered.size} apps shown • Usage window: last 24 hours"
            textSize = 12f
            setTextColor(0xFF9EACC4.toInt())
            setPadding(0, dp(6), 0, dp(10))
        }
        list.addView(summary)
        filtered.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(0xFF172338.toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener { showAppDetails(entry) }
            }
            val title = TextView(this).apply {
                text = entry.label
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFF4F7FF.toInt())
            }
            val packageLine = TextView(this).apply {
                text = entry.packageName
                textSize = 11f
                setTextColor(0xFF9EACC4.toInt())
            }
            val usageMs = usageByPackage[entry.packageName] ?: 0L
            val usageLine = TextView(this).apply {
                val minutes = usageMs / 60000L
                text = "Version ${entry.version}  •  Usage: ${minutes} min  •  ${entry.requestedPermissions.size} requested permissions" +
                    if (entry.isSystem) "  •  System" else ""
                textSize = 12f
                setTextColor(0xFFB9C8DE.toInt())
                setPadding(0, dp(5), 0, 0)
            }
            card.addView(title)
            card.addView(packageLine)
            card.addView(usageLine)
            list.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(9) })
        }
        if (filtered.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No matching apps found."
                setTextColor(0xFFFFFFFF.toInt())
            })
        }
    }

    private fun showAppDetails(entry: AppEntry) {
        val usageMinutes = (usageByPackage[entry.packageName] ?: 0L) / 60000L
        val permissionText = if (entry.requestedPermissions.isEmpty()) {
            "No requested permissions listed."
        } else {
            entry.requestedPermissions.joinToString("\n") { permission ->
                val short = permission.substringAfterLast('.')
                "• $short"
            }
        }
        val message = buildString {
            append("App: ${entry.label}\n")
            append("Package: ${entry.packageName}\n")
            append("Version: ${entry.version}\n")
            append("Foreground usage (24h): ${usageMinutes} minutes\n")
            append("Type: ${if (entry.isSystem) "System app" else "User app"}\n\n")
            append("Declared permissions:\n")
            append(permissionText)
            append("\n\nPrivacy note: Android does not allow this app to read another app's private files, login tokens, cookies, sessions, or private database.")
        }
        AlertDialog.Builder(this)
            .setTitle("App details")
            .setMessage(message)
            .setPositiveButton("Open Android App Info") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${entry.packageName}")
                }
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class SimpleTextWatcher(val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
