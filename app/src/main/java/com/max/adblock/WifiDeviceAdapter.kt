package com.max.adblock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.max.adblock.databinding.ItemWifiDeviceBinding

class WifiDeviceAdapter(
    private var devices: List<WifiDevice> = emptyList()
) : RecyclerView.Adapter<WifiDeviceAdapter.DeviceViewHolder>() {

    fun updateData(newDevices: List<WifiDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemWifiDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(private val binding: ItemWifiDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: WifiDevice) {
            val context = binding.root.context
            binding.tvDeviceName.text = device.name
            binding.tvDeviceIp.text = device.ip
            binding.tvDevicePing.text = "${device.pingMs} ms"

            when {
                device.isThisDevice -> {
                    binding.ivDeviceIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.neon_cyan)
                    )
                    binding.tvDevicePing.setTextColor(
                        ContextCompat.getColor(context, R.color.neon_cyan)
                    )
                }
                device.isGateway -> {
                    binding.ivDeviceIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.neon_amber)
                    )
                    binding.tvDevicePing.setTextColor(
                        ContextCompat.getColor(context, R.color.neon_green)
                    )
                }
                else -> {
                    binding.ivDeviceIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.text_secondary)
                    )
                    binding.tvDevicePing.setTextColor(
                        ContextCompat.getColor(context, R.color.neon_green)
                    )
                }
            }
        }
    }
}
