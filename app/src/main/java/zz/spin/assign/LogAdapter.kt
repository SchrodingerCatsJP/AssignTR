package zz.spin.assign

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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
                if (logEntry.isPaid) {
                    // PAID status - light yellow background
                    holder.detailsTextView.text = "PAID : +${numberFormatter.format(logEntry.points)}"
                    holder.detailsTextView.setTextColor(Color.parseColor("#A3F527"))
                    holder.detailsTextView.setBackgroundColor(Color.TRANSPARENT) // Light yellow
                    holder.timestampTextView.setBackgroundColor(Color.TRANSPARENT) // Light yellow
                } else if (logEntry.isCustomAdd) {
                    // Custom added points
                    holder.detailsTextView.text = "ADD : +${numberFormatter.format(logEntry.points)}"
                    holder.detailsTextView.setTextColor(Color.parseColor("#4CAF50")) // Green color
                    holder.detailsTextView.setBackgroundColor(Color.TRANSPARENT)
                    holder.timestampTextView.setBackgroundColor(Color.TRANSPARENT)
                } else {
                    // Regular DONE points
                    holder.detailsTextView.text = "DONE : +${numberFormatter.format(logEntry.points)}"
                    holder.detailsTextView.setTextColor(Color.parseColor("#4CAF50"))
                    holder.detailsTextView.setBackgroundColor(Color.TRANSPARENT)
                    holder.timestampTextView.setBackgroundColor(Color.TRANSPARENT)
                }
            }
            logEntry.points < 0 -> {
                val usedPoints = -logEntry.points
                holder.detailsTextView.text = "USED: -${numberFormatter.format(usedPoints)}"
                holder.detailsTextView.setTextColor(Color.RED)
                holder.detailsTextView.setBackgroundColor(Color.TRANSPARENT)
                holder.timestampTextView.setBackgroundColor(Color.TRANSPARENT)
            }
            else -> { // points == 0
                holder.detailsTextView.text = "SKIPPED"
                holder.detailsTextView.setTextColor(Color.RED)
                holder.detailsTextView.setBackgroundColor(Color.TRANSPARENT)
                holder.timestampTextView.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        val logCalendar = Calendar.getInstance()
        logCalendar.time = logEntry.timestamp

        val timestampText = when {
            today.get(Calendar.YEAR) == logCalendar.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == logCalendar.get(Calendar.DAY_OF_YEAR) -> {
                "TODAY"
            }
            yesterday.get(Calendar.YEAR) == logCalendar.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == logCalendar.get(Calendar.DAY_OF_YEAR) -> {
                "YESTERDAY"
            }
            else -> {
                val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                formatter.format(logEntry.timestamp)
            }
        }
        holder.timestampTextView.text = timestampText
    }

    override fun getItemCount() = logs.size
}
