package com.max.adblock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.max.adblock.databinding.ActivityPrivacySuiteBinding
import kotlinx.coroutines.launch

class PrivacySuiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySuiteBinding
    private lateinit var prefs: PreferencesManager
    private val ghostAdapter = GhostAdAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySuiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setupToolbar()
        setupTabs()
        setupPrivacyGuardTab()
        setupGhostDetectorTab()
        setupAppLockTab()
        setupBackupTab()
    }

    private fun setupToolbar() {
        binding.btnBackPrivacy.setOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        val tabs = listOf(
            Triple(binding.tabBtnPrivacy, binding.layoutTabPrivacy, 0),
            Triple(binding.tabBtnGhost, binding.layoutTabGhost, 1),
            Triple(binding.tabBtnAppLock, binding.layoutTabAppLock, 2),
            Triple(binding.tabBtnBackup, binding.layoutTabBackup, 3)
        )

        fun selectTab(selectedIndex: Int) {
            tabs.forEachIndexed { index, (button, view, _) ->
                if (index == selectedIndex) {
                    view.visibility = View.VISIBLE
                    button.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
                    button.strokeColor = ContextCompat.getColorStateList(this, R.color.neon_green)
                } else {
                    view.visibility = View.GONE
                    button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    button.strokeColor = ContextCompat.getColorStateList(this, R.color.card_stroke)
                }
            }
        }

        binding.tabBtnPrivacy.setOnClickListener { selectTab(0) }
        binding.tabBtnGhost.setOnClickListener { selectTab(1) }
        binding.tabBtnAppLock.setOnClickListener { selectTab(2) }
        binding.tabBtnBackup.setOnClickListener { selectTab(3) }

        selectTab(0)
    }

    // --- TAB 1: Privacy Guard & Link Tools ---
    private fun setupPrivacyGuardTab() {
        // Safe Link Preview
        binding.btnResolveShortUrl.setOnClickListener {
            val url = binding.etShortUrl.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "يرجى إدخال الرابط أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnResolveShortUrl.isEnabled = false
            lifecycleScope.launch {
                try {
                    val (finalUrl, hops) = LinkCleaner.resolveRedirects(url)
                    binding.layoutResolvedResult.visibility = View.VISIBLE
                    binding.tvResolvedFinalUrl.text = finalUrl
                    binding.tvResolvedHops.text = getString(R.string.safe_preview_redirect_hops, hops)
                } catch (e: Exception) {
                    Toast.makeText(this@PrivacySuiteActivity, "تعذر فك الرابط", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.btnResolveShortUrl.isEnabled = true
                }
            }
        }

        // Clipboard Clean
        binding.btnCleanClipboard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (item.isNullOrBlank()) {
                Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val clean = LinkCleaner.cleanUrl(item).cleanUrl
            val clip = ClipData.newPlainText("Clean URL", clean)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.clipboard_cleaned_toast, Toast.LENGTH_SHORT).show()
        }

        // Clipboard Clear
        binding.btnClearClipboard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            Toast.makeText(this, R.string.clipboard_cleared_toast, Toast.LENGTH_SHORT).show()
        }

        // Audio Muter
        binding.switchAudioMuter.isChecked = prefs.isAudioMuterEnabled
        binding.switchAudioMuter.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAudioMuterEnabled = isChecked
            if (isChecked) {
                val enabledListeners = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                val isGranted = enabledListeners?.contains(packageName) == true
                if (!isGranted) {
                    Toast.makeText(this, "يرجى منح إذن قراءة الإشعارات لكتم الإعلانات الصوتية", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    Toast.makeText(this, "تم تفعيل كاتم الإعلانات الصوتية 🔇", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "تم إيقاف كاتم الإعلانات الصوتية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- TAB 2: Ghost Ad Detector ---
    private fun setupGhostDetectorTab() {
        binding.rvGhostApps.layoutManager = LinearLayoutManager(this)
        binding.rvGhostApps.adapter = ghostAdapter

        binding.btnScanGhostApps.setOnClickListener {
            runGhostAdScan()
        }

        runGhostAdScan()
    }

    private fun runGhostAdScan() {
        binding.btnScanGhostApps.isEnabled = false
        binding.pbGhostScanning.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val apps = GhostAdDetector.scanForGhostAdApps(this@PrivacySuiteActivity)
                ghostAdapter.updateData(apps)
                if (apps.isEmpty()) {
                    binding.tvGhostStatus.text = getString(R.string.ghost_no_adware)
                    binding.tvGhostStatus.setTextColor(ContextCompat.getColor(this@PrivacySuiteActivity, R.color.neon_green))
                } else {
                    binding.tvGhostStatus.text = getString(R.string.ghost_found_count, apps.size)
                    binding.tvGhostStatus.setTextColor(ContextCompat.getColor(this@PrivacySuiteActivity, R.color.neon_amber))
                }
            } catch (e: Exception) {
                Toast.makeText(this@PrivacySuiteActivity, "خطأ أثناء فحص التطبيقات الشبحية", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnScanGhostApps.isEnabled = true
                binding.pbGhostScanning.visibility = View.GONE
            }
        }
    }

    // --- TAB 3: App Lock & Stealth Camouflage ---
    private fun setupAppLockTab() {
        binding.switchAppLock.isChecked = prefs.isAppLockEnabled

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (prefs.appLockPin.isBlank()) {
                    showSetPinDialog {
                        binding.switchAppLock.isChecked = prefs.isAppLockEnabled
                    }
                } else {
                    prefs.isAppLockEnabled = true
                }
            } else {
                prefs.isAppLockEnabled = false
            }
        }

        binding.btnSetNewPin.setOnClickListener {
            showSetPinDialog()
        }

        // Camouflage
        if (prefs.isStealthCamouflageEnabled) {
            binding.rbCamouflageStealth.isChecked = true
        } else {
            binding.rbCamouflageNormal.isChecked = true
        }

        binding.rgCamouflage.setOnCheckedChangeListener { _, checkedId ->
            val isStealth = (checkedId == R.id.rbCamouflageStealth)
            CamouflageManager.setStealthMode(this, isStealth)
            Toast.makeText(this, R.string.camouflage_changed_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSetPinDialog(onSaved: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "••••"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = android.view.Gravity.CENTER
            textSize = 22f
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            setTextColor(ContextCompat.getColor(this@PrivacySuiteActivity, R.color.neon_cyan))
            setBackgroundResource(R.drawable.bg_stat_chip)
            setPadding(20, 20, 20, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.app_lock_set_pin)
            .setMessage("أدخل 4 أرقام لتعيين رمز القفل الجديد:")
            .setView(input)
            .setPositiveButton("حفظ القفل 🔒") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length == 4) {
                    prefs.appLockPin = pin
                    prefs.isAppLockEnabled = true
                    Toast.makeText(this, R.string.app_lock_pin_saved, Toast.LENGTH_SHORT).show()
                    onSaved?.invoke()
                } else {
                    Toast.makeText(this, "يجب أن يتكون رمز PIN من 4 أرقام", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء") { _, _ ->
                onSaved?.invoke()
            }
            .show()
    }

    // --- TAB 4: Backup, QR & Updates ---
    private fun setupBackupTab() {
        binding.btnExportJson.setOnClickListener {
            val json = prefs.exportSettingsToJson()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AdBlock Max Backup", json))

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AdBlock Max Backup")
                putExtra(Intent.EXTRA_TEXT, json)
            }
            startActivity(Intent.createChooser(shareIntent, "حفظ أو مشاركة ملف النسخة الاحتياطية"))
            Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_SHORT).show()
        }

        binding.btnImportJson.setOnClickListener {
            val input = EditText(this).apply {
                hint = "الصق نص JSON هنا..."
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@PrivacySuiteActivity, R.color.text_primary))
                setBackgroundResource(R.drawable.bg_stat_chip)
                setPadding(16, 16, 16, 16)
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.backup_import_btn)
                .setMessage("الصق نص النسخة الاحتياطية (JSON) لاستعادة جميع إعداداتك:")
                .setView(input)
                .setPositiveButton("استعادة الآن 📥") { _, _ ->
                    val text = input.text.toString().trim()
                    if (text.isNotBlank() && prefs.importSettingsFromJson(text)) {
                        Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "صيغة ملف JSON غير صحيحة!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        binding.btnShareQrCode.setOnClickListener {
            val json = prefs.exportSettingsToJson()
            val base64 = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)

            AlertDialog.Builder(this)
                .setTitle(R.string.backup_qr_dialog_title)
                .setMessage("كود المشاركة السريع المشفر (Base64):\n\n${base64.take(160)}...\n\nيمكنك نسخه واستيراده في أي هاتف آخر مباشرة.")
                .setPositiveButton("نسخ الكود 📋") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Quick Code", base64))
                    Toast.makeText(this, "تم نسخ كود المشاركة!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إغلاق", null)
                .show()
        }

        binding.btnCheckUpdates.setOnClickListener {
            UpdateManager.showUpdateDialog(this)
        }
    }
}
