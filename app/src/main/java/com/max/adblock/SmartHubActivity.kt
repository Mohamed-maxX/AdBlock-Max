package com.max.adblock

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.max.adblock.databinding.ActivitySmartHubBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartHubActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAB_INDEX = "extra_smart_tab_index"
    }

    private lateinit var binding: ActivitySmartHubBinding
    private lateinit var prefs: PreferencesManager
    private var topLeakerPkg = ""

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshWifiCard()
        if (granted) {
            Toast.makeText(this, "تم منح إذن الموقع الاختياري لقراءة اسم الشبكة ✅", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmartHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                initSmartHubUi()
            }
        } else {
            initSmartHubUi()
        }
    }

    private fun initSmartHubUi() {
        setupToolbar()
        setupTabs()
        setupTab1Interactive()
        setupTab2Network()
        setupTab3Security()
        observeScreenRecording()

        val initialTab = intent.getIntExtra(EXTRA_TAB_INDEX, 0).coerceIn(0, 2)
        selectTab(initialTab)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
        refreshWifiCard()
        refreshWhitelistSuggestionCard()
        refreshAlertCards()
    }

    private fun setupToolbar() {
        binding.btnBackSmartHub.setOnClickListener {
            finish()
        }
    }

    private fun selectTab(selectedIndex: Int) {
        val tabs = listOf(
            Triple(binding.tabBtnInteractive, binding.layoutTabInteractive, 0),
            Triple(binding.tabBtnNetwork, binding.layoutTabNetwork, 1),
            Triple(binding.tabBtnSecurity, binding.layoutTabSecurity, 2)
        )
        tabs.forEachIndexed { index, (button, view, _) ->
            if (index == selectedIndex) {
                view.visibility = View.VISIBLE
                button.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                button.strokeColor = ContextCompat.getColorStateList(this, R.color.neon_cyan)
            } else {
                view.visibility = View.GONE
                button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                button.strokeColor = ContextCompat.getColorStateList(this, R.color.card_stroke)
            }
        }
    }

    private fun setupTabs() {
        binding.tabBtnInteractive.setOnClickListener { selectTab(0) }
        binding.tabBtnNetwork.setOnClickListener { selectTab(1) }
        binding.tabBtnSecurity.setOnClickListener { selectTab(2) }
    }

    private fun refreshPermissionStatuses() {
        val accEnabled = AdSkipAccessibilityService.isServiceEnabled(this)
        if (accEnabled) {
            binding.tvAccessibilityStatus.text = "حالة خدمة الأتمتة الاختيارية: نشطة ومفعلة ✅"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else {
            binding.tvAccessibilityStatus.text = "حالة خدمة الأتمتة الاختيارية: غير مفعلة (اختياري)"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
        }

        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val notifEnabled = enabledListeners?.contains(packageName) == true
        if (notifEnabled) {
            binding.tvNotifListenerStatus.text = "حالة مراقب الإشعارات الاختياري: نشط ومفعل ✅"
            binding.tvNotifListenerStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else {
            binding.tvNotifListenerStatus.text = "حالة مراقب الإشعارات الاختياري: غير مفعل (اختياري)"
            binding.tvNotifListenerStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
        }
    }

    // --- TAB 1: Interactive Automation ---
    private fun setupTab1Interactive() {
        binding.btnEnableAccessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "يرجى تفعيل الخدمة من إعدادات إمكانية الوصول", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchAutoSkipAds.isChecked = prefs.smartAutoSkipAds
        binding.switchAutoSkipAds.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartAutoSkipAds = isChecked
            if (isChecked && !AdSkipAccessibilityService.isServiceEnabled(this)) {
                Toast.makeText(this, "لتشغيل تخطي الإعلانات تلقائياً، فعّل خدمة إمكانية الوصول بالأعلى", Toast.LENGTH_LONG).show()
            }
        }

        binding.switchAntiRedirect.isChecked = prefs.smartAntiRedirect
        binding.switchAntiRedirect.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartAntiRedirect = isChecked
        }

        binding.switchAutoRejectCookies.isChecked = prefs.smartAutoRejectCookies
        binding.switchAutoRejectCookies.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartAutoRejectCookies = isChecked
        }

        binding.switchAppAwareDns.isChecked = prefs.smartAppAwareDns
        binding.switchAppAwareDns.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartAppAwareDns = isChecked
        }

        binding.btnSelectGamingApps.setOnClickListener {
            showGamingAppsSelectorDialog()
        }

        binding.switchAutoLearnWhitelist.isChecked = prefs.smartAutoLearnWhitelist
        binding.switchAutoLearnWhitelist.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartAutoLearnWhitelist = isChecked
        }

        refreshWhitelistSuggestionCard()

        binding.btnHubOneTapClean.setOnClickListener {
            binding.btnHubOneTapClean.isEnabled = false
            lifecycleScope.launch {
                val report = SmartSecurityGuards.performOneTapClean(this@SmartHubActivity)
                val summary = buildString {
                    append("✅ اكتمل التنظيف الشامل بنجاح:\n")
                    append("• كاش الـ DNS: ${if (report.dnsFlushed) "تم التصفية ⚡" else "درع الحماية متوقف"}\n")
                    append("• الحافظة: ${if (report.clipboardCleaned) "تمت إزالة ${report.removedTrackerParams} متتبعات 🧼" else "نظيفة بالفعل"}\n")
                    append("• فحص التنزيلات: تم رصد ${report.suspiciousApksFound} ملف APK")
                }
                binding.tvOneTapCleanResult.visibility = View.VISIBLE
                binding.tvOneTapCleanResult.text = summary
                binding.btnHubOneTapClean.isEnabled = true
                refreshAlertCards()
            }
        }
    }

    private fun refreshWhitelistSuggestionCard() {
        val pendingPkg = prefs.pendingWhitelistSuggestionPkg
        if (pendingPkg.isBlank() || prefs.isAppBypassed(pendingPkg)) {
            binding.layoutWhitelistSuggestion.visibility = View.GONE
            return
        }
        val appName = try {
            val ai = packageManager.getApplicationInfo(pendingPkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pendingPkg
        }
        binding.layoutWhitelistSuggestion.visibility = View.VISIBLE
        binding.tvWhitelistSuggestionText.text =
            "🧠 لاحظنا إيقاف الحماية 3 مرات أثناء استخدام ($appName). هل تريد إضافته للاستثناء الدائم؟"

        binding.btnAcceptWhitelist.setOnClickListener {
            prefs.setAppBypassed(pendingPkg, true)
            prefs.resetAppPauseCount(pendingPkg)
            prefs.pendingWhitelistSuggestionPkg = ""
            binding.layoutWhitelistSuggestion.visibility = View.GONE
            Toast.makeText(this, "تمت إضافة $appName للاستثناء الدائم ✅", Toast.LENGTH_SHORT).show()
        }

        binding.btnDismissWhitelist.setOnClickListener {
            prefs.resetAppPauseCount(pendingPkg)
            prefs.pendingWhitelistSuggestionPkg = ""
            binding.layoutWhitelistSuggestion.visibility = View.GONE
        }
    }

    private fun showGamingAppsSelectorDialog() {
        lifecycleScope.launch {
            val userApps = withContext(Dispatchers.IO) {
                AppTrackerScanner.scanAllInstalledApps(this@SmartHubActivity).filter { !it.isSystemApp }
            }
            if (userApps.isEmpty()) return@launch

            val currentGaming = prefs.getSmartGamingApps()
            val names = userApps.map { it.appName }.toTypedArray()
            val checked = userApps.map { currentGaming.contains(it.packageName) }.toBooleanArray()

            AlertDialog.Builder(this@SmartHubActivity)
                .setTitle("تطبيقات الألعاب (التبديل التلقائي لـ Low-Ping)")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("حفظ القائمة 🎮") { _, _ ->
                    val newSet = mutableSetOf<String>()
                    userApps.forEachIndexed { idx, app ->
                        if (checked[idx]) newSet.add(app.packageName)
                    }
                    prefs.setSmartGamingApps(newSet)
                    Toast.makeText(this@SmartHubActivity, "تم حفظ ${newSet.size} لعبة للتبديل التلقائي", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    // --- TAB 2: Network, Battery & Quota ---
    private fun setupTab2Network() {
        refreshWifiCard()

        binding.btnGrantLocationPerm.setOnClickListener {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        binding.btnSaveHomeWifi.setOnClickListener {
            val info = SmartNetworkGuard.readCurrentWifiInfo(this)
            if (info == null) {
                if (!SmartNetworkGuard.hasLocationPermission(this)) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    Toast.makeText(this, "تأكد من الاتصال بشبكة Wi-Fi أولاً", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            prefs.trustedHomeSsid = info.ssid
            prefs.trustedHomeBssid = info.bssid
            refreshWifiCard()
            Toast.makeText(this, "تم حفظ \"${info.ssid}\" كشبكة منزلية موثوقة ✅", Toast.LENGTH_SHORT).show()
        }

        binding.switchWifiSsidTriggers.isChecked = prefs.smartWifiSsidTriggers
        binding.switchWifiSsidTriggers.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartWifiSsidTriggers = isChecked
            if (isChecked) SmartNetworkGuard.evaluateWifiSecurity(this)
        }

        binding.switchEvilTwinDetector.isChecked = prefs.smartEvilTwinDetector
        binding.switchEvilTwinDetector.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartEvilTwinDetector = isChecked
            if (isChecked) SmartNetworkGuard.evaluateWifiSecurity(this)
        }

        binding.switchSelfHealing.isChecked = prefs.smartSelfHealingWatchdog
        binding.tvSelfHealingStats.text =
            "إعادة تنشيط نفق DNS بصمت فور توقف الاستجابة • مرات الإصلاح التلقائي: ${prefs.selfHealingCount}"
        binding.switchSelfHealing.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartSelfHealingWatchdog = isChecked
        }

        binding.switchWeeklySchedule.isChecked = prefs.smartScheduleEnabled
        updateScheduleLabel()
        binding.switchWeeklySchedule.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartScheduleEnabled = isChecked
            if (isChecked) SmartSecurityGuards.evaluateWeeklySchedule(this)
        }

        binding.btnScheduleStartTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    prefs.smartScheduleStartHour = hour
                    prefs.smartScheduleStartMinute = minute
                    updateScheduleLabel()
                    SmartSecurityGuards.evaluateWeeklySchedule(this)
                },
                prefs.smartScheduleStartHour,
                prefs.smartScheduleStartMinute,
                true
            ).show()
        }

        binding.btnScheduleEndTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    prefs.smartScheduleEndHour = hour
                    prefs.smartScheduleEndMinute = minute
                    updateScheduleLabel()
                    SmartSecurityGuards.evaluateWeeklySchedule(this)
                },
                prefs.smartScheduleEndHour,
                prefs.smartScheduleEndMinute,
                true
            ).show()
        }

        binding.switchBatterySurvival.isChecked = prefs.smartBatterySurvivalEnabled
        if (prefs.isBatterySurvivalActive) {
            binding.tvBatterySurvivalStatus.text = "وضع الإنقاذ نشط حالياً بسبب انخفاض البطارية ⚡"
        }
        binding.switchBatterySurvival.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartBatterySurvivalEnabled = isChecked
        }

        // Quota Predictor
        binding.etMonthlyPackageGb.setText(prefs.monthlyPackageGb.toString())
        binding.btnPredictQuota.setOnClickListener {
            val gb = binding.etMonthlyPackageGb.text.toString().toIntOrNull()?.coerceIn(1, 5000) ?: 50
            prefs.monthlyPackageGb = gb
            runQuotaPrediction()
        }
        binding.btnBlockTopLeaker.setOnClickListener {
            if (topLeakerPkg.isNotBlank()) {
                prefs.setAppBlocked(topLeakerPkg, true)
                Toast.makeText(this, "تم قطع الإنترنت عن التطبيق المستنزف في الجدار الناري 🛡️", Toast.LENGTH_SHORT).show()
                runQuotaPrediction()
            }
        }
    }

    private fun refreshWifiCard() {
        val hasPerm = SmartNetworkGuard.hasLocationPermission(this)
        binding.btnGrantLocationPerm.visibility = if (hasPerm) View.GONE else View.VISIBLE

        val wifi = SmartNetworkGuard.readCurrentWifiInfo(this)
        binding.tvCurrentWifiInfo.text = if (wifi != null) {
            "الشبكة الحالية: ${wifi.ssid} (${wifi.bssid.ifBlank { "BSSID محمي" }})"
        } else if (!hasPerm) {
            "الشبكة الحالية: يلزم إذن الموقع (اختياري) لقراءة اسم الشبكة"
        } else {
            "الشبكة الحالية: غير متصل بـ Wi-Fi"
        }

        val savedSsid = prefs.trustedHomeSsid
        binding.tvHomeWifiSaved.text = if (savedSsid.isNotBlank()) {
            "شبكة المنزل الموثوقة: $savedSsid (${prefs.trustedHomeBssid})"
        } else {
            "شبكة المنزل الموثوقة: غير محددة بعد"
        }
    }

    private fun updateScheduleLabel() {
        val start = String.format("%02d:%02d", prefs.smartScheduleStartHour, prefs.smartScheduleStartMinute)
        val end = String.format("%02d:%02d", prefs.smartScheduleEndHour, prefs.smartScheduleEndMinute)
        binding.tvScheduleWindow.text = "من $start إلى $end • تقييد السوشيال ميديا وتشديد الحماية"
    }

    private fun runQuotaPrediction() {
        binding.btnPredictQuota.isEnabled = false
        lifecycleScope.launch {
            val pred = SmartNetworkGuard.predictQuotaAndTopLeaker(this@SmartHubActivity)
            topLeakerPkg = pred.topLeakerPkg
            binding.tvQuotaPredictionSummary.text =
                "• الاستهلاك المرصود: ${pred.estimatedUsedTodayMb} MB من ${pred.monthlyLimitGb} GB\n" +
                "• الأيام المتبقية المتوقعة للباقة: ~${pred.estimatedDaysRemaining} يوم\n" +
                "• أكثر تطبيق يسرب البيانات: ${pred.topLeakerName} (${pred.topLeakerMb} MB)"

            binding.btnBlockTopLeaker.visibility = if (pred.topLeakerPkg.isNotBlank()) View.VISIBLE else View.GONE
            binding.btnPredictQuota.isEnabled = true
        }
    }

    // --- TAB 3: Security & Privacy Guard ---
    private fun setupTab3Security() {
        binding.btnGrantNotifListener.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
            }
        }

        binding.switchPhishingNotifShield.isChecked = prefs.smartPhishingNotifShield
        binding.switchPhishingNotifShield.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartPhishingNotifShield = isChecked
        }

        binding.switchSilentSpyDetector.isChecked = prefs.smartSilentSpyDetector
        binding.switchSilentSpyDetector.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartSilentSpyDetector = isChecked
        }

        binding.switchPostUpdateWatcher.isChecked = prefs.smartPostUpdateWatcher
        binding.switchPostUpdateWatcher.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartPostUpdateWatcher = isChecked
        }

        binding.switchNewAppPrivacyScore.isChecked = prefs.smartNewAppPrivacyScore
        binding.switchNewAppPrivacyScore.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartNewAppPrivacyScore = isChecked
        }

        binding.switchFakeApkGuard.isChecked = prefs.smartFakeApkGuard
        binding.switchFakeApkGuard.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartFakeApkGuard = isChecked
            if (isChecked) {
                SmartSecurityGuards.startDownloadApkGuard(this)
            } else {
                SmartSecurityGuards.stopDownloadApkGuard()
            }
        }

        binding.btnScanDownloadsApks.setOnClickListener {
            lifecycleScope.launch {
                val apks = withContext(Dispatchers.IO) {
                    SmartSecurityGuards.scanDownloadsForSuspiciousApks()
                }
                if (apks.isEmpty()) {
                    prefs.lastSuspiciousApkName = ""
                    prefs.lastSuspiciousApkPath = ""
                    Toast.makeText(this@SmartHubActivity, "مجلد التنزيلات نظيف من ملفات APK المشبوهة ✅", Toast.LENGTH_SHORT).show()
                } else {
                    val latest = apks.first()
                    prefs.lastSuspiciousApkName = latest.name
                    prefs.lastSuspiciousApkPath = latest.absolutePath
                    Toast.makeText(this@SmartHubActivity, "تم العثور على ${apks.size} ملف APK في التنزيلات ⚠️", Toast.LENGTH_SHORT).show()
                }
                refreshAlertCards()
            }
        }

        binding.switchScreenRecordPrivacy.isChecked = prefs.smartScreenRecordPrivacy
        binding.switchScreenRecordPrivacy.setOnCheckedChangeListener { _, isChecked ->
            prefs.smartScreenRecordPrivacy = isChecked
            if (isChecked) {
                SmartSecurityGuards.startScreenRecordMonitor(this)
            } else {
                SmartSecurityGuards.stopScreenRecordMonitor(this)
            }
        }

        refreshAlertCards()
    }

    private fun refreshAlertCards() {
        val spySummary = prefs.lastSpyAlertSummary
        val spyPkg = prefs.lastSpyAlertPkg
        if (spySummary.isNotBlank() && spyPkg.isNotBlank()) {
            binding.layoutSpyAlert.visibility = View.VISIBLE
            binding.tvSpyAlertText.text = "⚠️ $spySummary"
            binding.btnBlockSpyApp.setOnClickListener {
                prefs.setAppBlocked(spyPkg, true)
                prefs.lastSpyAlertSummary = ""
                prefs.lastSpyAlertPkg = ""
                binding.layoutSpyAlert.visibility = View.GONE
                Toast.makeText(this, "تم حظر التطبيق في الجدار الناري 🚫", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.layoutSpyAlert.visibility = View.GONE
        }

        val apkName = prefs.lastSuspiciousApkName
        val apkPath = prefs.lastSuspiciousApkPath
        if (apkName.isNotBlank() && apkPath.isNotBlank()) {
            binding.layoutFakeApkAlert.visibility = View.VISIBLE
            binding.tvFakeApkText.text = "🚨 ملف تثبيت APK مشبوه مرصود: $apkName"
            binding.btnDeleteFakeApk.setOnClickListener {
                val deleted = SmartSecurityGuards.deleteSuspiciousApk(apkPath)
                prefs.lastSuspiciousApkName = ""
                prefs.lastSuspiciousApkPath = ""
                binding.layoutFakeApkAlert.visibility = View.GONE
                Toast.makeText(
                    this,
                    if (deleted) "تم حذف الملف المشبوه بنجاح 🗑️" else "تعذر الحذف المباشر بسبب قيود التخزين، احذفه من مدير الملفات",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            binding.layoutFakeApkAlert.visibility = View.GONE
        }
    }

    private fun observeScreenRecording() {
        lifecycleScope.launch {
            SmartSecurityGuards.isScreenRecordingActive.collectLatest { recording ->
                if (recording) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    binding.tvScreenRecordStatus.text = "🎥 تم رصد بث أو تسجيل شاشة: الحماية والتمويه مفعلة الآن!"
                    binding.tvScreenRecordStatus.setTextColor(ContextCompat.getColor(this@SmartHubActivity, R.color.neon_amber))
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    binding.tvScreenRecordStatus.text = "إخفاء الـ IP وسجل الرادار وحماية الشاشة تلقائياً أثناء التسجيل أو البث"
                    binding.tvScreenRecordStatus.setTextColor(ContextCompat.getColor(this@SmartHubActivity, R.color.text_secondary))
                }
            }
        }
    }
}
