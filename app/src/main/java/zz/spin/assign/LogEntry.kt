package zz.spin.assign

import java.util.Date

data class LogEntry(
    val points: Long, // Changed from Int to Long for larger numbers
    val timestamp: Date
)
