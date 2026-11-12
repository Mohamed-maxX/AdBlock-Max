package com.max.adblock

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.max.adblock.databinding.ItemAppFirewallBinding

class AppFirewallAdapter(
    private val context: Context,
    private var appsList: List<AppInfo>,
    private val onBypassChanged: (AppInfo, Boolean) -> Unit,
    private val onBlockChanged: (AppInfo, Boolean) -> Unit,
    private val onSetQuotaClicked: (AppInfo) -> Unit,
    private val onTrackerDetailsClicked: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppFirewallAdapter.AppViewHolder>() {

    inner class AppViewHolder(val binding: ItemAppFirewallBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding =
            ItemAppFirewallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun getItemCount(): Int = appsList.size

    fun updateList(newList: List<AppInfo>) {
        appsList = newList
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appsList[position]
        val binding = holder.binding

        binding.ivAppIcon.setImageDrawable(app.icon)
        binding.tvAppName.text = app.appName
        binding.tvPackageName.text = app.packageName

        // Data usage
        val formattedBytes = Formatter.formatFileSize(context, app.usedTodayBytes)
        binding.tvDataUsage.text = "استهلاك: $formattedBytes"

        // Quota
        if (app.dailyQuotaMb > 0) {
            binding.tvQuotaBadge.text = "الكوتة: ${app.dailyQuotaMb} MB"
            binding.tvQuotaBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_amber))
        } else {
            binding.tvQuotaBadge.text = "الكوتة: بلا حدود"
            binding.tvQuotaBadge.setTextColor(ContextCompat.getColor(context, R.color.text_dim))
        }

        // Trackers & Battery Risk
        when (app.batteryDrainRisk) {
            BatteryDrainRisk.HIGH -> {
                binding.tvRiskBadge.text = "${app.trackerCount} متتبعات • مرتفع الخطورة 🔴"
                binding.tvRiskBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_red))
            }
            BatteryDrainRisk.MEDIUM -> {
                binding.tvRiskBadge.text = "${app.trackerCount} متتبعات • متوسط 🟡"
                binding.tvRiskBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_amber))
            }
            BatteryDrainRisk.LOW -> {
                binding.tvRiskBadge.text = "خالي من المتتبعات ✅"
                binding.tvRiskBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_green))
            }
        }

        // Switches setup without triggering recursive listeners
        binding.switchBypass.setOnCheckedChangeListener(null)
        binding.switchBlock.setOnCheckedChangeListener(null)

        binding.switchBypass.isChecked = app.isBypassed
        binding.switchBlock.isChecked = app.isFirewallBlocked

        binding.switchBypass.setOnCheckedChangeListener { _, isChecked ->
            app.isBypassed = isChecked
            if (isChecked) {
                app.isFirewallBlocked = false
                binding.switchBlock.isChecked = false
            }
            onBypassChanged(app, isChecked)
        }

        binding.switchBlock.setOnCheckedChangeListener { _, isChecked ->
            app.isFirewallBlocked = isChecked
            if (isChecked) {
                app.isBypassed = false
                binding.switchBypass.isChecked = false
            }
            onBlockChanged(app, isChecked)
        }

        binding.btnSetQuota.setOnClickListener {
            onSetQuotaClicked(app)
        }

        binding.btnTrackerInfo.setOnClickListener {
            onTrackerDetailsClicked(app)
        }

        binding.root.setOnClickListener {
            onTrackerDetailsClicked(app)
        }
    }
}
