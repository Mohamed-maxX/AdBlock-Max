package com.max.adblock

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object CamouflageManager {

    private const val ALIAS_STEALTH = "com.max.adblock.MainActivityStealth"

    fun setStealthMode(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val prefs = PreferencesManager(context)
        prefs.isStealthCamouflageEnabled = enabled

        val mainComponent = ComponentName(context, MainActivity::class.java)
        val stealthComponent = ComponentName(context, ALIAS_STEALTH)

        try {
            if (enabled) {
                pm.setComponentEnabledSetting(
                    stealthComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    stealthComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
