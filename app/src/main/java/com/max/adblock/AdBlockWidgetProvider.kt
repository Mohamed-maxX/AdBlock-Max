package com.max.adblock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class AdBlockWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_WIDGET = "com.max.adblock.ACTION_TOGGLE_WIDGET"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AdBlockWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, AdBlockWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = PreferencesManager(context)
        val isActive = prefs.isVpnActive && !prefs.isVpnPaused
        val modeTitle = context.getString(prefs.selectedMode.titleResId)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_adblock)

            // Update UI
            if (isActive) {
                views.setInt(R.id.widgetContainer, "setBackgroundResource", R.drawable.bg_widget)
                views.setInt(R.id.ivWidgetToggle, "setBackgroundResource", R.drawable.bg_power_button_on)
                views.setTextViewText(R.id.tvWidgetStatus, context.getString(R.string.status_protected))
                views.setTextColor(R.id.tvWidgetStatus, ContextCompat.getColor(context, R.color.neon_green))
            } else {
                views.setInt(R.id.widgetContainer, "setBackgroundResource", R.drawable.bg_widget_off)
                views.setInt(R.id.ivWidgetToggle, "setBackgroundResource", R.drawable.bg_power_button_off)
                views.setTextViewText(R.id.tvWidgetStatus, context.getString(R.string.status_unprotected))
                views.setTextColor(R.id.tvWidgetStatus, ContextCompat.getColor(context, R.color.neon_red))
            }

            views.setTextViewText(R.id.tvWidgetMode, modeTitle)

            // Toggle Click Intent
            val toggleIntent = Intent(context, AdBlockWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_WIDGET
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.ivWidgetToggle, togglePendingIntent)
            views.setOnClickPendingIntent(R.id.widgetContainer, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        if (action == ACTION_TOGGLE_WIDGET) {
            val prefs = PreferencesManager(context)
            val isActive = prefs.isVpnActive && !prefs.isVpnPaused

            if (isActive) {
                val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
                    this.action = AdBlockVpnService.ACTION_STOP
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
                        this.action = AdBlockVpnService.ACTION_START
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(appIntent)
                }
            }
            updateAllWidgets(context)
        } else if (action == AdBlockVpnService.ACTION_VPN_STATE_CHANGED) {
            updateAllWidgets(context)
        }
    }
}
