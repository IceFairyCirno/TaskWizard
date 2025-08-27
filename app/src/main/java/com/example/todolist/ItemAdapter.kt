package com.example.todolist

import android.media.Image
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ItemAdapter(private var items: List<EventEntity>, private val onButtonClick: (EventEntity) -> Unit) :
    RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.ItemTitle)
        val date: TextView = itemView.findViewById(R.id.ItemDeadline)
        val deletebtn: ImageButton = itemView.findViewById(R.id.DeleteEventButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = items[position]
        holder.title.text = event.title
        holder.date.text = "${event.date}, ${event.time}"

        holder.deletebtn.setOnClickListener {
            onButtonClick(event)
        }
    }

    override fun getItemCount(): Int = items.size

    // Update the entire list when data changes
    fun updateItems(newItems: List<EventEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}

