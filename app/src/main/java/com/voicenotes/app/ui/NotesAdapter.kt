package com.voicenotes.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.voicenotes.app.databinding.ItemNoteBinding
import com.voicenotes.app.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔记列表适配器。
 */
class NotesAdapter(
    private val onClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.VH>() {

    private val items = mutableListOf<Note>()

    fun submit(list: List<Note>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemNoteBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(note: Note) {
            b.title.text = note.title.ifBlank { "语音笔记" }
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(note.createdAt))
            b.meta.text = "$date · ${formatDuration(note.durationMs)}"
            b.preview.text = note.transcript.ifBlank { "（无转写内容）" }
            b.root.setOnClickListener { onClick(note) }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        return String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60)
    }
}
