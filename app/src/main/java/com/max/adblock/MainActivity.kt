package com.max.adblock

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.max.adblock.databinding.ActivityMainBinding
import com.max.adblock.databinding.DialogCustomDnsBinding
import com.max.adblock.databinding.DialogTesterResultBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var automationManager: AutomationManager

    private var pulseAnimator: AnimatorSet? = null
    private var timerJob: Job? = null
    private var pingJob: Job? = null
    private var consecutivePingFailures = 0
    private var lastFailoverTriggerTimeMs = 0L

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnInternal()
        } else {
            Toast.makeText(this, R.string.toast_vpn_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled */ }

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AdBlockVpnService.ACTION_VPN_STATE_CHANGED) {
                val isActive = intent.getBooleanExtra(AdBlockVpnService.EXTRA_IS_ACTIVE, false)
                val isPaused = intent.getBooleanExtra(AdBlockVpnService.EXTRA_IS_PAUSED, false)
                updateUiState(isActive, isPaused)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        networkMonitor = NetworkMonitor(this)
        automationManager = AutomationManager(this)

        requestNotificationPermission()
        setupModeSelectors()
        setupPowerButton()
        setupTools()
        setupNetworkMonitor()

        // Phase 2 setups
        setupProfiles()
        setupBatteryWhitelist()
        setupAutomationSwitches()
        setupSleepTimer()

        // Handle Shortcut intents with App Lock verification
        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                handleShortcutIntent(intent)
            }
        } else {
            handleShortcutIntent(intent)
        }

        // Sync initial UI with current state
        updateUiState(prefs.isVpnActive, prefs.isVpnPaused)
        updateCustomDnsSummary()
        observeScreenRecordPrivacy()
    }

    private fun observeScreenRecordPrivacy() {
        lifecycleScope.launch {
            SmartSecurityGuards.isScreenRecordingActive.collect { recording ->
                if (recording) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                    binding.tvStatusSubtitle.text = "🎥 وضع الخصوصية نشط أثناء البث/التسجيل (البيانات مخفية)"
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                handleShortcutIntent(intent)
            }
        } else {
            handleShortcutIntent(intent)
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            "com.max.adblock.ACTION_SHORTCUT_START" -> {
                prepareAndStartVpn()
            }
            "com.max.adblock.ACTION_SHORTCUT_GAMING" -> {
                prefs.selectedMode = DnsMode.GAMING
                setupModeSelectors()
                prepareAndStartVpn()
            }
            "com.max.adblock.ACTION_SHORTCUT_FAMILY" -> {
                prefs.selectedMode = DnsMode.FAMILY
                setupModeSelectors()
                prepareAndStartVpn()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
        automationManager.start()
        SmartSecurityGuards.startScreenRecordMonitor(this)
        val filter = IntentFilter(AdBlockVpnService.ACTION_VPN_STATE_CHANGED)
        ContextCompat.registerReceiver(
            this,
            vpnStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        updateUiState(prefs.isVpnActive, prefs.isVpnPaused)
        updateBatteryStatus()
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.notifyAppForegrounded()
        updateBatteryStatus()
        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                // Successfully unlocked
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.notifyAppBackgrounded()
        networkMonitor.stop()
        automationManager.stop()
        try {
            unregisterReceiver(vpnStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopPulseAnimation()
        timerJob?.cancel()
        pingJob?.cancel()
        super.onDestroy()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupPowerButton() {
        binding.btnPower.setOnClickListener {
            performCyberpunkHaptic()
            if (prefs.isVpnActive && !prefs.isVpnPaused) {
                stopVpnInternal()
            } else {
                prepareAndStartVpn()
            }
        }
    }

    private fun prepareAndStartVpn() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPrepareLauncher.launch(prepareIntent)
        } else {
            startVpnInternal()
        }
    }

    private fun startVpnInternal() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        updateUiState(isActive = true, isPaused = false)
    }

    private fun stopVpnInternal() {
        if (prefs.smartAutoLearnWhitelist) {
            val lastApp = prefs.lastForegroundAppPkg
            if (lastApp.isNotBlank() && lastApp != packageName && !prefs.isAppBypassed(lastApp)) {
                val count = prefs.incrementAppPauseCount(lastApp)
                if (count >= 3) {
                    prefs.pendingWhitelistSuggestionPkg = lastApp
                }
            }
        }
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_STOP
        }
        ContextCompat.startForegroundService(this, intent)
        updateUiState(isActive = false, isPaused = false)
    }

    private fun updateUiState(isActive: Boolean, isPaused: Boolean) {
        if (isActive && !isPaused) {
            // Active Shield State
            binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_on)
            binding.ivPowerIcon.setColorFilter(ContextCompat.getColor(this, R.color.neon_cyan))
            binding.tvStatusTitle.setText(R.string.status_protected)
            binding.tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.neon_green))

            val failoverStatus = if (prefs.isFailoverActive) " • [Auto-Failover ⚡]" else ""
            binding.tvStatusSubtitle.text = "درع الحظر مفعل • تصفية وحماية تسريب IPv4 / IPv6$failoverStatus"

            startPulseAnimation()
            startSessionTimer()
            startPingMonitor()
        } else if (isPaused) {
            // Paused State
            binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off)
            binding.ivPowerIcon.setColorFilter(ContextCompat.getColor(this, R.color.neon_amber))
            binding.tvStatusTitle.text = "الحماية موقوفة مؤقتاً (5 دقائق)"
            binding.tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
            binding.tvStatusSubtitle.text = "سيتم استئناف درع الحظر تلقائياً"

            stopPulseAnimation()
            timerJob?.cancel()
            pingJob?.cancel()
            binding.tvPingValue.text = "-- ms"
        } else {
            // Inactive State
            binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off)
            binding.ivPowerIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvStatusTitle.setText(R.string.status_unprotected)
            binding.tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
            binding.tvStatusSubtitle.text = "اضغط على الزر لتفعيل درع الحظر وفلترة IPv4 / IPv6"

            stopPulseAnimation()
            timerJob?.cancel()
            pingJob?.cancel()
            binding.tvTimerValue.setText(R.string.initial_timer)
            binding.tvPingValue.setText(R.string.initial_ping)
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()

        val scaleX = ObjectAnimator.ofFloat(binding.pulseRing, View.SCALE_X, 1.0f, 1.25f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(binding.pulseRing, View.SCALE_Y, 1.0f, 1.25f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val alpha = ObjectAnimator.ofFloat(binding.pulseRing, View.ALPHA, 0.9f, 0.1f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.pulseRing.scaleX = 1.0f
        binding.pulseRing.scaleY = 1.0f
        binding.pulseRing.alpha = 0.2f
    }

    private fun startSessionTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val startTime = prefs.sessionStartTime
                if (startTime > 0L) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                    val hours = elapsedSeconds / 3600
                    val minutes = (elapsedSeconds % 3600) / 60
                    val seconds = elapsedSeconds % 60
                    binding.tvTimerValue.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                }
                delay(1000)
            }
        }
    }

    private fun startPingMonitor() {
        pingJob?.cancel()
        pingJob = lifecycleScope.launch {
            while (isActive) {
                if (!prefs.isVpnActive || prefs.isVpnPaused) {
                    consecutivePingFailures = 0
                    binding.tvPingValue.text = "-- ms"
                    delay(4000)
                    continue
                }

                val mode = prefs.selectedMode
                val targetIp = if (mode == DnsMode.CUSTOM) {
                    prefs.customIpv4
                } else if (prefs.isFailoverActive && mode.secondaryIpv4.isNotBlank()) {
                    mode.secondaryIpv4
                } else {
                    mode.primaryIpv4
                }

                val ping = PingTester.measurePingMs(targetIp)
                if (ping in 1..800) {
                    consecutivePingFailures = 0
                    binding.tvPingValue.text = "$ping ms"
                } else {
                    consecutivePingFailures++
                    binding.tvPingValue.text = if (ping > 800) "$ping ms ⚠️" else "-- ms"

                    // Strict Auto-Failover: 3 consecutive failures + failover enabled + 5 min cooldown
                    if (consecutivePingFailures >= 3 && prefs.autoFailoverEnabled && prefs.isVpnActive) {
                        triggerAutoFailover()
                    }
                }
                delay(5000)
            }
        }
    }

    private fun triggerAutoFailover() {
        val now = System.currentTimeMillis()
        val cooldownMs = 5 * 60 * 1000L // 5 minutes cooldown
        if (now - lastFailoverTriggerTimeMs < cooldownMs) {
            return
        }
        lastFailoverTriggerTimeMs = now
        consecutivePingFailures = 0

        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_TRIGGER_FAILOVER
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun setupNetworkMonitor() {
        lifecycleScope.launch {
            networkMonitor.networkState.collect { netType ->
                automationManager.onNetworkChanged(netType)
                when (netType) {
                    NetworkType.WIFI -> {
                        binding.ivNetworkIcon.setImageResource(R.drawable.ic_wifi)
                        binding.ivNetworkIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.neon_cyan))
                        binding.tvNetworkValue.setText(R.string.net_wifi)
                    }
                    NetworkType.CELLULAR -> {
                        binding.ivNetworkIcon.setImageResource(R.drawable.ic_wifi)
                        binding.ivNetworkIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.neon_blue))
                        binding.tvNetworkValue.setText(R.string.net_cellular)
                    }
                    NetworkType.DISCONNECTED -> {
                        binding.ivNetworkIcon.setImageResource(R.drawable.ic_wifi)
                        binding.ivNetworkIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.neon_red))
                        binding.tvNetworkValue.setText(R.string.net_disconnected)
                    }
                }
            }
        }
    }

    private fun setupModeSelectors() {
        val modeCards = listOf(
            Triple(binding.cardModeStandard, binding.radioStandard, DnsMode.STANDARD),
            Triple(binding.cardModeSecurity, binding.radioSecurity, DnsMode.SECURITY),
            Triple(binding.cardModeFamily, binding.radioFamily, DnsMode.FAMILY),
            Triple(binding.cardModeGaming, binding.radioGaming, DnsMode.GAMING),
            Triple(binding.cardModeCustom, binding.radioCustom, DnsMode.CUSTOM)
        )

        fun updateSelectedMode(selected: DnsMode) {
            prefs.selectedMode = selected
            for ((card, radio, mode) in modeCards) {
                val isCurrent = (mode == selected)
                radio.isChecked = isCurrent
                card.setBackgroundResource(
                    if (isCurrent) R.drawable.bg_neon_card_selected else R.drawable.bg_neon_card
                )
            }

            // Restart VPN with new mode if active
            if (prefs.isVpnActive && !prefs.isVpnPaused) {
                startVpnInternal()
            }
        }

        // Apply initial visual selection
        updateSelectedMode(prefs.selectedMode)

        for ((card, _, mode) in modeCards) {
            card.setOnClickListener {
                performCyberpunkHaptic()
                if (mode == DnsMode.CUSTOM) {
                    showCustomDnsDialog {
                        updateSelectedMode(DnsMode.CUSTOM)
                        updateCustomDnsSummary()
                    }
                } else {
                    updateSelectedMode(mode)
                }
            }
        }
    }

    private fun updateCustomDnsSummary() {
        binding.tvCustomDnsSummary.text = "IPv4: ${prefs.customIpv4} • IPv6: ${prefs.customIpv6}"
    }

    // Phase 2: Profiles
    private fun setupProfiles() {
        binding.cardProfileGamer.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.applyProfile("GAMER")
            setupModeSelectors()
            syncAutomationSwitches()
            Toast.makeText(this, getString(R.string.toast_profile_applied, getString(R.string.profile_gamer_title)), Toast.LENGTH_SHORT).show()
        }

        binding.cardProfileFocus.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.applyProfile("FOCUS")
            setupModeSelectors()
            syncAutomationSwitches()
            Toast.makeText(this, getString(R.string.toast_profile_applied, getString(R.string.profile_focus_title)), Toast.LENGTH_SHORT).show()
        }

        binding.cardProfileSaver.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.applyProfile("SAVER")
            setupModeSelectors()
            syncAutomationSwitches()
            Toast.makeText(this, getString(R.string.toast_profile_applied, getString(R.string.profile_saver_title)), Toast.LENGTH_SHORT).show()
        }
    }

    // Phase 2: Battery Whitelist
    private fun setupBatteryWhitelist() {
        updateBatteryStatus()
        binding.cardBatteryWhitelist.setOnClickListener {
            performCyberpunkHaptic()
            requestBatteryOptimizationExemption()
        }
    }

    private fun updateBatteryStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        if (isIgnoring) {
            binding.tvBatteryStatus.setText(R.string.battery_status_optimized)
            binding.tvBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else {
            binding.tvBatteryStatus.setText(R.string.battery_status_restricted)
            binding.tvBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "تعذر فتح إعدادات البطارية تلقائياً", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "التطبيق مستثنى بالفعل من قيود البطارية ✅", Toast.LENGTH_SHORT).show()
        }
    }

    // Phase 2: Automation Switches
    private fun setupAutomationSwitches() {
        syncAutomationSwitches()

        binding.switchAutoBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoStartOnBoot = isChecked
        }

        binding.switchAutoWifi.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoOnWifi = isChecked
        }

        binding.switchAutoCellular.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoOnCellular = isChecked
        }

        binding.switchScreenOff.setOnCheckedChangeListener { _, isChecked ->
            prefs.screenOffLockdown = isChecked
        }

        binding.switchAutoFailover.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoFailoverEnabled = isChecked
            if (!isChecked) {
                consecutivePingFailures = 0
            }
        }

        binding.switchKillSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.killSwitchEnabled = isChecked
            // If VPN active, restart with Kill Switch setting
            if (prefs.isVpnActive && !prefs.isVpnPaused) {
                startVpnInternal()
            }
        }

        binding.switchBlockAutoUpdates.setOnCheckedChangeListener { _, isChecked ->
            prefs.blockAutoUpdatesOnCellular = isChecked
            if (prefs.isVpnActive && !prefs.isVpnPaused) {
                startVpnInternal()
            }
        }
    }

    private fun syncAutomationSwitches() {
        binding.switchAutoBoot.isChecked = prefs.autoStartOnBoot
        binding.switchAutoWifi.isChecked = prefs.autoOnWifi
        binding.switchAutoCellular.isChecked = prefs.autoOnCellular
        binding.switchScreenOff.isChecked = prefs.screenOffLockdown
        binding.switchAutoFailover.isChecked = prefs.autoFailoverEnabled
        binding.switchKillSwitch.isChecked = prefs.killSwitchEnabled
        binding.switchBlockAutoUpdates.isChecked = prefs.blockAutoUpdatesOnCellular
    }

    // Phase 2: Sleep Timer
    private fun setupSleepTimer() {
        updateSleepTimerDisplay()

        binding.btnSleepOff.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.setSleepTimer(0) { updateSleepTimerDisplay() }
            Toast.makeText(this, R.string.toast_sleep_timer_disabled, Toast.LENGTH_SHORT).show()
            updateSleepTimerDisplay()
        }

        binding.btnSleep30.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.setSleepTimer(30) { updateSleepTimerDisplay() }
            Toast.makeText(this, getString(R.string.toast_sleep_timer_set, "30 دقيقة"), Toast.LENGTH_SHORT).show()
            updateSleepTimerDisplay()
        }

        binding.btnSleep60.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.setSleepTimer(60) { updateSleepTimerDisplay() }
            Toast.makeText(this, getString(R.string.toast_sleep_timer_set, "ساعة واحدة"), Toast.LENGTH_SHORT).show()
            updateSleepTimerDisplay()
        }

        binding.btnSleep120.setOnClickListener {
            performCyberpunkHaptic()
            automationManager.setSleepTimer(120) { updateSleepTimerDisplay() }
            Toast.makeText(this, getString(R.string.toast_sleep_timer_set, "ساعتان"), Toast.LENGTH_SHORT).show()
            updateSleepTimerDisplay()
        }
    }

    private fun updateSleepTimerDisplay() {
        val minutes = prefs.sleepTimerMinutes
        if (minutes > 0) {
            binding.tvSleepTimerStatus.text = "مؤقت النوم نشط: سيتم إيقاف الحماية تلقائياً بعد $minutes دقيقة"
            binding.tvSleepTimerStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
        } else {
            binding.tvSleepTimerStatus.setText(R.string.sleep_timer_desc)
            binding.tvSleepTimerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupTools() {
        // Tool 1: AdBlock Tester
        binding.btnAdBlockTester.setOnClickListener {
            performCyberpunkHaptic()
            showAdBlockTesterDialog()
        }

        // Tool 2: DNS Flush
        binding.btnDnsFlush.setOnClickListener {
            performCyberpunkHaptic()
            if (prefs.isVpnActive && !prefs.isVpnPaused) {
                startVpnInternal()
            }
            Toast.makeText(this, R.string.toast_dns_flushed, Toast.LENGTH_SHORT).show()
        }

        // Tool 3: Private DNS Shortcut
        binding.btnPrivateDns.setOnClickListener {
            performCyberpunkHaptic()
            val host = prefs.selectedMode.dotHostname
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Private DNS", host)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(
                this,
                getString(R.string.toast_private_dns_copied, host),
                Toast.LENGTH_LONG
            ).show()

            openSystemNetworkSettings()
        }

        // Phase 3 Tool 4: App Firewall & Management
        binding.btnAppFirewall.setOnClickListener {
            performCyberpunkHaptic()
            val intent = Intent(this, AppFirewallActivity::class.java)
            startActivity(intent)
        }

        // Phase 4 Tool 5: Live DNS Radar & Analytics
        binding.btnDnsRadar.setOnClickListener {
            performCyberpunkHaptic()
            val intent = Intent(this, DnsRadarActivity::class.java)
            startActivity(intent)
        }

        // Phase 5 Tool 6: Network & Router Security Suite
        binding.btnNetworkTools.setOnClickListener {
            performCyberpunkHaptic()
            val intent = Intent(this, NetworkToolsActivity::class.java)
            startActivity(intent)
        }

        // Phase 6 Tool 7: Built-in Ad-Free Browser
        binding.btnBrowser.setOnClickListener {
            performCyberpunkHaptic()
            val intent = Intent(this, BrowserActivity::class.java)
            startActivity(intent)
        }

        // Phase 6 Tool 8: Privacy Suite, App Lock & Backup
        binding.btnPrivacySuite.setOnClickListener {
            performCyberpunkHaptic()
            val intent = Intent(this, PrivacySuiteActivity::class.java)
            startActivity(intent)
        }

        // v2.1.0: Smart AI Hub
        binding.btnSmartHub.setOnClickListener {
            performCyberpunkHaptic()
            startActivity(Intent(this, SmartHubActivity::class.java))
        }

        // v2.1.0 Feature 6: One-Tap Smart Clean
        binding.btnOneTapClean.setOnClickListener {
            performCyberpunkHaptic()
            binding.btnOneTapClean.isEnabled = false
            lifecycleScope.launch {
                val report = SmartSecurityGuards.performOneTapClean(this@MainActivity)
                binding.btnOneTapClean.isEnabled = true
                val msg = buildString {
                    append("⚡ التنظيف الشامل:\n")
                    append("• كاش DNS: ${if (report.dnsFlushed) "تم التحديث" else "متوقف"}\n")
                    append("• الحافظة: ${if (report.clipboardCleaned) "تنظيف ${report.removedTrackerParams} متتبع" else "نظيفة"}\n")
                    append("• ملفات APK بالتنزيلات: ${report.suspiciousApksFound}")
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }

        // Auto-Update Checker (Top Header & Main Card)
        val currentVer = UpdateManager.getCurrentVersion(this)
        binding.tvHeaderAppVersion.text = "v$currentVer • تحديث 🔄"
        binding.tvUpdateSubtitle.text = "الإصدار الحالي: v$currentVer • فحص وتحميل آخر إصدار تلقائياً"
        binding.tvMainAppVersionBadge.text = "v$currentVer"
        binding.btnHeaderCheckUpdate.setOnClickListener {
            performCyberpunkHaptic()
            UpdateManager.showUpdateDialog(this)
        }

        binding.btnMainCheckUpdate.setOnClickListener {
            performCyberpunkHaptic()
            UpdateManager.showUpdateDialog(this)
        }
    }

    private fun showCustomDnsDialog(onSaved: () -> Unit) {
        val dialogBinding = DialogCustomDnsBinding.inflate(LayoutInflater.from(this))
        dialogBinding.etCustomIpv4.setText(prefs.customIpv4)
        dialogBinding.etCustomIpv6.setText(prefs.customIpv6)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCancelCustom.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveCustom.setOnClickListener {
            val ipv4 = dialogBinding.etCustomIpv4.text.toString().trim()
            val ipv6 = dialogBinding.etCustomIpv6.text.toString().trim()

            if (ipv4.isNotBlank()) {
                prefs.customIpv4 = ipv4
                prefs.customIpv6 = ipv6
                onSaved()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "يرجى إدخال عنوان IPv4 صالح", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showAdBlockTesterDialog() {
        val dialogBinding = DialogTesterResultBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.ivTesterIcon.visibility = View.GONE
        dialogBinding.pbTesterLoading.visibility = View.VISIBLE
        dialogBinding.tvTesterTitle.text = "جاري الفحص المباشر..."
        dialogBinding.tvTesterMsg.setText(R.string.dialog_test_running)
        dialogBinding.tvTestedDomain.text = "فحص استعلامات AdGuard / Quad9 / Google Ad Services..."
        dialogBinding.btnTesterClose.visibility = View.GONE

        dialog.show()

        lifecycleScope.launch {
            val result = AdBlockTester.runTest()
            dialogBinding.pbTesterLoading.visibility = View.GONE
            dialogBinding.ivTesterIcon.visibility = View.VISIBLE
            dialogBinding.btnTesterClose.visibility = View.VISIBLE

            if (result.isBlocked) {
                dialogBinding.ivTesterIcon.setImageResource(R.drawable.ic_bug_check)
                dialogBinding.ivTesterIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.neon_green))
                dialogBinding.tvTesterTitle.setText(R.string.dialog_test_blocked_title)
                dialogBinding.tvTesterTitle.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neon_green))
                dialogBinding.tvTesterMsg.setText(R.string.dialog_test_blocked_msg)
                dialogBinding.tvTestedDomain.text = "${result.domain} -> ${result.resolvedIp}"
            } else {
                dialogBinding.ivTesterIcon.setImageResource(R.drawable.ic_bug_check)
                dialogBinding.ivTesterIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.neon_red))
                dialogBinding.tvTesterTitle.setText(R.string.dialog_test_unblocked_title)
                dialogBinding.tvTesterTitle.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neon_red))
                dialogBinding.tvTesterMsg.setText(R.string.dialog_test_unblocked_msg)
                dialogBinding.tvTestedDomain.text = "${result.domain} -> ${result.resolvedIp} (تم السماح)"
            }

            dialogBinding.btnTesterClose.setOnClickListener {
                dialog.dismiss()
            }
        }
    }

    private fun openSystemNetworkSettings() {
        val intents = listOf(
            Intent("android.settings.NETWORK_OPERATOR_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try fallback
            }
        }
    }

    private fun performCyberpunkHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (e: Exception) {
            binding.btnPower.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
