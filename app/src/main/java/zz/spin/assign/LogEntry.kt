package zz.spin.assign

import java.util.Date

data class LogEntry(
    val points: Long, // Changed from Int to Long for larger numbers
    val timestamp: Date,
    val isPaid: Boolean = false, // New field to track if assignment is paid
    val isCustomAdd: Boolean = false // New field to track if it's custom added points
)