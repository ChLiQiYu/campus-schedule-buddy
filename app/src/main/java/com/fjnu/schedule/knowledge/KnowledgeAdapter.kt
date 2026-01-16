package com.fjnu.schedule.knowledge

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fjnu.schedule.R
import com.fjnu.schedule.data.KnowledgePointEntity
import com.fjnu.schedule.model.KnowledgeStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class KnowledgeAdapter(
    private val onStatusClick: (KnowledgePointEntity) -> Unit,
    private val onItemClick: (KnowledgePointEntity) -> Unit,
    private val onItemLongClick: (KnowledgePointEntity) -> Unit
) : ListAdapter<KnowledgePointEntity, KnowledgeAdapter.KnowledgeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KnowledgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_knowledge_point, parent, false)
        return KnowledgeViewHolder(view, onStatusClick, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: KnowledgeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class KnowledgeViewHolder(
        itemView: View,
        private val onStatusClick: (KnowledgePointEntity) -> Unit,
        private val onItemClick: (KnowledgePointEntity) -> Unit,
        private val onItemLongClick: (KnowledgePointEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleView: TextView = itemView.findViewById(R.id.tv_point_title)
        private val keyView: TextView = itemView.findViewById(R.id.tv_point_key)
        private val statusView: TextView = itemView.findViewById(R.id.tv_point_status)
        private val reviewedView: TextView = itemView.findViewById(R.id.tv_point_reviewed)
        private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")

        fun bind(point: KnowledgePointEntity) {
            titleView.text = point.title

            val status = KnowledgeStatus.fromLevel(point.masteryLevel)
            statusView.text = status.label
            statusView.background = buildChipBackground(statusColor(status))

            keyView.visibility = if (point.isKeyPoint) View.VISIBLE else View.GONE
            if (point.isKeyPoint) {
                keyView.background = buildChipBackground(
                    ContextCompat.getColor(itemView.context, R.color.knowledge_keypoint)
                )
            }

            reviewedView.text = if (point.lastReviewedAt != null) {
                val date = Instant.ofEpochMilli(point.lastReviewedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                "最近复习：${date.format(dateFormatter)}"
            } else {
                "最近复习：--"
            }

            statusView.setOnClickListener { onStatusClick(point) }
            itemView.setOnClickListener { onItemClick(point) }
            itemView.setOnLongClickListener {
                onItemLongClick(point)
                true
            }
        }

        private fun statusColor(status: KnowledgeStatus): Int {
            return when (status) {
                KnowledgeStatus.NOT_STARTED ->
                    ContextCompat.getColor(itemView.context, R.color.knowledge_not_started)
                KnowledgeStatus.LEARNING ->
                    ContextCompat.getColor(itemView.context, R.color.knowledge_learning)
                KnowledgeStatus.MASTERED ->
                    ContextCompat.getColor(itemView.context, R.color.knowledge_mastered)
                KnowledgeStatus.STUCK ->
                    ContextCompat.getColor(itemView.context, R.color.knowledge_stuck)
            }
        }

        private fun buildChipBackground(color: Int): GradientDrawable {
            val radius = dpToPx(10)
            return GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius.toFloat()
            }
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * itemView.resources.displayMetrics.density + 0.5f).toInt()
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<KnowledgePointEntity>() {
            override fun areItemsTheSame(
                oldItem: KnowledgePointEntity,
                newItem: KnowledgePointEntity
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: KnowledgePointEntity,
                newItem: KnowledgePointEntity
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
