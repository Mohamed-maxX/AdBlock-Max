package com.max.adblock

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton

object AppLockManager {

    var isSessionUnlocked = false

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

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_lock, null)
        val etPin = dialogView.findViewById<EditText>(R.id.etPinInput)
        val btnUnlock = dialogView.findViewById<MaterialButton>(R.id.btnUnlock)
        val tvError = dialogView.findViewById<TextView>(R.id.tvLockError)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnUnlock.setOnClickListener {
            val entered = etPin.text.toString().trim()
            if (entered == prefs.appLockPin) {
                isSessionUnlocked = true
                dialog.dismiss()
                onUnlocked()
            } else {
                tvError.visibility = android.view.View.VISIBLE
                tvError.text = activity.getString(R.string.app_lock_wrong_pin)
                etPin.text.clear()
            }
        }

        dialog.show()
    }
}
