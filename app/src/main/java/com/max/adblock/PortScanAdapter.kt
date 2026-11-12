package com.max.adblock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.max.adblock.databinding.ItemPortScanBinding

class PortScanAdapter(
    private var ports: List<PortScanResult> = emptyList()
) : RecyclerView.Adapter<PortScanAdapter.PortViewHolder>() {

    fun updateData(newPorts: List<PortScanResult>) {
        ports = newPorts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortScanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(ports[position])
    }

    override fun getItemCount(): Int = ports.size

    class PortViewHolder(private val binding: ItemPortScanBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: PortScanResult) {
            val context = binding.root.context
            binding.tvPortNumber.text = result.port.toString()
            binding.tvPortService.text = result.serviceName
            binding.tvPortDesc.text = result.description

            if (result.isOpen) {
                binding.tvPortStatus.text = context.getString(R.string.port_open)
                binding.tvPortStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.neon_red)
                )
                binding.tvPortNumber.setTextColor(
                    ContextCompat.getColor(context, R.color.neon_red)
                )
            } else {
                binding.tvPortStatus.text = context.getString(R.string.port_closed)
                binding.tvPortStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.neon_green)
                )
                binding.tvPortNumber.setTextColor(
                    ContextCompat.getColor(context, R.color.neon_cyan)
                )
            }
        }
    }
}
