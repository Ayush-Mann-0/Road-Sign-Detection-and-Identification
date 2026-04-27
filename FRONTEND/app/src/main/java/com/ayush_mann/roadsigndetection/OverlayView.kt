package com.ayush_mann.roadsigndetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import androidx.core.graphics.toColorInt

class OverlayView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) : View(context, attrs) {
    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var trackingIdPaint = Paint()
    private var bounds = Rect()

    // Properties to handle aspect ratio and coordinate transformation
    private var imageWidth = 0f
    private var imageHeight = 0f
    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Enhanced color palette optimized for road sign visibility
    private val classColors = listOf(
        "#FF4444".toColorInt(), // Bright Red - High visibility
        "#44AA44".toColorInt(), // Bright Green - High contrast
        "#4444FF".toColorInt(), // Bright Blue - Clear visibility
        "#FFAA00".toColorInt(), // Bright Orange - High attention
        "#AA44FF".toColorInt(), // Bright Purple - Good contrast
        "#44FFFF".toColorInt(), // Cyan - High visibility
        "#FF44AA".toColorInt(), // Pink - Good contrast
        "#AAFF44".toColorInt(), // Lime - High visibility
        "#FF8844".toColorInt(), // Orange-Red - Warm visibility
        "#8844FF".toColorInt(), // Purple-Blue - Cool visibility
        "#44FFAA".toColorInt(), // Turquoise - High contrast
        "#FFAA44".toColorInt(), // Gold - Attention grabbing
        "#AA44AA".toColorInt(), // Magenta - High visibility
        "#44AAFF".toColorInt(), // Sky Blue - Clear visibility
        "#AAFFAA".toColorInt()  // Light Green - Soft visibility
    )

    // Track colors for consistent tracking ID visualization
    private val trackingColors = mutableMapOf<Int, Int>()
    private var colorIndex = 0

    init {
        initPaints()
    }

    fun clear() {
        results = emptyList()
        invalidate()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 36f
        textBackgroundPaint.alpha = 200 // More opaque for better readability

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 36f
        textPaint.isAntiAlias = true
        textPaint.isFakeBoldText = true // Make text bolder

        trackingIdPaint.color = Color.WHITE
        trackingIdPaint.style = Paint.Style.FILL
        trackingIdPaint.textSize = 24f
        trackingIdPaint.isAntiAlias = true
        trackingIdPaint.isFakeBoldText = true

        boxPaint.strokeWidth = 5F
        boxPaint.style = Paint.Style.STROKE
        boxPaint.isAntiAlias = true
    }

    fun setImageDimensions(imgWidth: Float, imgHeight: Float) {
        imageWidth = imgWidth
        imageHeight = imgHeight
        calculateScaling()
    }

    private fun calculateScaling() {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return

        // Calculate scale to fit image within view while maintaining aspect ratio
        val scaleWidth = width.toFloat() / imageWidth
        val scaleHeight = height.toFloat() / imageHeight
        val scale = minOf(scaleWidth, scaleHeight)

        scaleX = scale
        scaleY = scale

        // Calculate offsets to center the image
        val scaledImageWidth = imageWidth * scaleX
        val scaledImageHeight = imageHeight * scaleY

        offsetX = (width - scaledImageWidth) / 2f
        offsetY = (height - scaledImageHeight) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateScaling()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (results.isEmpty()) return

        results.forEachIndexed { index, boundingBox ->
            // Get consistent color for this object class and tracking ID
            val color = getObjectColor(boundingBox)
            boxPaint.color = color

            if (imageWidth <= 0 || imageHeight <= 0) {
                // Fallback to simple scaling if dimensions not set
                drawBoundingBox(canvas, boundingBox, index,
                    width.toFloat(), height.toFloat(), 0f, 0f, 1f, 1f)
            } else {
                // Use proper aspect ratio scaling
                drawBoundingBox(canvas, boundingBox, index,
                    imageWidth, imageHeight, offsetX, offsetY, scaleX, scaleY)
            }
        }
    }

    private fun getObjectColor(boundingBox: BoundingBox): Int {
        // If object has a tracking ID, use consistent color for that ID
        if (boundingBox.trackingId >= 0) {
            return trackingColors.getOrPut(boundingBox.trackingId) {
                val color = classColors[colorIndex % classColors.size]
                colorIndex++
                color
            }
        } else {
            // Fall back to class-based color
            return classColors[boundingBox.cls % classColors.size]
        }
    }

