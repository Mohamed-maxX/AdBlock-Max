package com.max.adblock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.max.adblock.databinding.ActivityNetworkToolsBinding
import kotlinx.coroutines.launch

class NetworkToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNetworkToolsBinding
    private val wifiAdapter = WifiDeviceAdapter()
    private val portAdapter = PortScanAdapter()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            startFloatingMonitor()
            binding.switchFloatingMonitor.isChecked = true
            Toast.makeText(this, "تم تفعيل إذن النافذة العائمة بنجاح 🎮", Toast.LENGTH_SHORT).show()
        } else {
            binding.switchFloatingMonitor.isChecked = false
            Toast.makeText(this, "يتطلب المراقب العائم منح إذن الظهور فوق التطبيقات", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                initNetworkToolsUi()
            }
        } else {
            initNetworkToolsUi()
        }
    }

    private fun initNetworkToolsUi() {
        setupToolbar()
        setupTabs()
        setupWifiScanner()
        setupPortScanner()
        setupSpeedTestAndGamingPing()
        setupFloatingMonitor()
        observeScreenRecordingPrivacy()
    }

    private fun observeScreenRecordingPrivacy() {
        lifecycleScope.launch {
            SmartSecurityGuards.isScreenRecordingActive.collect { recording ->
                if (recording) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                    binding.rvWifiDevices.visibility = View.INVISIBLE
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    binding.rvWifiDevices.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkCaptivePortal()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        val tabs = listOf(
            Triple(binding.tabBtnWifi, binding.layoutWifiTab, 0),
            Triple(binding.tabBtnPorts, binding.layoutPortsTab, 1),
            Triple(binding.tabBtnSpeed, binding.layoutSpeedTab, 2),
            Triple(binding.tabBtnFloating, binding.layoutFloatingTab, 3)
        )

        fun selectTab(selectedIndex: Int) {
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

        binding.tabBtnWifi.setOnClickListener { selectTab(0) }
        binding.tabBtnPorts.setOnClickListener { selectTab(1) }
        binding.tabBtnSpeed.setOnClickListener { selectTab(2) }
        binding.tabBtnFloating.setOnClickListener { selectTab(3) }

        // Default to Wi-Fi tab
        selectTab(0)
    }

    // --- TAB 1: Wi-Fi Scanner ---
    private fun setupWifiScanner() {
        binding.rvWifiDevices.layoutManager = LinearLayoutManager(this)
        binding.rvWifiDevices.adapter = wifiAdapter

        binding.btnStartWifiScan.setOnClickListener {
            runWifiScan()
        }

        // Automatic initial scan
        runWifiScan()
    }

    private fun runWifiScan() {
        binding.btnStartWifiScan.isEnabled = false
        binding.pbWifiScanning.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val devices = NetworkScanner.scanSubnet(this@NetworkToolsActivity)
                wifiAdapter.updateData(devices)
                binding.tvWifiDevicesCount.text = "الأجهزة المتصلة حالياً بالشبكة: ${devices.size} جهاز"
            } catch (e: Exception) {
                Toast.makeText(this@NetworkToolsActivity, "خطأ أثناء فحص الشبكة", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnStartWifiScan.isEnabled = true
                binding.pbWifiScanning.visibility = View.GONE
            }
        }
    }

    // --- TAB 2: Port & Vulnerability Scanner ---
    private fun setupPortScanner() {
        binding.rvPorts.layoutManager = LinearLayoutManager(this)
        binding.rvPorts.adapter = portAdapter

        binding.btnStartPortScan.setOnClickListener {
            runPortScan()
        }

        // Initial default closed ports list
        lifecycleScope.launch {
            val initial = NetworkScanner.scanPorts("127.0.0.1")
            portAdapter.updateData(initial)
        }
    }

    private fun checkCaptivePortal() {
        val isCaptive = NetworkScanner.isCaptivePortal(this)
        if (isCaptive) {
            binding.tvCaptivePortalStatus.text = getString(R.string.captive_portal_detected)
            binding.tvCaptivePortalStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_amber))
        } else {
            binding.tvCaptivePortalStatus.text = getString(R.string.captive_portal_secure)
            binding.tvCaptivePortalStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        }
    }

    private fun runPortScan() {
        binding.btnStartPortScan.isEnabled = false
        binding.pbPortScanning.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val ip = NetworkScanner.getLocalIpAddress()
                val results = NetworkScanner.scanPorts(ip)
                portAdapter.updateData(results)
                checkCaptivePortal()
                Toast.makeText(this@NetworkToolsActivity, "تم فحص المنافذ المحلية بنجاح", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@NetworkToolsActivity, "تعذر إكمال فحص المنافذ", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnStartPortScan.isEnabled = true
                binding.pbPortScanning.visibility = View.GONE
            }
        }
    }

    // --- TAB 3: Speed Test & Gaming Ping ---
    private fun setupSpeedTestAndGamingPing() {
        binding.btnRunSpeedTest.setOnClickListener {
            runSpeedTest()
        }

        binding.btnRefreshGamingPings.setOnClickListener {
            runGamingPingTest()
        }

        // Initial ping test
        runGamingPingTest()
    }

    private fun runSpeedTest() {
        binding.btnRunSpeedTest.isEnabled = false
        binding.pbSpeedTesting.progress = 0

        lifecycleScope.launch {
            try {
                val (speed, jitter) = NetworkScanner.runSpeedTest { currentSpeed, currentJitter ->
                    runOnUiThread {
                        binding.tvSpeedValue.text = "%.1f".format(currentSpeed)
                        binding.tvJitterValue.text = "استقرار الخط (Jitter): %.1f ms".format(currentJitter)
                        binding.pbSpeedTesting.progress = (currentSpeed * 1.5f).toInt().coerceIn(10, 100)
                    }
                }
                binding.tvSpeedValue.text = "%.1f".format(speed)
                binding.tvJitterValue.text = "استقرار الخط (Jitter): %.1f ms".format(jitter)
                binding.pbSpeedTesting.progress = 100
                Toast.makeText(this@NetworkToolsActivity, "اكتمل قياس سرعة الاتصال بنجاح!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@NetworkToolsActivity, "حدث خطأ أثناء اختبار السرعة", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnRunSpeedTest.isEnabled = true
            }
        }
    }

    private fun runGamingPingTest() {
        binding.btnRefreshGamingPings.isEnabled = false
        binding.btnRefreshGamingPings.text = "جارٍ قياس البنج العالمي... ⏳"

        lifecycleScope.launch {
            try {
                val pings = NetworkScanner.testGlobalGamingPings()
                pings.forEach { result ->
                    val pingText = "${result.pingMs} ms • ${result.status}"
                    when {
                        result.regionName.contains("الشرق الأوسط") -> {
                            binding.tvPingMe.text = pingText
                            binding.tvPingMe.setTextColor(getPingColor(result.pingMs))
                        }
                        result.regionName.contains("أوروبا") -> {
                            binding.tvPingEu.text = pingText
                            binding.tvPingEu.setTextColor(getPingColor(result.pingMs))
                        }
                        result.regionName.contains("آسيا") -> {
                            binding.tvPingAsia.text = pingText
                            binding.tvPingAsia.setTextColor(getPingColor(result.pingMs))
                        }
                        result.regionName.contains("أمريكا") -> {
                            binding.tvPingUs.text = pingText
                            binding.tvPingUs.setTextColor(getPingColor(result.pingMs))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                binding.btnRefreshGamingPings.isEnabled = true
                binding.btnRefreshGamingPings.text = "تحديث بنج الألعاب الآن 🎮"
            }
        }
    }

    private fun getPingColor(pingMs: Long): Int {
        return when {
            pingMs < 60 -> ContextCompat.getColor(this, R.color.neon_green)
            pingMs in 60..110 -> ContextCompat.getColor(this, R.color.neon_amber)
            else -> ContextCompat.getColor(this, R.color.neon_red)
        }
    }

    // --- TAB 4: Floating Mini-Monitor ---
    private fun setupFloatingMonitor() {
        binding.switchFloatingMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    binding.switchFloatingMonitor.isChecked = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                } else {
                    startFloatingMonitor()
                }
            } else {
                stopFloatingMonitor()
            }
        }
    }

    private fun startFloatingMonitor() {
        val intent = Intent(this, FloatingMonitorService::class.java)
        startService(intent)
        Toast.makeText(this, "تم تفعيل مراقب الألعاب العائم 🎮", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingMonitor() {
        val intent = Intent(this, FloatingMonitorService::class.java)
        stopService(intent)
        Toast.makeText(this, "تم إيقاف المراقب العائم", Toast.LENGTH_SHORT).show()
    }
}
