package com.example.todolist

import android.content.res.ColorStateList
import android.graphics.Color
import android.media.Image
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class ItemAdapter(private var items: List<EventEntity>, private val onButtonClick: (EventEntity) -> Unit) :
    RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.ItemTitle)
        val date: TextView = itemView.findViewById(R.id.ItemDeadlineDate)
        val time: TextView = itemView.findViewById(R.id.ItemDeadlineTime)
        val deletebtn: ImageButton = itemView.findViewById(R.id.DeleteEventButton)
        val eventType: ImageButton = itemView.findViewById(R.id.EventType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = items[position]
        holder.title.text = event.title
        holder.date.text = event.date
        holder.time.text = event.time
        holder.eventType.setImageResource(
            when (event.type) {
                "home" -> R.drawable.ic_home
                "work" -> R.drawable.ic_work
                "friend" -> R.drawable.ic_people
                "personal" -> R.drawable.ic_personal
                "study" -> R.drawable.ic_computer
                else -> R.drawable.ic_personal
            }
        )
        holder.eventType.backgroundTintList = ColorStateList.valueOf(
            when (event.type) {
                "home" -> "#F5E8C7".toColorInt()
                "work" -> "#4682B4".toColorInt()
                "friend" -> "#FF6F61".toColorInt()
                "personal" -> "#A8D5BA".toColorInt()
                "study" -> "#2E2E2E".toColorInt()
                else -> "#A8D5BA".toColorInt()
            }
        )

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

