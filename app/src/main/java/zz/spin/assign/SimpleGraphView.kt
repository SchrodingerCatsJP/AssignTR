package zz.spin.assign

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class SimpleGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2") // Blue color
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
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

        // Draw data line
        if (dataPoints.size > 1) {
            path.reset()

            for (i in dataPoints.indices) {
                val x = padding + (graphWidth * i / (dataPoints.size - 1))
                val y = padding + graphHeight - (graphHeight * dataPoints[i] / maxValue)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            canvas.drawPath(path, linePaint)
        }

        // Draw data points and labels
        for (i in dataPoints.indices) {
            val x = padding + (graphWidth * i / max(dataPoints.size - 1, 1))
            val y = padding + graphHeight - (graphHeight * dataPoints[i] / maxValue)

            // Draw dot
            canvas.drawCircle(x, y, 8f, dotPaint)

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