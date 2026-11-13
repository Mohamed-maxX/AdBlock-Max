package com.max.adblock

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.max.adblock.databinding.ActivityAppFirewallBinding
import com.max.adblock.databinding.DialogAppTrackerDetailsBinding
import com.max.adblock.databinding.DialogSetQuotaBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppFirewallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppFirewallBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: AppFirewallAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var currentFilter = FilterType.ALL
    private var searchQuery = ""

    enum class FilterType {
        ALL,
        USER,
        SYSTEM,
        BLOCKED,
        RISK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppFirewallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                initFirewallUi()
            }
        } else {
            initFirewallUi()
        }
    }

    private fun initFirewallUi() {
        setupToolbar()
        setupFilters()
        setupSearch()
        loadInstalledApps()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            performCyberpunkHaptic()
            finish()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFilters() {
        fun updateChipColors(selected: FilterType) {
            currentFilter = selected
            val chips = listOf(
                Pair(binding.chipAll, FilterType.ALL),
                Pair(binding.chipUser, FilterType.USER),
                Pair(binding.chipSystem, FilterType.SYSTEM),
                Pair(binding.chipBlocked, FilterType.BLOCKED),
                Pair(binding.chipRisk, FilterType.RISK)
            )
            for ((chip, type) in chips) {
                if (type == selected) {
                    chip.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                    chip.strokeColor = ContextCompat.getColorStateList(this, R.color.neon_cyan)
                } else {
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    chip.strokeColor = ContextCompat.getColorStateList(this, R.color.card_stroke)
                }
            }
            applyFilterAndSearch()
        }

        binding.chipAll.setOnClickListener {
            performCyberpunkHaptic()
            updateChipColors(FilterType.ALL)
        }
        binding.chipUser.setOnClickListener {
            performCyberpunkHaptic()
            updateChipColors(FilterType.USER)
        }
        binding.chipSystem.setOnClickListener {
            performCyberpunkHaptic()
            updateChipColors(FilterType.SYSTEM)
        }
        binding.chipBlocked.setOnClickListener {
            performCyberpunkHaptic()
            updateChipColors(FilterType.BLOCKED)
        }
        binding.chipRisk.setOnClickListener {
            performCyberpunkHaptic()
            updateChipColors(FilterType.RISK)
        }
    }

    private fun loadInstalledApps() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.rvApps.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppTrackerScanner.scanAllInstalledApps(this@AppFirewallActivity)
            }
            allApps = apps

            binding.pbLoading.visibility = View.GONE
            binding.rvApps.visibility = View.VISIBLE

            setupRecyclerView()
            applyFilterAndSearch()

            // Run Quota Check
            QuotaManager.checkAppQuotas(this@AppFirewallActivity, allApps) { exceededApp ->
                Toast.makeText(
                    this@AppFirewallActivity,
                    getString(R.string.toast_quota_exceeded, exceededApp.appName, "${exceededApp.dailyQuotaMb}MB"),
                    Toast.LENGTH_LONG
                ).show()
                applyFilterAndSearch()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = AppFirewallAdapter(
            context = this,
            appsList = emptyList(),
            onBypassChanged = { app, isBypassed ->
                performCyberpunkHaptic()
                prefs.setAppBypassed(app.packageName, isBypassed)
                if (isBypassed) {
                    prefs.setAppBlocked(app.packageName, false)
                    Toast.makeText(this, getString(R.string.toast_app_bypassed, app.appName), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.toast_app_unbypassed, app.appName), Toast.LENGTH_SHORT).show()
                }
                syncVpnRules()
            },
            onBlockChanged = { app, isBlocked ->
                performCyberpunkHaptic()
                prefs.setAppBlocked(app.packageName, isBlocked)
                if (isBlocked) {
                    prefs.setAppBypassed(app.packageName, false)
                    Toast.makeText(this, getString(R.string.toast_app_blocked, app.appName), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.toast_app_unblocked, app.appName), Toast.LENGTH_SHORT).show()
                }
                syncVpnRules()
            },
            onSetQuotaClicked = { app ->
                performCyberpunkHaptic()
                showSetQuotaDialog(app)
            },
            onTrackerDetailsClicked = { app ->
                performCyberpunkHaptic()
                showTrackerDetailsDialog(app)
            }
        )

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun applyFilterAndSearch() {
        var filtered = allApps

        // Filter Type
        filtered = when (currentFilter) {
            FilterType.ALL -> filtered
            FilterType.USER -> filtered.filter { !it.isSystemApp }
            FilterType.SYSTEM -> filtered.filter { it.isSystemApp }
            FilterType.BLOCKED -> filtered.filter { it.isFirewallBlocked }
            FilterType.RISK -> filtered.filter { it.batteryDrainRisk == BatteryDrainRisk.HIGH }
        }

        // Search Query
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        adapter.updateList(filtered)
        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSetQuotaDialog(app: AppInfo) {
        val dialogBinding = DialogSetQuotaBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvQuotaDialogTitle.text = "كوتة استهلاك: ${app.appName}"

        // Pre-select current quota
        when (app.dailyQuotaMb) {
            50 -> dialogBinding.rbQuota50.isChecked = true
            100 -> dialogBinding.rbQuota100.isChecked = true
            250 -> dialogBinding.rbQuota250.isChecked = true
            500 -> dialogBinding.rbQuota500.isChecked = true
            else -> dialogBinding.rbQuotaUnlimited.isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCancelQuota.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveQuota.setOnClickListener {
            performCyberpunkHaptic()
            val selectedMb = when {
                dialogBinding.rbQuota50.isChecked -> 50
                dialogBinding.rbQuota100.isChecked -> 100
                dialogBinding.rbQuota250.isChecked -> 250
                dialogBinding.rbQuota500.isChecked -> 500
                else -> 0
            }

            app.dailyQuotaMb = selectedMb
            prefs.setAppQuota(app.packageName, selectedMb)

            val quotaText = if (selectedMb > 0) "$selectedMb MB" else getString(R.string.firewall_quota_unlimited)
            Toast.makeText(this, getString(R.string.toast_quota_set, app.appName, quotaText), Toast.LENGTH_SHORT).show()

            adapter.notifyDataSetChanged()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTrackerDetailsDialog(app: AppInfo) {
        val dialogBinding = DialogAppTrackerDetailsBinding.inflate(LayoutInflater.from(this))

        dialogBinding.ivDialogAppIcon.setImageDrawable(app.icon)
        dialogBinding.tvDialogAppName.text = app.appName
        dialogBinding.tvDialogPackageName.text = app.packageName

        // Risk Level Title & Color
        when (app.batteryDrainRisk) {
            BatteryDrainRisk.HIGH -> {
                dialogBinding.tvDialogRiskTitle.text = "مستوى استنزاف البطارية والمخاطر: مرتفع 🔴"
                dialogBinding.tvDialogRiskTitle.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
                dialogBinding.tvDialogRiskDesc.text = "يحتوي هذا التطبيق على خدمات إعلانية ومستقبلات تتبع تعمل بالخلفية وتستنزف البطارية وبيانات الباقة."
            }
            BatteryDrainRisk.MEDIUM -> {
                dialogBinding.tvDialogRiskTitle.text = "مستوى استنزاف البطارية والمخاطر: متوسط 🟡"
                dialogBinding.tvDialogRiskTitle.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
                dialogBinding.tvDialogRiskDesc.text = "يحتوي على شبكات إعلانية معتدلة تنشط عند فتح التطبيق."
            }
            BatteryDrainRisk.LOW -> {
                dialogBinding.tvDialogRiskTitle.text = "مستوى استنزاف البطارية والمخاطر: آمن / خالي 🟢"
                dialogBinding.tvDialogRiskTitle.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
                dialogBinding.tvDialogRiskDesc.text = "هذا التطبيق نظيف وخالي من بصمات شبكات التتبع الإعلانية المعروفة."
            }
        }

        // Trackers list
        if (app.trackersFound.isNotEmpty()) {
            dialogBinding.tvDialogTrackersList.text = app.trackersFound.joinToString("\n• ", prefix = "• ")
        } else {
            dialogBinding.tvDialogTrackersList.text = "لا توجد شبكات إعلانية مشبوهة مكتشفة."
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnDialogClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogIsolate.setOnClickListener {
            performCyberpunkHaptic()
            app.isFirewallBlocked = true
            app.isBypassed = false
            prefs.setAppBlocked(app.packageName, true)
            prefs.setAppBypassed(app.packageName, false)
            adapter.notifyDataSetChanged()
            syncVpnRules()
            Toast.makeText(this, getString(R.string.toast_app_blocked, app.appName), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun syncVpnRules() {
        if (prefs.isVpnActive && !prefs.isVpnPaused) {
            val intent = Intent(this, AdBlockVpnService::class.java).apply {
                action = AdBlockVpnService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun performCyberpunkHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (e: Exception) {
            // Ignored
        }
    }
}
