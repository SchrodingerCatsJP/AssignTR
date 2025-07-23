package zz.spin.assign

import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import zz.spin.assign.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logEntries = mutableListOf<LogEntry>()
    private lateinit var logAdapter: LogAdapter

    private val numberFormatter = NumberFormat.getInstance(Locale("id", "ID"))

    // Constants for SharedPreferences
    private val PREFS_NAME = "AssignmentTrackerPrefs"
    private val LOGS_KEY = "logEntries"
    private val LAST_ACTION_DATE_KEY = "lastActionDate"

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                ReminderBroadcastReceiver.scheduleDailyReminder(this)
                Toast.makeText(this, "Reminder notifications enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadLogs()
        setupRecyclerView()
        setupClickListeners()
        updatePointsUI()
        updateButtonStateForToday()

        askForNotificationPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                ReminderBroadcastReceiver.scheduleDailyReminder(this)
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            ReminderBroadcastReceiver.scheduleDailyReminder(this)
        }
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(logEntries)
        binding.recyclerViewLogs.adapter = logAdapter
        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonDone.setOnTouchListener(createHoldListener(20000L, "DONE Logged!"))
        binding.buttonSkipped.setOnTouchListener(createHoldListener(0L, "SKIPPED Logged!"))

        binding.fabAddCustom.setOnClickListener {
            showCustomPointsDialog(true)
        }

        binding.fabSubtractCustom.setOnClickListener {
            showCustomPointsDialog(false)
        }
    }

    private fun createHoldListener(points: Long, message: String): View.OnTouchListener {
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
                        addLog(points)
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        drawable.level = 0
                        // The daily lock is now triggered ONLY by the DONE and SKIPPED buttons
                        saveLastActionDate()
                        updateButtonStateForToday()
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
    }

    private fun setButtonsEnabled(isEnabled: Boolean) {
        binding.buttonDone.isEnabled = isEnabled
        binding.buttonSkipped.isEnabled = isEnabled
        binding.buttonDone.alpha = if (isEnabled) 1.0f else 0.5f
        binding.buttonSkipped.alpha = if (isEnabled) 1.0f else 0.5f
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        if (isEnabled) {
            (binding.buttonDone as TextView).text = "DONE (+20,000)"
            (binding.buttonSkipped as TextView).text = "SKIPPED"
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
                        (binding.buttonDone as TextView).text = countdownText
                        (binding.buttonSkipped as TextView).text = countdownText
                        countdownHandler.postDelayed(this, 1000)
                    } else {
                        updateButtonStateForToday()
                    }
                }
            }
            countdownHandler.post(countdownRunnable!!)
        }
    }

    private fun saveLastActionDate() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putLong(LAST_ACTION_DATE_KEY, System.currentTimeMillis())
        prefs.apply()
    }

    private fun showCustomPointsDialog(isAdding: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_points, null)
        val editText = dialogView.findViewById<EditText>(R.id.editTextDialogCustomPoints)
        val dialogTitle = if (isAdding) "Add Custom Points" else "Use Custom Points"
        val buttonText = if (isAdding) "Add" else "Use"

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
                        addLog(customPoints)
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

    private fun addLog(points: Long) {
        logEntries.add(0, LogEntry(points = points, timestamp = Date()))
        logAdapter.notifyItemInserted(0)
        binding.recyclerViewLogs.scrollToPosition(0)
        updatePointsUI()
        saveLogs()
        // The daily lock logic has been removed from this general function
    }

    private fun updatePointsUI() {
        // Net Total Points (Earned - Used)
        val totalPoints = logEntries.sumOf { it.points }
        binding.textViewTotalPointsValue.text = numberFormatter.format(totalPoints)

        // Gross Total Points (Earned only)
        val grossTotal = logEntries.filter { it.points > 0 }.sumOf { it.points }
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

        // Gross Monthly Points
        val grossMonthly = monthlyLogs.filter { it.points > 0 }.sumOf { it.points }
        binding.textViewGrossMonthlyValue.text = "(Earned: ${numberFormatter.format(grossMonthly)})"

        // NEW: Used Monthly Points
        val usedMonthly = monthlyLogs.filter { it.points < 0 }.sumOf { -it.points }
        binding.textViewUsedMonthlyValue.text = "(Used: ${numberFormatter.format(usedMonthly)})"
    }

    private fun saveLogs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val gson = Gson()
        val json = gson.toJson(logEntries)
        prefs.putString(LOGS_KEY, json)
        prefs.apply()
    }

    private fun loadLogs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(LOGS_KEY, null)
        val type = object : TypeToken<MutableList<LogEntry>>() {}.type
        if (json != null) {
            val loadedLogs: MutableList<LogEntry> = gson.fromJson(json, type)
            logEntries.clear()
            logEntries.addAll(loadedLogs)
        }
    }
}
