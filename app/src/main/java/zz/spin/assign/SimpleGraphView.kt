package zz.spin.assign

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

class SimpleGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val upPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private var dataPoints = listOf<Float>()
    private var dateLabels = listOf<String>()
    private val path = Path()

    fun setData(points: List<Float>, dates: List<String>) {
        dataPoints = points
        dateLabels = dates
        invalidate() // Trigger redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) {
            // Draw "No data" message
            canvas.drawText(
                "No data available",
                width / 2f,
                height / 2f,
                textPaint
            )
            return
        }

        val padding = 60f
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding

        val maxValue = max(dataPoints.maxOrNull() ?: 0f, 1f)

        // Draw grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = padding + (graphHeight * i / gridLines)
            canvas.drawLine(padding, y, width - padding, y, gridPaint)
        }

        // Draw data line and dots
        if (dataPoints.size > 1) {
            for (i in 1 until dataPoints.size) {
                val x1 = padding + (graphWidth * (i - 1) / (dataPoints.size - 1))
                val y1 = padding + graphHeight - (graphHeight * dataPoints[i - 1] / maxValue)

                val x2 = padding + (graphWidth * i / (dataPoints.size - 1))
                val y2 = padding + graphHeight - (graphHeight * dataPoints[i] / maxValue)

                val currentPaint = if (dataPoints[i] >= dataPoints[i - 1]) upPaint else downPaint
                canvas.drawLine(x1, y1, x2, y2, currentPaint)

                // Set dot color and draw dot for the current point
                dotPaint.color = currentPaint.color
                canvas.drawCircle(x2, y2, 8f, dotPaint)
            }
        }

        // Draw the first dot
        if (dataPoints.isNotEmpty()) {
            val firstX = padding
            val firstY = padding + graphHeight - (graphHeight * dataPoints[0] / maxValue)
            dotPaint.color = if (dataPoints.size > 1 && dataPoints[1] >= dataPoints[0]) Color.GREEN else Color.RED
            canvas.drawCircle(firstX, firstY, 8f, dotPaint)
        }


        // Draw date and value labels
        for (i in dataPoints.indices) {
            val x = padding + (graphWidth * i / max(dataPoints.size - 1, 1))
            val y = padding + graphHeight - (graphHeight * dataPoints[i] / maxValue)

            // Draw date label
            if (i < dateLabels.size) {
                canvas.drawText(
                    dateLabels[i],
                    x,
                    height - 20f,
                    textPaint
                )
            }

            // Draw value label
            if (dataPoints[i] > 0) {
                canvas.drawText(
                    "${dataPoints[i].toInt()}",
                    x,
                    y - 20f,
                    textPaint
                )
            }
        }
    }
}