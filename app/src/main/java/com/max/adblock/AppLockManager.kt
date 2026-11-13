package com.max.adblock

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton

object AppLockManager {

    @Volatile
    var isSessionUnlocked = false

    private var activeDialog: AlertDialog? = null
    private var lastBackgroundTimeMs = 0L
    private const val LOCK_TIMEOUT_MS = 60_000L // 1 minute background timeout

    fun notifyAppBackgrounded() {
        lastBackgroundTimeMs = System.currentTimeMillis()
    }

    fun notifyAppForegrounded() {
        if (lastBackgroundTimeMs > 0 && System.currentTimeMillis() - lastBackgroundTimeMs > LOCK_TIMEOUT_MS) {
            isSessionUnlocked = false
        }
    }

    fun isLockRequired(activity: Activity): Boolean {
        val prefs = PreferencesManager(activity)
        return prefs.isAppLockEnabled && prefs.appLockPin.isNotBlank() && !isSessionUnlocked
    }

    fun promptAppLock(activity: Activity, onUnlocked: () -> Unit) {
        val prefs = PreferencesManager(activity)
        if (!prefs.isAppLockEnabled || prefs.appLockPin.isBlank()) {
            isSessionUnlocked = true
            onUnlocked()
            return
        }

        if (isSessionUnlocked) {
            onUnlocked()
            return
        }

        if (activeDialog?.isShowing == true) {
            return
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_lock, null)
        val etPin = dialogView.findViewById<EditText>(R.id.etPinInput)
        val btnUnlock = dialogView.findViewById<MaterialButton>(R.id.btnUnlock)
        val tvError = dialogView.findViewById<TextView>(R.id.tvLockError)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Intercept Back Key: Exit app if user refuses or cancels entering PIN
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                activeDialog = null
                activity.finishAffinity()
                true
            } else {
                false
            }
        }

        btnUnlock.setOnClickListener {
            val entered = etPin.text.toString().trim()
            if (entered == prefs.appLockPin) {
                isSessionUnlocked = true
                activeDialog = null
                dialog.dismiss()
                onUnlocked()
            } else {
                tvError.visibility = android.view.View.VISIBLE
                tvError.text = activity.getString(R.string.app_lock_wrong_pin)
                etPin.text.clear()
            }
        }

        activeDialog = dialog
        dialog.show()
    }
}
