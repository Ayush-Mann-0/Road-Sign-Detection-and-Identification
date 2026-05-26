package com.ayush_mann.roadsigndetection

data class BoundingBox(
    var x1: Float,
    var y1: Float,
    var x2: Float,
    var y2: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val cnf: Float,
    val cls: Int,
    val clsName: String,
    var trackingId: Int = -1,  // For object tracking
    var age: Int = 0,          // How many frames this detection has been alive
    var missCount: Int = 0     // How many consecutive frames this detection was missed
) {
    // Calculate center point
    fun centerX() = (x1 + x2) / 2f
    fun centerY() = (y1 + y2) / 2f

    // Calculate area
    fun area() = w * h

    // Calculate distance to another bounding box center
    fun distanceTo(other: BoundingBox): Float {
        val dx = centerX() - other.centerX()
        val dy = centerY() - other.centerY()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // Check if this box overlaps significantly with another
    fun overlapsWith(other: BoundingBox, threshold: Float = 0.3f): Float {
        val intersectionLeft = maxOf(x1, other.x1)
        val intersectionTop = maxOf(y1, other.y1)
        val intersectionRight = minOf(x2, other.x2)
        val intersectionBottom = minOf(y2, other.y2)

        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val unionArea = area() + other.area() - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    // Create a smoothed version by interpolating with another box
    fun smoothWith(other: BoundingBox, alpha: Float = 0.7f): BoundingBox {
        return copy(
            x1 = x1 * alpha + other.x1 * (1 - alpha),
            y1 = y1 * alpha + other.y1 * (1 - alpha),
            x2 = x2 * alpha + other.x2 * (1 - alpha),
            y2 = y2 * alpha + other.y2 * (1 - alpha),
            cx = cx * alpha + other.cx * (1 - alpha),
            cy = cy * alpha + other.cy * (1 - alpha),
            w = w * alpha + other.w * (1 - alpha),
            h = h * alpha + other.h * (1 - alpha),
            cnf = maxOf(cnf, other.cnf), // Keep higher confidence
            trackingId = trackingId,
            age = age + 1,
            missCount = 0
        )
    }
}