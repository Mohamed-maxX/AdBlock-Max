package com.max.adblock

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.max.adblock.databinding.ItemRadarLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RadarLogAdapter(
    private val context: Context,
    private var logs: List<DnsLogEntry>,
    private val onLogClicked: (DnsLogEntry) -> Unit
) : RecyclerView.Adapter<RadarLogAdapter.LogViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    inner class LogViewHolder(val binding: ItemRadarLogBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding =
            ItemRadarLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun getItemCount(): Int = logs.size

    fun updateList(newLogs: List<DnsLogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logs[position]
        val binding = holder.binding

        binding.tvLogDomain.text = entry.domain
        binding.tvLogTime.text = timeFormat.format(Date(entry.timestamp))
        binding.tvLogRule.text = entry.ruleMatched
        binding.tvLogType.text = entry.queryType

        if (entry.isBlocked) {
            binding.tvLogStatus.text = "محظور 🚫"
            binding.tvLogStatus.setTextColor(ContextCompat.getColor(context, R.color.neon_red))
            binding.tvLogDomain.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            binding.tvLogRule.setTextColor(ContextCompat.getColor(context, R.color.neon_red))
        } else {
            binding.tvLogStatus.text = "مسموح 🟢"
            binding.tvLogStatus.setTextColor(ContextCompat.getColor(context, R.color.neon_green))
            binding.tvLogDomain.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
            binding.tvLogRule.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
        }

        binding.root.setOnClickListener {
            onLogClicked(entry)
        }
    }
}
