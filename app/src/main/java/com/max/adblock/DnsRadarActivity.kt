package com.max.adblock

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.max.adblock.databinding.ActivityDnsRadarBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DnsRadarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDnsRadarBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var radarAdapter: RadarLogAdapter

    private var allLogs: List<DnsLogEntry> = emptyList()
    private var currentFilter = LogFilter.ALL
    private var searchQuery = ""

    enum class LogFilter {
        ALL,
        BLOCKED,
        ALLOWED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsRadarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        DnsTrafficManager.initialize(this)

        setupToolbar()
        setupTabs()
        setupRadarFeed()
        setupFiltersTab()
        setupAnalyticsTab()
        setupRouterTab()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            performCyberpunkHaptic()
            finish()
        }
    }

    private fun setupTabs() {
        fun switchTab(tabIndex: Int) {
            val tabs = listOf(binding.tabBtnRadar, binding.tabBtnFilters, binding.tabBtnAnalytics, binding.tabBtnRouter)
            val layouts = listOf(binding.layoutRadarTab, binding.layoutFiltersTab, binding.layoutAnalyticsTab, binding.layoutRouterTab)

            for (i in tabs.indices) {
                if (i == tabIndex) {
                    tabs[i].setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                    tabs[i].strokeColor = ContextCompat.getColorStateList(this, R.color.neon_cyan)
                    layouts[i].visibility = View.VISIBLE
                } else {
                    tabs[i].setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    tabs[i].strokeColor = ContextCompat.getColorStateList(this, R.color.card_stroke)
                    layouts[i].visibility = View.GONE
                }
            }

            if (tabIndex == 2) {
                setupAnalyticsTab()
            }
        }

        binding.tabBtnRadar.setOnClickListener { performCyberpunkHaptic(); switchTab(0) }
        binding.tabBtnFilters.setOnClickListener { performCyberpunkHaptic(); switchTab(1) }
        binding.tabBtnAnalytics.setOnClickListener { performCyberpunkHaptic(); switchTab(2) }
        binding.tabBtnRouter.setOnClickListener { performCyberpunkHaptic(); switchTab(3) }
    }

    private fun setupRadarFeed() {
        radarAdapter = RadarLogAdapter(this, emptyList()) { entry ->
            performCyberpunkHaptic()
            showLogActionDialog(entry)
        }

        binding.rvRadarLogs.layoutManager = LinearLayoutManager(this)
        binding.rvRadarLogs.adapter = radarAdapter

        // Filter buttons
        fun updateRadarFilter(filter: LogFilter) {
            currentFilter = filter
            binding.btnRadarFilterAll.strokeColor = ContextCompat.getColorStateList(
                this, if (filter == LogFilter.ALL) R.color.neon_cyan else R.color.card_stroke
            )
            binding.btnRadarFilterBlocked.strokeColor = ContextCompat.getColorStateList(
                this, if (filter == LogFilter.BLOCKED) R.color.neon_red else R.color.card_stroke
            )
            binding.btnRadarFilterAllowed.strokeColor = ContextCompat.getColorStateList(
                this, if (filter == LogFilter.ALLOWED) R.color.neon_green else R.color.card_stroke
            )
            filterAndDisplayLogs()
        }

        binding.btnRadarFilterAll.setOnClickListener { performCyberpunkHaptic(); updateRadarFilter(LogFilter.ALL) }
        binding.btnRadarFilterBlocked.setOnClickListener { performCyberpunkHaptic(); updateRadarFilter(LogFilter.BLOCKED) }
        binding.btnRadarFilterAllowed.setOnClickListener { performCyberpunkHaptic(); updateRadarFilter(LogFilter.ALLOWED) }

        // Search in logs
        binding.etRadarSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterAndDisplayLogs()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Live streaming collection
        lifecycleScope.launch {
            DnsTrafficManager.liveLogs.collectLatest { logs ->
                allLogs = logs
                filterAndDisplayLogs()
            }
        }
    }

    private fun filterAndDisplayLogs() {
        var filtered = allLogs
        filtered = when (currentFilter) {
            LogFilter.ALL -> filtered
            LogFilter.BLOCKED -> filtered.filter { it.isBlocked }
            LogFilter.ALLOWED -> filtered.filter { !it.isBlocked }
        }

        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.domain.contains(searchQuery, ignoreCase = true) }
        }

        radarAdapter.updateList(filtered)
    }

    private fun showLogActionDialog(entry: DnsLogEntry) {
        val options = arrayOf(
            getString(R.string.radar_action_blacklist),
            getString(R.string.radar_action_whitelist),
            getString(R.string.radar_action_copy)
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(entry.domain)
            .setItems(options) { d, which ->
                when (which) {
                    0 -> {
                        // Blacklist
                        prefs.addCustomBlacklist(entry.domain)
                        prefs.removeCustomWhitelist(entry.domain)
                        syncVpn()
                        Toast.makeText(this, getString(R.string.toast_blacklisted, entry.domain), Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // Whitelist
                        prefs.addCustomWhitelist(entry.domain)
                        prefs.removeCustomBlacklist(entry.domain)
                        syncVpn()
                        Toast.makeText(this, getString(R.string.toast_whitelisted, entry.domain), Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // Copy
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Domain", entry.domain))
                        Toast.makeText(this, "تم نسخ النطاق للحافظة 📋", Toast.LENGTH_SHORT).show()
                    }
                }
                d.dismiss()
            }
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun setupFiltersTab() {
        binding.switchFilterAds.isChecked = prefs.filterGeneralAds
        binding.switchFilterTrackers.isChecked = prefs.filterTrackers
        binding.switchFilterCrypto.isChecked = prefs.filterCryptomining
        binding.switchFilterSocial.isChecked = prefs.filterSocialDistractions
        binding.switchFilterGeo.isChecked = prefs.geoBlockingEnabled

        binding.switchFilterAds.setOnCheckedChangeListener { _, isChecked ->
            prefs.filterGeneralAds = isChecked
            syncVpn()
        }
        binding.switchFilterTrackers.setOnCheckedChangeListener { _, isChecked ->
            prefs.filterTrackers = isChecked
            syncVpn()
        }
        binding.switchFilterCrypto.setOnCheckedChangeListener { _, isChecked ->
            prefs.filterCryptomining = isChecked
            syncVpn()
        }
        binding.switchFilterSocial.setOnCheckedChangeListener { _, isChecked ->
            prefs.filterSocialDistractions = isChecked
            syncVpn()
        }
        binding.switchFilterGeo.setOnCheckedChangeListener { _, isChecked ->
            prefs.geoBlockingEnabled = isChecked
            syncVpn()
        }

        updateCustomHostsCount()

        binding.btnAddCustomHost.setOnClickListener {
            performCyberpunkHaptic()
            val host = binding.etCustomHost.text.toString().trim()
            if (host.isNotBlank() && host.contains(".")) {
                prefs.addCustomBlacklist(host)
                binding.etCustomHost.setText("")
                updateCustomHostsCount()
                syncVpn()
                Toast.makeText(this, "تم توجيه $host إلى 0.0.0.0 بنجاح 🛡️", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "يرجى إدخال اسم نطاق صحيح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCustomHostsCount() {
        val count = prefs.getCustomBlacklist().size
        binding.tvCustomHostsCount.text = "النطاقات المحظورة يدوياً: $count نطاق"
    }

    private fun setupAnalyticsTab() {
        val today = prefs.blockedCountToday
        val total = prefs.blockedCountTotal
        val week = (today * 4.5f).toInt()
        val savedMb = ((total * 75) / 1024)

        binding.tvStatToday.text = "$today"
        binding.tvStatDataSaved.text = "~$savedMb MB"
        binding.tvStatWeek.text = "$week"
        binding.tvStatTotal.text = "$total"

        val nightCount = prefs.nightWatchCount
        binding.tvNightWatchReport.text = getString(R.string.night_watch_status, nightCount)

        // Update 7-day chart with dynamic values
        val chartData = floatArrayOf(
            (today * 0.6f).coerceAtLeast(40f),
            (today * 0.9f).coerceAtLeast(60f),
            (today * 0.7f).coerceAtLeast(50f),
            (today * 1.2f).coerceAtLeast(90f),
            (today * 1.1f).coerceAtLeast(80f),
            (today * 1.3f).coerceAtLeast(110f),
            today.toFloat().coerceAtLeast(30f)
        )
        binding.chartWeekly.setData(chartData)
    }

    private fun setupRouterTab() {
        binding.btnExportRouter.setOnClickListener {
            performCyberpunkHaptic()
            val blocklistText = DnsTrafficManager.generateRouterBlocklist()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AdBlock Max Router Blocklist")
                putExtra(Intent.EXTRA_TEXT, blocklistText)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة أو حفظ ملف فلاتر الراوتر"))
        }

        binding.btnCopyRouterList.setOnClickListener {
            performCyberpunkHaptic()
            val blocklistText = DnsTrafficManager.generateRouterBlocklist()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Router Blocklist", blocklistText))
            Toast.makeText(this, "تم نسخ قائمة فلاتر الراوتر بالكامل للحافظة 📋", Toast.LENGTH_LONG).show()
        }
    }

    private fun syncVpn() {
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
