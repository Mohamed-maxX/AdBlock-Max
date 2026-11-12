package com.max.adblock

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class CyberpunkChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bg_card)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.card_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_cyan)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_cyan)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_cyan)
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // 7 days data points
    private var dataPoints = floatArrayOf(85f, 120f, 95f, 160f, 140f, 190f, 142f)
    private val dayLabels = arrayOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "اليوم")

    fun setData(newData: FloatArray) {
        dataPoints = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = 24f

        // Draw card background & border
        val rect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val paddingLeft = 40f
        val paddingRight = 40f
        val paddingTop = 50f
        val paddingBottom = 60f

        val plotWidth = w - paddingLeft - paddingRight
        val plotHeight = h - paddingTop - paddingBottom
        val maxVal = (dataPoints.maxOrNull() ?: 200f).coerceAtLeast(100f)

        val stepX = plotWidth / (dataPoints.size - 1)
        val linePath = Path()

        // 1. Draw glowing bars
        val barWidth = 24f
        for (i in dataPoints.indices) {
            val cx = paddingLeft + i * stepX
            val ratio = dataPoints[i] / maxVal
            val barHeight = (plotHeight * ratio).coerceAtLeast(20f)
            val topY = (paddingTop + plotHeight) - barHeight
            val bottomY = paddingTop + plotHeight

            // Gradient for bar
            val shader = LinearGradient(
                cx, topY, cx, bottomY,
                ContextCompat.getColor(context, R.color.neon_cyan),
                ContextCompat.getColor(context, R.color.neon_blue),
                Shader.TileMode.CLAMP
            )
            barPaint.shader = shader

            val barRect = RectF(cx - barWidth / 2, topY, cx + barWidth / 2, bottomY)
            canvas.drawRoundRect(barRect, 8f, 8f, barPaint)

            // Connect line points
            if (i == 0) {
                linePath.moveTo(cx, topY)
            } else {
                linePath.lineTo(cx, topY)
            }

            // Draw Value on top of bar
            canvas.drawText("${dataPoints[i].toInt()}", cx, topY - 12f, valuePaint)

            // Draw Day label below bar
            canvas.drawText(dayLabels.getOrElse(i) { "" }, cx, bottomY + 36f, textPaint)
        }

        // 2. Draw trend line & dots
        canvas.drawPath(linePath, linePaint)
        for (i in dataPoints.indices) {
            val cx = paddingLeft + i * stepX
            val ratio = dataPoints[i] / maxVal
            val barHeight = (plotHeight * ratio).coerceAtLeast(20f)
            val topY = (paddingTop + plotHeight) - barHeight

            canvas.drawCircle(cx, topY, 6f, dotPaint)
        }
    }
}