    private fun drawBoundingBox(
        canvas: Canvas,
        boundingBox: BoundingBox,
        index: Int,
        imgWidth: Float,
        imgHeight: Float,
        offsetX: Float,
        offsetY: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        // Transform normalized coordinates (0-1) to actual display coordinates
        // The boundingBox coordinates are normalized to the camera frame
        val left = (boundingBox.x1 * imgWidth) + offsetX
        val top = (boundingBox.y1 * imgHeight) + offsetY
        val right = (boundingBox.x2 * imgWidth) + offsetX
        val bottom = (boundingBox.y2 * imgHeight) + offsetY

        // Ensure coordinates are within view bounds
        val clampedLeft = left.coerceIn(0f, width.toFloat())
        val clampedTop = top.coerceIn(0f, height.toFloat())
        val clampedRight = right.coerceIn(0f, width.toFloat())
        val clampedBottom = bottom.coerceIn(0f, height.toFloat())

        // Draw bounding box with slightly thicker line for tracked objects
        if (boundingBox.trackingId >= 0) {
            boxPaint.strokeWidth = 6f
        } else {
            boxPaint.strokeWidth = 4f
        }

        canvas.drawRect(clampedLeft, clampedTop, clampedRight, clampedBottom, boxPaint)

        // Create label with confidence and tracking info
        val confidence = (boundingBox.cnf * 100).toInt()
        val classInitial = boundingBox.clsName.take(4).uppercase() // First 4 letters for better readability

        val mainLabel = "$classInitial $confidence%"
        val trackingLabel = if (boundingBox.trackingId >= 0) "ID:${boundingBox.trackingId}" else ""

        // Measure main label text
        textPaint.getTextBounds(mainLabel, 0, mainLabel.length, bounds)
        val mainTextWidth = bounds.width().toFloat()
        val mainTextHeight = bounds.height().toFloat()

        // Position main label above the box, or inside if no space above
        val labelTop = if (clampedTop - mainTextHeight - LABEL_PADDING * 2 > 0) {
            clampedTop - mainTextHeight - LABEL_PADDING
        } else {
            clampedTop + LABEL_PADDING + mainTextHeight
        }

        val labelLeft = clampedLeft.coerceAtMost(width - mainTextWidth - LABEL_PADDING)

        // Draw semi-transparent background for main label
        val bgLeft = labelLeft - LABEL_PADDING / 2
        val bgTop = labelTop - mainTextHeight - LABEL_PADDING / 2
        val bgRight = labelLeft + mainTextWidth + LABEL_PADDING / 2
        val bgBottom = labelTop + LABEL_PADDING / 2

        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBackgroundPaint)

        // Draw main label text
        canvas.drawText(mainLabel, labelLeft, labelTop, textPaint)

        // Draw tracking ID if available
        if (trackingLabel.isNotEmpty()) {
            trackingIdPaint.getTextBounds(trackingLabel, 0, trackingLabel.length, bounds)
            val trackingTextWidth = bounds.width().toFloat()
            val trackingTextHeight = bounds.height().toFloat()

            val trackingTop = bgBottom + trackingTextHeight + LABEL_PADDING / 2
            val trackingLeft = labelLeft

            // Background for tracking ID
            val trackingBgLeft = trackingLeft - LABEL_PADDING / 4
            val trackingBgTop = trackingTop - trackingTextHeight - LABEL_PADDING / 4
            val trackingBgRight = trackingLeft + trackingTextWidth + LABEL_PADDING / 4
            val trackingBgBottom = trackingTop + LABEL_PADDING / 4

            // Use object color with transparency for tracking ID background
            val trackingBgPaint = Paint().apply {
                color = boxPaint.color
                alpha = 180
                style = Paint.Style.FILL
            }

            canvas.drawRect(trackingBgLeft, trackingBgTop, trackingBgRight, trackingBgBottom, trackingBgPaint)
            canvas.drawText(trackingLabel, trackingLeft, trackingTop, trackingIdPaint)
        }

        // Draw colored indicator circle with tracking enhancement
        val circleRadius = if (boundingBox.trackingId >= 0) 10f else 8f
        val circleX = clampedRight - circleRadius - 6f
        val circleY = clampedTop + circleRadius + 6f

        val circlePaint = Paint().apply {
            color = boxPaint.color
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        canvas.drawCircle(circleX, circleY, circleRadius, circlePaint)

        // Draw white border around circle
        val circleBorderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = if (boundingBox.trackingId >= 0) 3f else 2f
            isAntiAlias = true
        }
        canvas.drawCircle(circleX, circleY, circleRadius, circleBorderPaint)

        // Draw tracking indicator dot in center for tracked objects
        if (boundingBox.trackingId >= 0) {
            val centerDotPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(circleX, circleY, 3f, centerDotPaint)
        }
    }

    fun setImageOffset(x: Float, y: Float, overrideScale: Boolean = false, customScale: Float = 1f) {
        offsetX = x
        offsetY = y
        if (overrideScale) {
            scaleX = customScale
            scaleY = customScale
        }
        invalidate()
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        cleanupTrackingColors() // Prevent color memory leaks
        invalidate()
    }

    /**
     * Clean up tracking colors for objects that are no longer present
     * Call this periodically to prevent memory leaks
     */
    fun cleanupTrackingColors() {
        val activeTrackingIds = results.mapNotNull {
            if (it.trackingId >= 0) it.trackingId else null
        }.toSet()

        trackingColors.keys.retainAll(activeTrackingIds)
    }

    companion object {
        private const val LABEL_PADDING = 10f
    }
}