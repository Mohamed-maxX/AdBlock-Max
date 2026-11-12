package com.max.adblock

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.max.adblock.databinding.ItemGhostAppBinding

class GhostAdAdapter(
    private var apps: List<GhostAdApp> = emptyList()
) : RecyclerView.Adapter<GhostAdAdapter.GhostViewHolder>() {

    fun updateData(newApps: List<GhostAdApp>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GhostViewHolder {
        val binding = ItemGhostAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GhostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GhostViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    class GhostViewHolder(private val binding: ItemGhostAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: GhostAdApp) {
            val context = binding.root.context
            binding.tvGhostAppName.text = app.appName
            binding.tvGhostAppPackage.text = app.packageName
            if (app.icon != null) {
                binding.ivGhostAppIcon.setImageDrawable(app.icon)
            } else {
                binding.ivGhostAppIcon.setImageResource(R.mipmap.ic_launcher)
            }

            binding.tvGhostAppRisk.text = app.riskLevel
            if (app.riskLevel.contains("خطر عالي")) {
                binding.tvGhostAppRisk.setTextColor(ContextCompat.getColor(context, R.color.neon_red))
            } else if (app.riskLevel.contains("صلاحية")) {
                binding.tvGhostAppRisk.setTextColor(ContextCompat.getColor(context, R.color.neon_amber))
            } else {
                binding.tvGhostAppRisk.setTextColor(ContextCompat.getColor(context, R.color.neon_green))
            }

            val trackersStr = if (app.adTrackers.isNotEmpty()) {
                "الشبكات الإعلانية المكتشفة: " + app.adTrackers.joinToString(", ")
            } else {
                "لا توجد شبكات إعلانية معروفة بالداخل"
            }

            val overlayStr = if (app.hasOverlayPermission) " • يمتلك صلاحية الظهور فوق الشاشة" else ""
            binding.tvGhostAppTrackers.text = trackersStr + overlayStr

            binding.btnManageGhostApp.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
