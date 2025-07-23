package zz.spin.assign

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class LogAdapter(private val logs: List<LogEntry>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val detailsTextView: TextView = itemView.findViewById(R.id.textViewLogDetails)
        val timestampTextView: TextView = itemView.findViewById(R.id.textViewLogTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.log_item, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val logEntry = logs[position]
        val numberFormatter = NumberFormat.getInstance(Locale("id", "ID"))

        val defaultColor = holder.timestampTextView.currentTextColor

        when {
            logEntry.points > 0 -> {
                holder.detailsTextView.text = "Points: ${numberFormatter.format(logEntry.points)}"
                holder.detailsTextView.setTextColor(defaultColor)
            }
            logEntry.points < 0 -> {
                val usedPoints = -logEntry.points
                holder.detailsTextView.text = "Used: ${numberFormatter.format(usedPoints)}"
                holder.detailsTextView.setTextColor(Color.RED)
            }
            else -> { // points == 0
                holder.detailsTextView.text = "SKIPPED"
                holder.detailsTextView.setTextColor(Color.RED)
            }
        }

        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        holder.timestampTextView.text = formatter.format(logEntry.timestamp)
    }

    override fun getItemCount() = logs.size
}
