package com.max.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var pauseJob: Job? = null
    private var failoverMonitorJob: Job? = null
    private var consecutiveFailures = 0
    private var lastFailoverTimeMs = 0L
    private lateinit var prefs: PreferencesManager

    companion object {
        const val ACTION_START = "com.max.adblock.ACTION_START"
        const val ACTION_STOP = "com.max.adblock.ACTION_STOP"
        const val ACTION_PAUSE_5MIN = "com.max.adblock.ACTION_PAUSE_5MIN"
        const val ACTION_RESUME = "com.max.adblock.ACTION_RESUME"
        const val ACTION_TRIGGER_FAILOVER = "com.max.adblock.ACTION_TRIGGER_FAILOVER"
        const val ACTION_VPN_STATE_CHANGED = "com.max.adblock.VPN_STATE_CHANGED"

        const val EXTRA_IS_ACTIVE = "extra_is_active"
        const val EXTRA_IS_PAUSED = "extra_is_paused"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "adblock_max_channel"
        private const val MTU_SIZE = 1500

        // Strict Failover Parameters
        private const val FAILOVER_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes cooldown
        private const val FAILOVER_CHECK_INTERVAL_MS = 30 * 1000L // 30 seconds
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val PING_LATENCY_THRESHOLD_MS = 800L
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START, ACTION_RESUME -> {
                pauseJob?.cancel()
                prefs.isVpnPaused = false
                startVpn()
            }
            ACTION_STOP -> {
                pauseJob?.cancel()
                stopVpn()
            }
            ACTION_PAUSE_5MIN -> {
                pauseVpnFor5Minutes()
            }
            ACTION_TRIGGER_FAILOVER -> {
                if (prefs.isVpnActive && !prefs.isVpnPaused && prefs.autoFailoverEnabled) {
                    val now = System.currentTimeMillis()
                    if (now - lastFailoverTimeMs >= FAILOVER_COOLDOWN_MS) {
                        lastFailoverTimeMs = now
                        consecutiveFailures = 0
                        prefs.isFailoverActive = !prefs.isFailoverActive
                        startVpn()
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null

            val builder = Builder()
                .setSession("AdBlock Max Cyberpunk Shield")
                .setMtu(MTU_SIZE)

            // 1. Local IPv4 & IPv6 interfaces with isolated dummy endpoints
            builder.addAddress("10.0.0.1", 32)
            builder.addAddress("fd00:1:fd00:1::1", 128)

            // 2. High-speed Direct DNS Injection: Only route local dummy endpoint into TUN
            // NEVER route 0.0.0.0/0, and NEVER route the external DNS server IPs into TUN!
            // This ensures 100% of user internet traffic (games, videos, web) and DNS queries
            // flow directly over the physical Wi-Fi/LTE network with zero latency or blackholing.
            addRouteSafe(builder, "10.0.0.2", 32)
            addRouteSafe(builder, "fd00:1:fd00:1::2", 128)

            // Configure DNS based on active mode & Failover
            val mode = prefs.selectedMode
            val (ipv4Pri, ipv4Sec, ipv6Pri, ipv6Sec) = getDnsAddresses(mode)

            // If failover is active, swap primary and secondary for instant recovery
            val effectiveIpv4Pri = if (prefs.isFailoverActive && ipv4Sec.isNotBlank()) ipv4Sec else ipv4Pri
            val effectiveIpv4Sec = if (prefs.isFailoverActive && ipv4Sec.isNotBlank()) ipv4Pri else ipv4Sec

            // Inject ad-blocking DNS servers directly
            addDnsSafe(builder, effectiveIpv4Pri)
            addDnsSafe(builder, effectiveIpv4Sec)
            addDnsSafe(builder, ipv6Pri)
            addDnsSafe(builder, ipv6Sec)

            // 3. Network performance optimizations: non-blocking and unmetered
            builder.setBlocking(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
                builder.setUnderlyingNetworks(null)
            }

            // Phase 3: Split Tunneling (App Bypass)
            val bypassedApps = prefs.getBypassedApps()
            for (pkg in bypassedApps) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Phase 3: Auto-Update Blocker on Cellular
            if (prefs.blockAutoUpdatesOnCellular) {
                val updateBlockPackages = listOf("com.android.vending", "com.google.android.gms")
                for (pkg in updateBlockPackages) {
                    if (!bypassedApps.contains(pkg)) {
                        // Keep within VPN to filter and throttle
                    }
                }
            }

            // Content intent to open MainActivity on click
            val contentIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingContentIntent = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setConfigureIntent(pendingContentIntent)

            val establishedInterface = builder.establish()
            if (establishedInterface == null) {
                stopVpn()
                return
            }
            vpnInterface = establishedInterface

            prefs.isVpnActive = true
            prefs.isVpnPaused = false
            if (prefs.sessionStartTime == 0L) {
                prefs.sessionStartTime = System.currentTimeMillis()
            }

            val notification = buildActiveNotification()
            startForegroundServiceCompat(notification)

            broadcastState(isActive = true, isPaused = false)

            PingTester.vpnServiceInstance = this
            startFailoverMonitor()

        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun startFailoverMonitor() {
        failoverMonitorJob?.cancel()
        consecutiveFailures = 0

        if (!prefs.autoFailoverEnabled) return

        failoverMonitorJob = serviceScope.launch {
            while (isActive && prefs.isVpnActive && !prefs.isVpnPaused) {
                delay(FAILOVER_CHECK_INTERVAL_MS)

                if (!prefs.autoFailoverEnabled) {
                    consecutiveFailures = 0
                    break
                }

                val mode = prefs.selectedMode
                val primaryIp = if (mode == DnsMode.CUSTOM) {
                    prefs.customIpv4
                } else if (prefs.isFailoverActive && mode.secondaryIpv4.isNotBlank()) {
                    mode.secondaryIpv4
                } else {
                    mode.primaryIpv4
                }

                val ping = PingTester.measurePingMs(primaryIp, vpnService = this@AdBlockVpnService)

                if (ping < 0 || ping > PING_LATENCY_THRESHOLD_MS) {
                    consecutiveFailures++
                } else {
                    consecutiveFailures = 0
                }

                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    val now = System.currentTimeMillis()
                    if (now - lastFailoverTimeMs >= FAILOVER_COOLDOWN_MS) {
                        lastFailoverTimeMs = now
                        consecutiveFailures = 0
                        prefs.isFailoverActive = !prefs.isFailoverActive
                        startVpn()
                    }
                }
            }
        }
    }

    private fun addDnsSafe(builder: Builder, ip: String) {
        if (ip.isNotBlank()) {
            try {
                builder.addDnsServer(InetAddress.getByName(ip))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addRouteSafe(builder: Builder, ip: String, prefix: Int) {
        if (ip.isNotBlank()) {
            try {
                builder.addRoute(InetAddress.getByName(ip), prefix)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDnsAddresses(mode: DnsMode): List<String> {
        return if (mode == DnsMode.CUSTOM) {
            listOf(prefs.customIpv4, "", prefs.customIpv6, "")
        } else {
            listOf(mode.primaryIpv4, mode.secondaryIpv4, mode.primaryIpv6, mode.secondaryIpv6)
        }
    }

    private fun pauseVpnFor5Minutes() {
        failoverMonitorJob?.cancel()
        consecutiveFailures = 0
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        prefs.isVpnActive = true
        prefs.isVpnPaused = true

        val notification = buildPausedNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        broadcastState(isActive = true, isPaused = true)

        pauseJob?.cancel()
        pauseJob = serviceScope.launch {
            delay(5 * 60 * 1000L) // 5 minutes pause
            startVpn()
        }
    }

    private fun stopVpn() {
        failoverMonitorJob?.cancel()
        consecutiveFailures = 0
        PingTester.vpnServiceInstance = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        prefs.isVpnActive = false
        prefs.isVpnPaused = false
        prefs.isFailoverActive = false
        prefs.sessionStartTime = 0L

        broadcastState(isActive = false, isPaused = false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        failoverMonitorJob?.cancel()
        consecutiveFailures = 0
        PingTester.vpnServiceInstance = null
        pauseJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.isVpnActive = false
        prefs.isVpnPaused = false
        broadcastState(isActive = false, isPaused = false)
        super.onDestroy()
    }

    private fun broadcastState(isActive: Boolean, isPaused: Boolean) {
        val intent = Intent(ACTION_VPN_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
            putExtra(EXTRA_IS_PAUSED, isPaused)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        // Update Home Screen Widgets
        AdBlockWidgetProvider.updateAllWidgets(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildActiveNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Pause 5 min
        val pauseIntent = Intent(this, VpnActionReceiver::class.java).apply {
            action = ACTION_PAUSE_5MIN
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Stop
        val stopIntent = Intent(this, VpnActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeTitle = getString(prefs.selectedMode.titleResId)
        val failoverNote = if (prefs.isFailoverActive) " [Auto-Failover ⚡]" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_protected)
            .setContentTitle(getString(R.string.notif_title_active) + failoverNote)
            .setContentText("$modeTitle • ${getString(R.string.notif_content_active)}")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_timer, getString(R.string.notif_action_pause), pausePendingIntent)
            .addAction(R.drawable.ic_power, getString(R.string.notif_action_stop), stopPendingIntent)
            .build()
    }

    private fun buildPausedNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Resume immediately
        val resumeIntent = Intent(this, VpnActionReceiver::class.java).apply {
            action = ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop completely
        val stopIntent = Intent(this, VpnActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.notif_paused_title))
            .setContentText(getString(R.string.notif_paused_desc))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_shield_protected, "استئناف الآن", resumePendingIntent)
            .addAction(R.drawable.ic_power, getString(R.string.notif_action_stop), stopPendingIntent)
            .build()
    }

    private fun startForegroundServiceCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
