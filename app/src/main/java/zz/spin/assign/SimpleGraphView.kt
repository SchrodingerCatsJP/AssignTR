package zz.spin.assign

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SimpleGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val upPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Bright green for gains
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722") // Bright orange-red for losses
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val neutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90CAF9") // Light blue for neutral
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A237E") // Dark blue stroke
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EAF6") // Light blue-grey text
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF") // White for values
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#303F9F") // Dark blue grid lines
        strokeWidth = 1f
        style = Paint.Style.STROKE
        alpha = 100 // Semi-transparent
    }

    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5") // Indigo zero line
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 150
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT // Transparent background
        style = Paint.Style.FILL
    }

    private var dataPoints = listOf<Float>()
    private var dateLabels = listOf<String>()
    private val numberFormatter = NumberFormat.getInstance(Locale("id", "ID"))

    fun setData(points: List<Float>, dates: List<String>) {
        dataPoints = points
        dateLabels = dates
        invalidate() // Trigger redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

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

        val padding = 80f
        val bottomPadding = 100f // Extra space for date labels
        val topPadding = 60f // Extra space for value labels
        val graphWidth = width - 2 * padding
        val graphHeight = height - bottomPadding - topPadding

        // Calculate smart scaling
        val minValue = dataPoints.minOrNull() ?: 0f
        val maxValue = dataPoints.maxOrNull() ?: 1f

        // Create a better range for visualization
        val range = maxValue - minValue
        val buffer = if (range > 0) range * 0.1f else max(abs(maxValue), abs(minValue)) * 0.1f

        val adjustedMinValue = if (minValue >= 0 && maxValue >= 0) {
            // All positive values - start from 0 or slightly below min
            min(0f, minValue - buffer)
        } else {
            minValue - buffer
        }

        val adjustedMaxValue = maxValue + buffer
        val adjustedRange = adjustedMaxValue - adjustedMinValue

        // Ensure we have a minimum range for very small values
        val finalRange = if (adjustedRange < 1000f) 1000f else adjustedRange
        val finalMinValue = if (adjustedRange < 1000f) adjustedMinValue - (1000f - adjustedRange) / 2 else adjustedMinValue
        val finalMaxValue = finalMinValue + finalRange

        // Draw grid lines with smart scaling
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = topPadding + (graphHeight * i / gridLines)
            val value = finalMaxValue - (finalRange * i / gridLines)

            // Draw grid line
            canvas.drawLine(padding, y, width - padding, y, gridPaint)

            // Draw value label on the left
            val valueText = formatValue(value)
            val valuePaint = Paint(valueTextPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(valueText, padding - 10f, y + 8f, valuePaint)
        }

        // Draw zero line if zero is within our range
        if (finalMinValue <= 0 && finalMaxValue >= 0) {
            val zeroY = topPadding + graphHeight - (graphHeight * (0 - finalMinValue) / finalRange)
            canvas.drawLine(padding, zeroY, width - padding, zeroY, zeroLinePaint)
        }

        // Calculate positions for all points
        val positions = mutableListOf<Pair<Float, Float>>()
        for (i in dataPoints.indices) {
            val x = padding + (graphWidth * i / max(dataPoints.size - 1, 1))
            val normalizedValue = (dataPoints[i] - finalMinValue) / finalRange
            val y = topPadding + graphHeight - (graphHeight * normalizedValue)
            positions.add(Pair(x, y))
        }

        // Draw connecting lines between points
        if (positions.size > 1) {
            for (i in 1 until positions.size) {
                val (x1, y1) = positions[i - 1]
                val (x2, y2) = positions[i]

                val currentPaint = when {
                    dataPoints[i] > dataPoints[i - 1] -> upPaint
                    dataPoints[i] < dataPoints[i - 1] -> downPaint
                    else -> neutralPaint
                }

                canvas.drawLine(x1, y1, x2, y2, currentPaint)
            }
        }

        // Draw dots and labels
        for (i in positions.indices) {
            val (x, y) = positions[i]

            // Determine dot color with gradient effect
            val dotColor = when {
                dataPoints[i] > 10000 -> Color.parseColor("#00E676") // Bright green for high values
                dataPoints[i] > 0 -> Color.parseColor("#4FC3F7") // Light blue for positive
                dataPoints[i] < 0 -> Color.parseColor("#FF5722") // Orange-red for negative
                else -> Color.parseColor("#90CAF9") // Light blue for zero
            }

            // Create gradient effect for dots
            val gradient = LinearGradient(
                x, y - 15f, x, y + 15f,
                dotColor, Color.parseColor("#1A237E"),
                Shader.TileMode.CLAMP
            )
            dotPaint.shader = gradient

            // Draw outer glow effect for dots
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dotColor
                style = Paint.Style.FILL
                alpha = 50
            }
            canvas.drawCircle(x, y, 18f, glowPaint)

            // Draw main dot with gradient
            canvas.drawCircle(x, y, 14f, dotPaint)

            // Reset shader for stroke
            dotPaint.shader = null
            dotPaint.color = dotColor
            canvas.drawCircle(x, y, 14f, dotStrokePaint)

            // Draw date label
            if (i < dateLabels.size) {
                canvas.drawText(
                    dateLabels[i],
                    x,
                    height - 20f,
                    textPaint
                )
            }

            // Draw value label above the point with modern styling
            if (abs(dataPoints[i]) >= 1000) { // Only show labels for values >= 1000
                val valueText = formatValue(dataPoints[i])

                // Position the label above or below the dot based on available space
                val labelY = if (y > topPadding + 50f) y - 30f else y + 50f

                // Create a modern rounded background for the value text
                val textBounds = android.graphics.Rect()
                valueTextPaint.getTextBounds(valueText, 0, valueText.length, textBounds)

                val backgroundRect = android.graphics.RectF(
                    x - textBounds.width() / 2f - 12f,
                    labelY - textBounds.height() - 8f,
                    x + textBounds.width() / 2f + 12f,
                    labelY + 8f
                )

                // Create gradient background for value labels
                val labelGradient = LinearGradient(
                    backgroundRect.left, backgroundRect.top,
                    backgroundRect.right, backgroundRect.bottom,
                    Color.parseColor("#3F51B5"), Color.parseColor("#1A237E"),
                    Shader.TileMode.CLAMP
                )

                val modernBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = labelGradient
                    style = Paint.Style.FILL
                    alpha = 200
                }

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#5C6BC0")
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }

                canvas.drawRoundRect(backgroundRect, 12f, 12f, modernBackgroundPaint)
                canvas.drawRoundRect(backgroundRect, 12f, 12f, borderPaint)
                canvas.drawText(valueText, x, labelY, valueTextPaint)
            }
        }

        // Draw modern trend indicator with icon-like styling
        if (dataPoints.size >= 2) {
            val firstValue = dataPoints.first()
            val lastValue = dataPoints.last()
            val trend = lastValue - firstValue

            val (trendText, trendIcon) = when {
                trend > 1000 -> Pair("Points", "▲")
                trend < -1000 -> Pair("Points", "▼")
                else -> Pair("Stable", "●")
            }

            val trendColor = when {
                trend > 1000 -> Color.parseColor("#00E676")
                trend < -1000 -> Color.parseColor("#FF5722")
                else -> Color.parseColor("#90CAF9")
            }

            // Draw trend background
            val trendBgRect = android.graphics.RectF(padding, 15f, padding + 200f, 55f)
            val trendBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A237E")
                style = Paint.Style.FILL
                alpha = 150
            }
            canvas.drawRoundRect(trendBgRect, 15f, 15f, trendBgPaint)

            // Draw trend icon
            val iconPaint = Paint(textPaint).apply {
                color = trendColor
                textSize = 24f
                textAlign = Paint.Align.LEFT
                isFakeBoldText = true
            }
            canvas.drawText(trendIcon, padding + 15f, 40f, iconPaint)

            // Draw trend text
            val trendPaint = Paint(textPaint).apply {
                color = Color.parseColor("#E8EAF6")
                textSize = 20f
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(trendText, padding + 45f, 40f, trendPaint)
        }
    }

    private fun formatValue(value: Float): String {
        return when {
            abs(value) >= 1000000 -> "${(value / 1000000).toInt()}M"
            abs(value) >= 1000 -> "${(value / 1000).toInt()}K"
            abs(value) >= 1 -> value.toInt().toString()
            value == 0f -> "0"
            else -> String.format("%.1f", value)
        }
    }
}