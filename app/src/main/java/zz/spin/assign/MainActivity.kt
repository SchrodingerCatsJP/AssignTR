package zz.spin.assign

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import zz.spin.assign.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data class to hold all app data for export/import
data class AppData(
    val logEntries: List<LogEntry>,
    val lastActionDate: Long
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logEntries = mutableListOf<LogEntry>()
    private lateinit var logAdapter: LogAdapter

    private val numberFormatter = NumberFormat.getInstance(Locale("id", "ID"))

    // Constants for SharedPreferences
    private val PREFS_NAME = "AssignmentTrackerPrefs"
    private val LOGS_KEY = "logEntries"
    private val LAST_ACTION_DATE_KEY = "lastActionDate"
    private val LAST_APP_OPEN_KEY = "lastAppOpen"
    private val EXIT_NOTIFICATION_SHOWN_KEY = "exitNotificationShown"

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    // Track if user has interacted with DONE/SKIPPED today
    private var hasInteractedToday = false

    // MediaPlayer for sound effects
    private var confirmationSound: MediaPlayer? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                ReminderBroadcastReceiver.scheduleDailyReminder(this)
                AppOpenReminderReceiver.scheduleDailyAppOpenReminder(this)
                Toast.makeText(this, "Reminder notifications enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    // Export/Import launchers
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            exportLogsToUri(uri)
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importLogsFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the custom toolbar
        setSupportActionBar(binding.toolbar.toolbarCustom)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        loadLogs()
        setupRecyclerView()
        setupClickListeners()
        updatePointsUI()
        updateButtonStateForToday()
        setupGraph()

        // Record that the app was opened today
        recordAppOpen()

        askForNotificationPermission()

        // Initialize confirmation sound
        initializeConfirmationSound()
    }

    private fun initializeConfirmationSound() {
        try {
            // Using system notification sound as confirmation sound
            val soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            confirmationSound = MediaPlayer.create(this, soundUri)
            confirmationSound?.setVolume(0.7f, 0.7f) // Set volume to 70%
        } catch (e: Exception) {
            // Fallback: create a simple beep sound programmatically
            confirmationSound = null
        }
    }

    private fun playConfirmationSound() {
        try {
            confirmationSound?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                    player.prepare()
                }
                player.start()
            }
        } catch (e: Exception) {
            // If sound fails, provide haptic feedback as fallback
            // This is already handled in the button press
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset exit notification flag when app comes to foreground
        resetExitNotificationFlag()
        // Record app open time
        recordAppOpen()
    }

    override fun onPause() {
        super.onPause()
        // Check if we should show exit notification
        checkAndScheduleExitNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }

        // Release MediaPlayer resources
        confirmationSound?.release()
        confirmationSound = null
    }

    private fun recordAppOpen() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putLong(LAST_APP_OPEN_KEY, System.currentTimeMillis())
        prefs.apply()
    }

    private fun resetExitNotificationFlag() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putBoolean(EXIT_NOTIFICATION_SHOWN_KEY, false)
        prefs.apply()
    }

    private fun checkAndScheduleExitNotification() {
        // Check if user has already interacted today
        checkIfInteractedToday()

        // Only schedule exit notification if user hasn't interacted today
        if (!hasInteractedToday) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val exitNotificationShown = prefs.getBoolean(EXIT_NOTIFICATION_SHOWN_KEY, false)

            if (!exitNotificationShown) {
                // Schedule immediate notification (after 3 seconds to ensure app is truly backgrounded)
                ExitNotificationReceiver.scheduleExitNotification(this)

                // Mark that we've shown the notification for today
                val editor = prefs.edit()
                editor.putBoolean(EXIT_NOTIFICATION_SHOWN_KEY, true)
                editor.apply()
            }
        }
    }

    private fun checkIfInteractedToday() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActionTime = prefs.getLong(LAST_ACTION_DATE_KEY, 0)

        if (lastActionTime == 0L) {
            hasInteractedToday = false
            return
        }

        val lastActionCalendar = Calendar.getInstance().apply { timeInMillis = lastActionTime }
        val todayCalendar = Calendar.getInstance()

        hasInteractedToday = lastActionCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                lastActionCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                ReminderBroadcastReceiver.scheduleDailyReminder(this)
                AppOpenReminderReceiver.scheduleDailyAppOpenReminder(this)
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            ReminderBroadcastReceiver.scheduleDailyReminder(this)
            AppOpenReminderReceiver.scheduleDailyAppOpenReminder(this)
        }
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(logEntries)
        binding.recyclerViewLogs.adapter = logAdapter
        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonDone.setOnTouchListener(createHoldListener(20000L, "DONE Logged!", false, false))
        binding.buttonSkipped.setOnTouchListener(createHoldListener(0L, "SKIPPED Logged!", false, false))

        binding.fabAddCustom.setOnClickListener {
            showCustomPointsDialog(true)
        }

        binding.fabSubtractCustom.setOnClickListener {
            showCustomPointsDialog(false)
        }

        // NEW: Set up PAID button click listener
        binding.fabPaid.setOnClickListener {
            showPaidDialog()
        }

        // NEW: Set up Export/Import button click listeners
        binding.buttonExport.setOnClickListener {
            showExportDialog()
        }

        binding.buttonImport.setOnClickListener {
            showImportDialog()
        }
    }

    private fun setupGraph() {
        // Initial graph setup - data will be populated in updateGraph()
        updateGraph()
    }

    private fun updateGraph() {
        val graphView = binding.simpleGraphView
        val points = mutableListOf<Float>()
        val dateLabels = mutableListOf<String>()

        // Get last 7 days data
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        for (i in 6 downTo 0) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -i)

            val dayStart = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayEnd = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            // Calculate net points for that day (earned - subtracted)
            val dayPoints = logEntries.filter { log ->
                log.timestamp.time >= dayStart.timeInMillis &&
                        log.timestamp.time <= dayEnd.timeInMillis
            }.sumOf { it.points }.toFloat()

            points.add(dayPoints)
            dateLabels.add(dateFormat.format(calendar.time))
        }

        // Update the graph with new data
        graphView.setData(points, dateLabels)
    }

    private fun createHoldListener(points: Long, message: String, isPaid: Boolean = false, isCustomAdd: Boolean = false): View.OnTouchListener {
        var progressAnimator: ObjectAnimator? = null
        val handler = Handler(Looper.getMainLooper())
        var actionRunnable: Runnable? = null

        return View.OnTouchListener { view, event ->
            if (!view.isEnabled) return@OnTouchListener false
            val layerDrawable = view.background as? LayerDrawable ?: return@OnTouchListener false
            val drawable = layerDrawable.findDrawableByLayerId(android.R.id.progress) as? ClipDrawable ?: return@OnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    progressAnimator = ObjectAnimator.ofInt(drawable, "level", 0, 10000).apply { duration = 2000; start() }
                    actionRunnable = Runnable {
                        addLog(points, isPaid, isCustomAdd)

                        // Play confirmation sound
                        playConfirmationSound()

                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        drawable.level = 0
                        // The daily lock is now triggered ONLY by the DONE and SKIPPED buttons
                        saveLastActionDate(System.currentTimeMillis())
                        updateButtonStateForToday()
                        // Update interaction flag
                        hasInteractedToday = true
                        // Cancel any pending exit notifications since user interacted
                        ExitNotificationReceiver.cancelExitNotification(this@MainActivity)
                    }
                    handler.postDelayed(actionRunnable!!, 2000)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(actionRunnable!!)
                    progressAnimator?.cancel()
                    drawable.level = 0
                    true
                }
                else -> false
            }
        }
    }

    private fun updateButtonStateForToday() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActionTime = prefs.getLong(LAST_ACTION_DATE_KEY, 0)
        if (lastActionTime == 0L) {
            setButtonsEnabled(true)
            return
        }
        val lastActionCalendar = Calendar.getInstance().apply { timeInMillis = lastActionTime }
        val todayCalendar = Calendar.getInstance()
        val isSameDay = lastActionCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                lastActionCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)
        setButtonsEnabled(!isSameDay)
        hasInteractedToday = isSameDay
    }

    private fun setButtonsEnabled(isEnabled: Boolean) {
        binding.buttonDone.isEnabled = isEnabled
        binding.buttonSkipped.isEnabled = isEnabled
        binding.buttonDone.alpha = if (isEnabled) 1.0f else 0.5f
        binding.buttonSkipped.alpha = if (isEnabled) 1.0f else 0.5f
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        if (isEnabled) {
            binding.buttonDone.text = "DONE (+20,000)"
            binding.buttonSkipped.text = "SKIPPED"
        } else {
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            countdownRunnable = object : Runnable {
                override fun run() {
                    val timeRemaining = midnight.timeInMillis - System.currentTimeMillis()
                    if (timeRemaining > 0) {
                        val hours = (timeRemaining / (1000 * 60 * 60)) % 24
                        val minutes = (timeRemaining / (1000 * 60)) % 60
                        val seconds = (timeRemaining / 1000) % 60
                        val countdownText = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                        binding.buttonDone.text = countdownText
                        binding.buttonSkipped.text = countdownText
                        countdownHandler.postDelayed(this, 1000)
                    } else {
                        updateButtonStateForToday()
                    }
                }
            }
            countdownHandler.post(countdownRunnable!!)
        }
    }

    private fun saveLastActionDate(timestamp: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putLong(LAST_ACTION_DATE_KEY, timestamp)
        prefs.apply()
    }

    // NEW: Show dialog to mark assignments as PAID
    private fun showPaidDialog() {
        // Get all DONE assignments that are not yet paid
        val unpaidDoneAssignments = logEntries.filter {
            it.points == 20000L && !it.isPaid && !it.isCustomAdd
        }

        if (unpaidDoneAssignments.isEmpty()) {
            Toast.makeText(this, "No unpaid assignments found", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a list of assignment descriptions for the dialog
        val assignmentDescriptions = unpaidDoneAssignments.mapIndexed { index, entry ->
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            "Assignment ${index + 1} - ${formatter.format(entry.timestamp)}"
        }.toTypedArray()

        val checkedItems = BooleanArray(assignmentDescriptions.size) { false }

        AlertDialog.Builder(this)
            .setTitle("Mark Assignments as PAID")
            .setMultiChoiceItems(assignmentDescriptions, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Mark as PAID") { dialog, _ ->
                var markedCount = 0
                checkedItems.forEachIndexed { index, isChecked ->
                    if (isChecked) {
                        val assignmentToMark = unpaidDoneAssignments[index]
                        // Find the assignment in the main list and mark it as paid
                        val mainIndex = logEntries.indexOf(assignmentToMark)
                        if (mainIndex != -1) {
                            logEntries[mainIndex] = assignmentToMark.copy(isPaid = true)
                            markedCount++
                        }
                    }
                }

                if (markedCount > 0) {
                    logAdapter.notifyDataSetChanged()
                    saveLogs()
                    updatePointsUI() // <-- BUG FIX: Refresh the UI after marking as paid
                    Toast.makeText(this, "$markedCount assignment(s) marked as PAID", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun showCustomPointsDialog(isAdding: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_points_keypad, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editTextDialogCustomPoints)
        val dialogTitle = if (isAdding) "Add Custom Points" else "Use Custom Points"
        val buttonText = if (isAdding) "Add" else "Use"

        val keypadButtons = mapOf(
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad1) to "1",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad2) to "2",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad3) to "3",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad4) to "4",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad5) to "5",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad6) to "6",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad7) to "7",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad8) to "8",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad9) to "9",
            dialogView.findViewById<MaterialButton>(R.id.buttonKeypad0) to "0"
        )

        keypadButtons.forEach { (button, number) ->
            button.setOnClickListener {
                editText.setText(editText.text.toString() + number)
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.buttonKeypadClear).setOnClickListener {
            editText.setText("")
        }

        dialogView.findViewById<MaterialButton>(R.id.buttonKeypadBackspace).setOnClickListener {
            val currentText = editText.text.toString()
            if (currentText.isNotEmpty()) {
                editText.setText(currentText.substring(0, currentText.length - 1))
            }
        }

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(buttonText) { dialog, _ ->
                val customPointsText = editText.text.toString()
                if (customPointsText.isNotEmpty()) {
                    try {
                        var customPoints = customPointsText.toLong()
                        if (!isAdding) {
                            customPoints = -customPoints
                        }
                        // Mark custom added points with isCustomAdd = true
                        addLog(customPoints, false, isAdding)
                        val toastMessage = if (isAdding) "Points Added!" else "Points Used!"
                        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Value is too large", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter a point value", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun addLog(points: Long, isPaid: Boolean = false, isCustomAdd: Boolean = false) {
        logEntries.add(0, LogEntry(points = points, timestamp = Date(), isPaid = isPaid, isCustomAdd = isCustomAdd))
        logAdapter.notifyItemInserted(0)
        binding.recyclerViewLogs.scrollToPosition(0)
        updatePointsUI()
        updateGraph() // Update graph when new log is added
        saveLogs()
    }

    private fun updatePointsUI() {
        // Net Total Points (Earned - Used)
        val totalPoints = logEntries.sumOf { it.points }
        binding.textViewTotalPointsValue.text = numberFormatter.format(totalPoints)

        // Gross Total Points (PAID only)
        val grossTotal = logEntries.filter { it.isPaid }.sumOf { it.points }
        binding.textViewGrossTotalValue.text = "(Earned: ${numberFormatter.format(grossTotal)})"

        // Total Used Points
        val totalUsed = logEntries.filter { it.points < 0 }.sumOf { -it.points }
        binding.textViewTotalSubtractedValue.text = "(Used: ${numberFormatter.format(totalUsed)})"

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val monthlyLogs = logEntries.filter {
            calendar.time = it.timestamp
            calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear
        }

        // Net Monthly Points
        val monthlyPoints = monthlyLogs.sumOf { it.points }
        binding.textViewMonthlyPointsValue.text = numberFormatter.format(monthlyPoints)

        // Gross Monthly Points (PAID only)
        val grossMonthly = monthlyLogs.filter { it.isPaid }.sumOf { it.points }
        binding.textViewGrossMonthlyValue.text = "(Earned: ${numberFormatter.format(grossMonthly)})"

        // Used Monthly Points
        val usedMonthly = monthlyLogs.filter { it.points < 0 }.sumOf { -it.points }
        binding.textViewUsedMonthlyValue.text = "(Used: ${numberFormatter.format(usedMonthly)})"

        // Update Toolbar
        val completedCount = logEntries.count { it.points == 20000L }
        binding.toolbar.toolbarCompletedCount.text = getString(R.string.completed_count, completedCount)
    }

    private fun saveLogs() {
        // REPLACED GSON WITH NATIVE JSON
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val jsonArray = JSONArray()
        logEntries.forEach { entry ->
            val jsonObject = JSONObject()
            jsonObject.put("points", entry.points)
            jsonObject.put("timestamp", entry.timestamp.time)
            jsonObject.put("isPaid", entry.isPaid)
            jsonObject.put("isCustomAdd", entry.isCustomAdd)
            jsonArray.put(jsonObject)
        }
        editor.putString(LOGS_KEY, jsonArray.toString())
        editor.apply()
    }

    private fun loadLogs() {
        // REPLACED GSON WITH NATIVE JSON
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(LOGS_KEY, null)
        if (jsonString != null) {
            try {
                val loadedLogs = mutableListOf<LogEntry>()
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val points = jsonObject.getLong("points")
                    val timestamp = Date(jsonObject.getLong("timestamp"))
                    val isPaid = jsonObject.getBoolean("isPaid")
                    val isCustomAdd = jsonObject.getBoolean("isCustomAdd")
                    loadedLogs.add(LogEntry(points, timestamp, isPaid, isCustomAdd))
                }
                logEntries.clear()
                logEntries.addAll(loadedLogs)
            } catch (e: Exception) {
                // Handle JSON parsing error
            }
        }
    }

    // Export/Import Methods
    private fun showExportDialog() {
        val options = arrayOf("Export as JSON", "Export as CSV")

        AlertDialog.Builder(this)
            .setTitle("Export Logs")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAsJson()
                    1 -> exportAsCsv()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportAsJson() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "assignment_logs_$timestamp.json"
        exportLauncher.launch(fileName)
    }

    private fun exportAsCsv() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "assignment_logs_$timestamp.csv"
        exportLauncher.launch(fileName)
    }

    private fun exportLogsToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = OutputStreamWriter(outputStream)
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastActionDate = prefs.getLong(LAST_ACTION_DATE_KEY, 0)

                when {
                    uri.toString().endsWith(".json") -> {
                        val appDataObject = JSONObject()
                        appDataObject.put("lastActionDate", lastActionDate)

                        val logsArray = JSONArray()
                        logEntries.forEach { entry ->
                            val logObject = JSONObject()
                            logObject.put("points", entry.points)
                            logObject.put("timestamp", entry.timestamp.time)
                            logObject.put("isPaid", entry.isPaid)
                            logObject.put("isCustomAdd", entry.isCustomAdd)
                            logsArray.put(logObject)
                        }
                        appDataObject.put("logEntries", logsArray)

                        writer.write(appDataObject.toString(4)) // Use 4 for pretty printing
                        Toast.makeText(this, "Logs and state exported to JSON successfully!", Toast.LENGTH_LONG).show()
                    }
                    uri.toString().endsWith(".csv") -> {
                        // BUG FIX: Add lastActionDate to CSV export
                        writer.write("lastActionDate:$lastActionDate\n")
                        writer.write("Points,Timestamp,IsPaid,IsCustomAdd\n")
                        logEntries.forEach { entry ->
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val line = "${entry.points},${dateFormat.format(entry.timestamp)},${entry.isPaid},${entry.isCustomAdd}\n"
                            writer.write(line)
                        }
                        Toast.makeText(this, "Logs and state exported to CSV successfully!", Toast.LENGTH_LONG).show()
                    }
                }
                writer.flush()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Import Logs")
            .setMessage("This will replace all current logs and the daily checked-in status. Are you sure you want to continue?")
            .setPositiveButton("Import") { _, _ ->
                importLauncher.launch(arrayOf("application/json", "text/csv", "*/*"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importLogsFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()

                when {
                    uri.toString().endsWith(".json") -> {
                        importFromJson(content)
                    }
                    uri.toString().endsWith(".csv") -> {
                        importFromCsv(content)
                    }
                    else -> {
                        // Fallback for unknown file types, try JSON first
                        try {
                            importFromJson(content)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Could not import as JSON, attempting CSV. Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            importFromCsv(content)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromJson(jsonContent: String) {
        try {
            // REPLACED GSON WITH NATIVE JSON
            val importedLogs = mutableListOf<LogEntry>()
            val appDataObject = JSONObject(jsonContent)
            val logsArray = appDataObject.getJSONArray("logEntries")

            for (i in 0 until logsArray.length()) {
                val logObject = logsArray.getJSONObject(i)
                val points = logObject.getLong("points")
                val timestamp = Date(logObject.getLong("timestamp"))
                val isPaid = logObject.getBoolean("isPaid")
                val isCustomAdd = logObject.getBoolean("isCustomAdd")
                importedLogs.add(LogEntry(points, timestamp, isPaid, isCustomAdd))
            }

            // Replace current logs
            logEntries.clear()
            logEntries.addAll(importedLogs)

            // Save the imported last action date
            val lastActionDate = appDataObject.getLong("lastActionDate")
            saveLastActionDate(lastActionDate)
            saveLogs() // Save the new logs to preferences

            // Update UI completely
            logAdapter.notifyDataSetChanged()
            updatePointsUI()
            updateGraph()
            updateButtonStateForToday() // This is crucial to refresh the timer

            Toast.makeText(this, "Successfully imported ${importedLogs.size} logs and state!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            // This might be an old backup file, try parsing it as a simple array
            try {
                val importedLogs = mutableListOf<LogEntry>()
                val logsArray = JSONArray(jsonContent)
                for (i in 0 until logsArray.length()) {
                    val logObject = logsArray.getJSONObject(i)
                    val points = logObject.getLong("points")
                    val timestamp = Date(logObject.getLong("timestamp"))
                    val isPaid = logObject.getBoolean("isPaid")
                    val isCustomAdd = logObject.getBoolean("isCustomAdd")
                    importedLogs.add(LogEntry(points, timestamp, isPaid, isCustomAdd))
                }

                logEntries.clear()
                logEntries.addAll(importedLogs)
                saveLogs()
                logAdapter.notifyDataSetChanged()
                updatePointsUI()
                updateGraph()
                updateButtonStateForToday()
                Toast.makeText(this, "Imported old backup with ${importedLogs.size} logs. Daily status not updated.", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                throw Exception("Invalid JSON format. Could not parse as new or old backup.")
            }
        }
    }

    private fun importFromCsv(csvContent: String) {
        try {
            val lines = csvContent.split("\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) {
                throw Exception("CSV file is empty.")
            }

            val importedLogs = mutableListOf<LogEntry>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            var dataLines = lines

            // BUG FIX: Check for and parse lastActionDate from the first line
            if (lines.first().startsWith("lastActionDate:")) {
                try {
                    val lastActionTimestamp = lines.first().removePrefix("lastActionDate:").trim().toLong()
                    saveLastActionDate(lastActionTimestamp)
                } catch (e: Exception) {
                    // Ignore if parsing fails, treat as normal line
                }
                dataLines = lines.drop(1) // Remove the special header line
            }


            // Skip header line if present
            if (dataLines.first().contains("Points,Timestamp")) {
                dataLines = dataLines.drop(1)
            }

            dataLines.forEach { line ->
                if (line.trim().isNotEmpty()) {
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        try {
                            val points = parts[0].trim().toLong()
                            val timestamp = dateFormat.parse(parts[1].trim()) ?: Date()
                            val isPaid = if (parts.size > 2) parts[2].trim().toBoolean() else false
                            val isCustomAdd = if (parts.size > 3) parts[3].trim().toBoolean() else false

                            importedLogs.add(LogEntry(points, timestamp, isPaid, isCustomAdd))
                        } catch (e: Exception) {
                            // Skip invalid lines
                        }
                    }
                }
            }

            if (importedLogs.isNotEmpty()) {
                // Replace current logs with imported logs
                logEntries.clear()
                logEntries.addAll(importedLogs)

                saveLogs()
                logAdapter.notifyDataSetChanged()
                updatePointsUI()
                updateGraph()
                updateButtonStateForToday() // Refresh the timer with the new date

                Toast.makeText(this, "Successfully imported ${importedLogs.size} log entries from CSV!", Toast.LENGTH_LONG).show()
            } else {
                if (!lines.first().startsWith("lastActionDate:")) {
                    throw Exception("No valid log entries found in CSV")
                }
            }
        } catch (e: Exception) {
            throw Exception("Invalid CSV format: ${e.message}")
        }
    }
}
