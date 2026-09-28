package com.mypanel.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(private val onClick: (Int) -> Unit) :
    RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var items: List<Channel> = emptyList()
    private var selected = -1

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val group: TextView = v.findViewById(R.id.group)
    }

    fun submit(list: List<Channel>) {
        items = list
        selected = -1
        notifyDataSetChanged()
    }

    fun select(pos: Int) {
        val old = selected
        selected = pos
        if (old in items.indices) notifyItemChanged(old)
        if (pos in items.indices) notifyItemChanged(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        h.name.text = c.name
        h.group.text = c.group
        if (c.logo.isNotBlank()) {
            h.logo.load(c.logo) {
                size(120)
                error(android.R.drawable.ic_menu_gallery)
            }
        } else {
            h.logo.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        h.itemView.isActivated = position == selected
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
    }
}
