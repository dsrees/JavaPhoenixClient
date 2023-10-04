package com.github.dsrees.chatexample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.dsrees.chatexample.databinding.ItemMessageBinding

class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

    private var messages: MutableList<String> = mutableListOf()

    fun add(message: String) {
        messages.add(message)
        notifyItemInserted(messages.size)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.label.text = messages[position]
    }

    inner class ViewHolder(binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        val label: TextView = binding.itemMessageLabel
    }

}