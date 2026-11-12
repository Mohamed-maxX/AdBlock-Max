package com.max.adblock

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class AdBlockTileService : TileService() {

    private lateinit var prefs: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isActive = prefs.isVpnActive && !prefs.isVpnPaused

        if (isActive) {
            // Stop protection
            val intent = Intent(this, AdBlockVpnService::class.java).apply {
                action = AdBlockVpnService.ACTION_STOP
            }
            ContextCompat.startForegroundService(this, intent)
            updateTileState(active = false)
        } else {
            // Start protection
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent == null) {
                // Permission already granted, start immediately
                val intent = Intent(this, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(this, intent)
                updateTileState(active = true)
            } else {
                // Must open app to request VPN permission
                val appIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(
                        android.app.PendingIntent.getActivity(
                            this,
                            0,
                            appIntent,
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(appIntent)
                }
            }
        }
    }

    private fun updateTileState(active: Boolean? = null) {
        val tile = qsTile ?: return
        val isShieldActive = active ?: (prefs.isVpnActive && !prefs.isVpnPaused)

        tile.state = if (isShieldActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isShieldActive) {
                getString(R.string.tile_active)
            } else if (prefs.isVpnPaused) {
                "موقوف مؤقتاً"
            } else {
                getString(R.string.tile_inactive)
            }
        }

        tile.updateTile()
    }
}
