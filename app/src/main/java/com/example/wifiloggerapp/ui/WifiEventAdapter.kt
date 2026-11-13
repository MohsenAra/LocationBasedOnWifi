package com.example.wifiloggerapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wifiloggerapp.R
import com.example.wifiloggerapp.data.WifiEventEntity
import com.example.wifiloggerapp.util.JalaliDateHelper
import com.example.wifiloggerapp.util.NetworkAliasManager

class WifiEventAdapter(
    private val aliasManager: NetworkAliasManager,
    private val onItemClick: (WifiEventEntity) -> Unit
) : RecyclerView.Adapter<WifiEventAdapter.EventViewHolder>() {

    private val items = mutableListOf<WifiEventEntity>()

    fun submitList(newItems: List<WifiEventEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEventBadge: TextView = itemView.findViewById(R.id.tvEventBadge)
        private val tvEventDate: TextView = itemView.findViewById(R.id.tvEventDate)
        private val tvNetworkTitle: TextView = itemView.findViewById(R.id.tvNetworkTitle)
        private val tvNetworkSsid: TextView = itemView.findViewById(R.id.tvNetworkSsid)
        private val tvNetworkBssid: TextView = itemView.findViewById(R.id.tvNetworkBssid)

        fun bind(item: WifiEventEntity) {
            val context = itemView.context
            val isConnect = item.eventType.equals("CONNECTED", ignoreCase = true)

            if (isConnect) {
                tvEventBadge.text = "🟢 ورود به شبکه"
                tvEventBadge.setBackgroundResource(R.drawable.badge_connected)
                tvEventBadge.setTextColor(ContextCompat.getColor(context, R.color.status_connected_text))
            } else {
                tvEventBadge.text = "🔴 خروج از شبکه"
                tvEventBadge.setBackgroundResource(R.drawable.badge_disconnected)
                tvEventBadge.setTextColor(ContextCompat.getColor(context, R.color.status_disconnected_text))
            }

            tvEventDate.text = JalaliDateHelper.formatShamsiDateTime(item.timestamp)

            val rawSsid = item.ssid ?: "<نامشخص>"
            val alias = aliasManager.getAlias(item.ssid)

            if (!alias.isNullOrBlank()) {
                tvNetworkTitle.text = "🏷️ $alias"
                tvNetworkSsid.text = "نام شبکه اصلی: $rawSsid"
            } else {
                tvNetworkTitle.text = "📶 $rawSsid"
                tvNetworkSsid.text = "لمس کنید تا نام دلخواه تعیین شود"
            }

            tvNetworkBssid.text = "BSSID: ${item.bssid ?: "<نامشخص>"}"

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
