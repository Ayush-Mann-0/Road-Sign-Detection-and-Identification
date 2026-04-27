package com.ayush_mann.roadsigndetection

import kotlin.math.*

/**
 * Advanced object tracking system optimized for road sign detection in moving vehicles.
 * Features:
 * - Aggressive smoothing for car-mounted usage
 * - Smart overlapping detection to prevent visual clutter
 * - Motion prediction for better tracking during camera movement
 * - Adaptive confidence thresholds based on tracking stability
 * - Visual stability prioritization over detection accuracy
 */
class ObjectTracker(
    // Distance parameters optimized for multiple road sign detection
    private val maxDistance: Float = 100f,  // Reduced for better association accuracy
    private val maxMissedFrames: Int = 10,    // Reduced for faster track cleanup
    private val smoothingFactor: Float = 0.3f, // Reduced for less smoothing
    private val confidenceDecay: Float = 0.9f, // Faster decay for stale tracks
    private val minConfidence: Float = 0.2f,   // Higher threshold for quality
    private val overlapThreshold: Float = 0.3f, // Much lower to allow multiple detections
    
    // Car-specific parameters
    private val cameraMotionTolerance: Float = 200f, // Tolerance for camera movement
    private val velocitySmoothing: Float = 0.7f,     // Heavy velocity smoothing
    private val accelerationSmoothing: Float = 0.8f, // Heavy acceleration smoothing
    private val positionSmoothingBoost: Float = 0.3f // Extra smoothing for fast movement
) {

    companion object {
        private const val EXPECTED_FRAME_TIME_MS = 100f
        private const val MIN_TRACK_STABILITY = 2      // Reduced for faster track visibility
        private const val MAX_SIMULTANEOUS_TRACKS = 15 // Increased for more signs
    }

    private val activeTracks = mutableListOf<TrackedObject>()
    private var nextTrackId = 0
    private var lastFrameTimeMs: Long = 0
    private var cameraVelocityX = 0f
    private var cameraVelocityY = 0f

    /** Update tracking with new detections - optimized for moving car scenarios */
    fun update(
        newDetections: List<BoundingBox>,
        dtMs: Long = EXPECTED_FRAME_TIME_MS.toLong()
    ): List<BoundingBox> {
        val currentTimeMs = System.currentTimeMillis()
        val actualDtMs = if (lastFrameTimeMs > 0) {
            (currentTimeMs - lastFrameTimeMs).coerceAtLeast(16L) // Minimum 16ms
        } else {
            EXPECTED_FRAME_TIME_MS.toLong()
        }
        lastFrameTimeMs = currentTimeMs
        
        // Update camera motion estimation
        updateCameraMotion(newDetections, actualDtMs)
        
        // Less aggressive filtering to allow multiple sign boards
        val filteredDetections = removeOverlappingDetections(newDetections)
        val adjustedMaxDistance = maxDistance * (actualDtMs.toFloat() / EXPECTED_FRAME_TIME_MS)
        
        // Enhanced association with camera motion compensation
        val associations = associateDetectionsWithMotionCompensation(filteredDetections, adjustedMaxDistance)
        updateExistingTracksEnhanced(associations, actualDtMs)
        createNewTracks(associations.unassociatedDetections)
        removeDeadTracks()
        cleanupDuplicateTracks()
        
        return getStableDetections()
    }

    fun clear() {
        activeTracks.clear()
        nextTrackId = 0
    }

    fun getActiveTrackCount(): Int = activeTracks.size

    private fun updateCameraMotion(detections: List<BoundingBox>, dtMs: Long) {
        if (detections.isEmpty() || activeTracks.isEmpty()) return
        
        // Estimate camera motion from stable tracks
        val stableTracks = activeTracks.filter { it.isStable() }
        if (stableTracks.isNotEmpty()) {
            val avgVelocityX = stableTracks.map { it.velocityX }.average().toFloat()
            val avgVelocityY = stableTracks.map { it.velocityY }.average().toFloat()
            
            // Heavy smoothing for camera motion
            cameraVelocityX = cameraVelocityX * 0.9f + avgVelocityX * 0.1f
            cameraVelocityY = cameraVelocityY * 0.9f + avgVelocityY * 0.1f
        }
    }
    
    private fun associateDetectionsWithMotionCompensation(
        detections: List<BoundingBox>,
        maxDist: Float
    ): AssociationResult {
        val associations = mutableMapOf<TrackedObject, BoundingBox>()
        val unassociatedDetections = detections.toMutableList()
        val unassociatedTracks = activeTracks.toMutableList()
        
        // Sort by confidence for better association
        unassociatedDetections.sortByDescending { it.cnf }
        unassociatedTracks.sortByDescending { it.getStabilityScore() }
        
        while (unassociatedTracks.isNotEmpty() && unassociatedDetections.isNotEmpty()) {
            var bestTrack: TrackedObject? = null
            var bestDetection: BoundingBox? = null
            var bestDistance = Float.MAX_VALUE
            var bestScore = 0f

            for (track in unassociatedTracks) {
                for (detection in unassociatedDetections) {
                    if (track.lastDetection.cls != detection.cls) continue
                    
                    val distance = calculateEnhancedAssociationDistance(track, detection, maxDist)
                    val score = calculateAssociationScore(track, detection, distance)
                    
                    if (score > bestScore && distance < maxDist) {
                        bestScore = score
                        bestDistance = distance
                        bestTrack = track
                        bestDetection = detection
                    }
                }
            }
            
            if (bestTrack != null && bestDetection != null && bestScore > 0.15f) {
                associations[bestTrack] = bestDetection
                unassociatedTracks.remove(bestTrack)
                unassociatedDetections.remove(bestDetection)
            } else {
                break
            }
        }
        
        return AssociationResult(associations, unassociatedDetections, unassociatedTracks)
    }

    private fun calculateEnhancedAssociationDistance(
        track: TrackedObject, 
        detection: BoundingBox,
        maxDist: Float
    ): Float {
        val predicted = track.predictNextWithCameraMotion(cameraVelocityX, cameraVelocityY)
        
        // Enhanced position distance with camera motion compensation
        val positionDistance = sqrt(
            (predicted.centerX() - detection.centerX()).pow(2) +
            (predicted.centerY() - detection.centerY()).pow(2)
        )
        
        // Size similarity with more tolerance for perspective changes
        val sizeDistance = abs(predicted.area() - detection.area()) /
                max(predicted.area(), detection.area())
        
        // Enhanced IoU with camera motion tolerance
        val iou = predicted.overlapsWith(detection)
        val iouDistance = if (iou > 0) (1f - iou) * 20f else 100f
        
        // Confidence consistency
        val confidenceConsistency = 1f - abs(predicted.cnf - detection.cnf)
        
        // Track stability bonus
        val stabilityBonus = track.getStabilityScore() * 10f
        
        // Motion consistency with camera compensation
        val motionConsistency = calculateMotionConsistencyWithCamera(track, detection)
        
        // Combined distance with emphasis on stability and motion
        return positionDistance * 0.3f + 
               iouDistance * 0.2f + 
               sizeDistance * 10f + 
               (1f - confidenceConsistency) * 15f +
               motionConsistency * 0.2f -
               stabilityBonus
    }
    
    private fun calculateAssociationScore(
        track: TrackedObject, 
        detection: BoundingBox, 
        distance: Float
    ): Float {
        val stability = track.getStabilityScore()
        val confidence = detection.cnf
        val distanceScore = max(0f, 1f - distance / 100f)
        
        return stability * 0.4f + confidence * 0.4f + distanceScore * 0.2f
    }

    private fun calculateMotionConsistencyWithCamera(
        track: TrackedObject, 
        detection: BoundingBox
    ): Float {
        if (track.totalFrames < 3) return 0f
        
        // Expected motion including camera movement
        val expectedVelocityX = track.velocityX + cameraVelocityX
        val expectedVelocityY = track.velocityY + cameraVelocityY
        
        // Actual motion
        val actualVelocityX = detection.centerX() - track.lastDetection.centerX()
        val actualVelocityY = detection.centerY() - track.lastDetection.centerY()
        
        // Motion difference
        val velocityDiff = sqrt(
            (expectedVelocityX - actualVelocityX).pow(2) +
            (expectedVelocityY - actualVelocityY).pow(2)
        )
        
        return velocityDiff * 15f
    }

    private fun updateExistingTracksEnhanced(associations: AssociationResult, dtMs: Long) {
        for ((track, detection) in associations.associations) {
            track.updateEnhanced(detection, dtMs, cameraVelocityX, cameraVelocityY)
        }
        
        for (track in associations.unassociatedTracks) {
            track.missedFrameWithCameraMotion(cameraVelocityX, cameraVelocityY)
        }
    }

    private fun removeOverlappingDetections(
        detections: List<BoundingBox>
    ): List<BoundingBox> {
        if (detections.size <= 1) return detections
        
        val sortedDetections = detections.sortedByDescending { it.cnf }
        val filteredDetections = mutableListOf<BoundingBox>()
        
        for (detection in sortedDetections) {
            var shouldAdd = true
            
            // Check against existing detections - allow same-type nearby signs
            for (existing in filteredDetections) {
                if (detection.cls == existing.cls) {
                    val overlap = detection.overlapsWith(existing)
                    val centerDistance = detection.distanceTo(existing)
                    
                    // Only filter out exact duplicates or very close overlaps
                    // Allow nearby same-type signs to be detected separately
                    if (overlap > 0.8f || 
                        (overlap > 0.6f && centerDistance < 10f) ||
                        (overlap > 0.4f && centerDistance < 5f)) {
                        shouldAdd = false
                        break
                    }
                }
            }
            
            if (shouldAdd) {
                filteredDetections.add(detection)
            }
        }
        
        return filteredDetections
    }

    private fun createNewTracks(newDetections: List<BoundingBox>) {
        for (detection in newDetections) {
            // Create tracks for medium to high-confidence detections
            if (detection.cnf < 0.3f) continue
            
            // Check if this detection significantly overlaps with any existing tracks
            val overlapsWithExisting = activeTracks.any { track ->
                track.lastDetection.cls == detection.cls &&
                        track.lastDetection.overlapsWith(detection) > 0.7f
            }
            
            // Additional check for very nearby tracks of same class (but allow some proximity)
            val hasVeryNearbyTrack = activeTracks.any { track ->
                track.lastDetection.cls == detection.cls &&
                        track.lastDetection.distanceTo(detection) < 20f
            }
            
            if (!overlapsWithExisting && !hasVeryNearbyTrack) {
                // Allow more tracks for same-type signs
                if (activeTracks.size < MAX_SIMULTANEOUS_TRACKS) {
                    activeTracks.add(
                        TrackedObject(
                            id = nextTrackId++,
                            initialDetection = detection,
                            smoothingFactor = smoothingFactor,
                            velocitySmoothing = velocitySmoothing,
                            accelerationSmoothing = accelerationSmoothing
                        )
                    )
                }
            }
        }
    }

    private fun cleanupDuplicateTracks() {
        val toRemove = mutableListOf<TrackedObject>()
        
        for (i in activeTracks.indices) {
            if (toRemove.contains(activeTracks[i])) continue
            
            for (j in i + 1 until activeTracks.size) {
                if (toRemove.contains(activeTracks[j])) continue
                
                val track1 = activeTracks[i]
                val track2 = activeTracks[j]
                
                if (track1.lastDetection.cls == track2.lastDetection.cls) {
                    val overlap = track1.smoothedDetection.overlapsWith(track2.smoothedDetection)
                    val distanceBetweenCenters = track1.smoothedDetection.distanceTo(track2.smoothedDetection)
                    
                    // Less aggressive duplicate cleanup - only remove very close duplicates
                    if (overlap > 0.8f || 
                        (overlap > 0.6f && distanceBetweenCenters < 10f) ||
                        (overlap > 0.4f && distanceBetweenCenters < 5f)) {
                        val track1Score = track1.getStabilityScore() * track1.smoothedDetection.cnf
                        val track2Score = track2.getStabilityScore() * track2.smoothedDetection.cnf
                        
                        if (track1Score >= track2Score) {
                            toRemove.add(track2)
                        } else {
                            toRemove.add(track1)
                        }
                    }
                }
            }
        }
        
        activeTracks.removeAll(toRemove)
    }

    private fun removeDeadTracks() {
        activeTracks.removeAll { track ->
            track.missedFrames > maxMissedFrames ||
                    track.smoothedDetection.cnf < minConfidence
        }
    }

    private fun getStableDetections(): List<BoundingBox> {
        val stableDetections = activeTracks
            .filter { it.isStable() }  // Only show stable tracks
            .map { it.getCurrentDetection() }
            .sortedByDescending { it.cnf * it.age }  // Sort by confidence and stability
        
        // Group nearby same-type detections and expand bounding boxes
        return groupAndExpandNearbyDetections(stableDetections)
    }
    
    /**
     * Groups nearby same-type road signs and expands bounding boxes to cover all nearby signs
     * of the same type, unless there are obstacles (different type signs) in between.
     */
    private fun groupAndExpandNearbyDetections(detections: List<BoundingBox>): List<BoundingBox> {
        if (detections.size <= 1) return detections
        
        val groupedDetections = mutableListOf<BoundingBox>()
        val processed = mutableSetOf<Int>()
        
        for (i in detections.indices) {
            if (i in processed) continue
            
            val currentDetection = detections[i]
            val sameTypeGroup = mutableListOf<BoundingBox>()
            sameTypeGroup.add(currentDetection)
            processed.add(i)
            
            // Find nearby same-type detections
            for (j in i + 1 until detections.size) {
                if (j in processed) continue
                
                val otherDetection = detections[j]
                
                // Only group same type detections
                if (currentDetection.cls == otherDetection.cls) {
                    val distance = currentDetection.distanceTo(otherDetection)
                    val maxGroupingDistance = calculateMaxGroupingDistance(currentDetection, otherDetection)
                    
                    // Check if they should be grouped (nearby and no obstacles)
                    if (distance <= maxGroupingDistance && !hasObstaclesBetween(currentDetection, otherDetection, detections)) {
                        sameTypeGroup.add(otherDetection)
                        processed.add(j)
                    }
                }
            }
            
            // Create expanded bounding box for the group
            if (sameTypeGroup.size > 1) {
                val expandedBox = createExpandedBoundingBox(sameTypeGroup)
                groupedDetections.add(expandedBox)
            } else {
                groupedDetections.add(currentDetection)
            }
        }
        
        return groupedDetections
    }
    
    /**
     * Calculate maximum distance for grouping based on detection size and confidence
     */
    private fun calculateMaxGroupingDistance(box1: BoundingBox, box2: BoundingBox): Float {
        val avgSize = sqrt(box1.area() + box2.area()) / 2f
        val avgConfidence = (box1.cnf + box2.cnf) / 2f
        
        // Base distance proportional to size, modified by confidence
        val baseDistance = avgSize * 3f  // Allow grouping within 3x the average size
        val confidenceMultiplier = 0.5f + avgConfidence * 0.5f  // Higher confidence = larger grouping distance
        
        return baseDistance * confidenceMultiplier
    }
    
    /**
     * Check if there are obstacles (different type signs) between two detections
     */
    private fun hasObstaclesBetween(box1: BoundingBox, box2: BoundingBox, allDetections: List<BoundingBox>): Boolean {
        val lineDistance = box1.distanceTo(box2)
        if (lineDistance < 50f) return false  // Very close detections likely don't have obstacles
        
        // Check if any different type detection lies between them
        for (detection in allDetections) {
            if (detection.cls == box1.cls) continue  // Skip same type
            
            // Calculate if this detection lies roughly between the two boxes
            val distToBox1 = detection.distanceTo(box1)
            val distToBox2 = detection.distanceTo(box2)
            
            // If detection is roughly between the two boxes and close to the line connecting them
            if (distToBox1 < lineDistance && distToBox2 < lineDistance) {
                val lineCenterX = (box1.centerX() + box2.centerX()) / 2f
                val lineCenterY = (box1.centerY() + box2.centerY()) / 2f
                val detectionToLineCenter = sqrt(
                    (detection.centerX() - lineCenterX).pow(2) + 
                    (detection.centerY() - lineCenterY).pow(2)
                )
                
                // If obstacle is close to the line connecting the two boxes
                if (detectionToLineCenter < lineDistance * 0.3f) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Create an expanded bounding box that covers all detections in a group
     */
    private fun createExpandedBoundingBox(group: List<BoundingBox>): BoundingBox {
        // Find the bounding box that encompasses all detections
        val minX = group.minOf { it.x1 }
        val minY = group.minOf { it.y1 }
        val maxX = group.maxOf { it.x2 }
        val maxY = group.maxOf { it.y2 }
        
        // Add some padding (10% of the size)
        val width = maxX - minX
        val height = maxY - minY
        val paddingX = width * 0.1f
        val paddingY = height * 0.1f
        
        // Ensure box stays within image bounds
        val paddedMinX = max(0f, minX - paddingX)
        val paddedMinY = max(0f, minY - paddingY)
        val paddedMaxX = min(1f, maxX + paddingX)
        val paddedMaxY = min(1f, maxY + paddingY)
        
        // Calculate center and dimensions
        val centerX = (paddedMinX + paddedMaxX) / 2f
        val centerY = (paddedMinY + paddedMaxY) / 2f
        val boxWidth = paddedMaxX - paddedMinX
        val boxHeight = paddedMaxY - paddedMinY
        
        // Use the highest confidence and average age from the group
        val maxConfidence = group.maxOf { it.cnf }
        val avgAge = group.map { it.age }.average().toInt()
        
        return BoundingBox(
            x1 = paddedMinX, y1 = paddedMinY,
            x2 = paddedMaxX, y2 = paddedMaxY,
            cx = centerX, cy = centerY,
            w = boxWidth, h = boxHeight,
            cnf = maxConfidence,
            cls = group.first().cls,
            clsName = group.first().clsName,
            trackingId = group.first().trackingId,
            age = avgAge
        )
    }

    private data class AssociationResult(
        val associations: Map<TrackedObject, BoundingBox>,
        val unassociatedDetections: List<BoundingBox>,
        val unassociatedTracks: List<TrackedObject>
    )

    private class TrackedObject(
        val id: Int,
        initialDetection: BoundingBox,
        private val smoothingFactor: Float,
        private val velocitySmoothing: Float = 0.7f,
        private val accelerationSmoothing: Float = 0.8f
    ) {
        var lastDetection = initialDetection.copy(trackingId = id)
        var smoothedDetection = initialDetection.copy(trackingId = id)
        var missedFrames = 0
        var totalFrames = 1
        var consecutiveDetections = 1  // Track consecutive detections for stability
        
        // Motion state
        var velocityX = 0f
        var velocityY = 0f
        private var velocityW = 0f
        private var velocityH = 0f
        private var accelerationX = 0f
        private var accelerationY = 0f
        
        // Stability tracking
        private var confidenceHistory = mutableListOf<Float>()
        private var positionHistory = mutableListOf<Pair<Float, Float>>()
        private var lastUpdateTimeMs: Long = 0

        fun updateEnhanced(newDetection: BoundingBox, dtMs: Long, cameraVelX: Float, cameraVelY: Float) {
            val currentTimeMs = System.currentTimeMillis()
            val actualDtMs = if (lastUpdateTimeMs > 0) {
                (currentTimeMs - lastUpdateTimeMs).coerceAtLeast(16L)
            } else {
                100L
            }
            lastUpdateTimeMs = currentTimeMs
            
            // Calculate relative velocity (subtract camera motion)
            val newVelX = (newDetection.centerX() - lastDetection.centerX()) - cameraVelX * (actualDtMs / 1000f)
            val newVelY = (newDetection.centerY() - lastDetection.centerY()) - cameraVelY * (actualDtMs / 1000f)
            
            // Enhanced smoothing with configurable parameters
            accelerationX = (newVelX - velocityX) * (1f - accelerationSmoothing) + accelerationX * accelerationSmoothing
            accelerationY = (newVelY - velocityY) * (1f - accelerationSmoothing) + accelerationY * accelerationSmoothing
            
            velocityX = newVelX * (1f - velocitySmoothing) + velocityX * velocitySmoothing
            velocityY = newVelY * (1f - velocitySmoothing) + velocityY * velocitySmoothing
            
            velocityW = (newDetection.w - lastDetection.w) * 0.2f + velocityW * 0.8f
            velocityH = (newDetection.h - lastDetection.h) * 0.2f + velocityH * 0.8f
            
            // Enhanced smoothing with adaptive factors
            smoothedDetection = smoothDetectionEnhanced(newDetection, 0.3f)
            lastDetection = newDetection.copy(trackingId = id)
            
            missedFrames = 0
            totalFrames++
            consecutiveDetections++
            
            // Update stability tracking
            updateStabilityTracking(newDetection)
        }

        fun missedFrameWithCameraMotion(cameraVelX: Float, cameraVelY: Float) {
            missedFrames++
            consecutiveDetections = 0
            
            // Enhanced prediction with camera motion
            val predicted = predictNextWithCameraMotion(cameraVelX, cameraVelY)
            
            // Gradual confidence decay
            val decayFactor = if (missedFrames < 5) 0.95f else 0.85f
            
            smoothedDetection = smoothedDetection.copy(
                cnf = smoothedDetection.cnf * decayFactor,
                x1 = predicted.x1, y1 = predicted.y1,
                x2 = predicted.x2, y2 = predicted.y2,
                cx = predicted.cx, cy = predicted.cy,
                w = predicted.w, h = predicted.h
            )
            
            // Gradual velocity decay
            velocityX *= 0.95f
            velocityY *= 0.95f
            accelerationX *= 0.9f
            accelerationY *= 0.9f
        }

        fun predictNext(): BoundingBox {
            return predictNextWithCameraMotion(0f, 0f)
        }
        
        fun predictNextWithCameraMotion(cameraVelX: Float, cameraVelY: Float): BoundingBox {
            val stabilityFactor = min(1f, totalFrames / 15f)  // Slower stability buildup
            val adaptiveVelocityX = velocityX * stabilityFactor + cameraVelX * 0.1f
            val adaptiveVelocityY = velocityY * stabilityFactor + cameraVelY * 0.1f
            
            val nextCx = smoothedDetection.cx + adaptiveVelocityX + accelerationX * 0.15f
            val nextCy = smoothedDetection.cy + adaptiveVelocityY + accelerationY * 0.15f
            val nextW = max(0.01f, smoothedDetection.w + velocityW * 0.3f)
            val nextH = max(0.01f, smoothedDetection.h + velocityH * 0.3f)
            
            return smoothedDetection.copy(
                cx = nextCx, cy = nextCy, w = nextW, h = nextH,
                x1 = nextCx - nextW / 2f, y1 = nextCy - nextH / 2f,
                x2 = nextCx + nextW / 2f, y2 = nextCy + nextH / 2f
            )
        }

        private fun smoothDetectionEnhanced(newDetection: BoundingBox, positionSmoothingBoost: Float = 0.3f): BoundingBox {
            val movementSpeed = sqrt(velocityX * velocityX + velocityY * velocityY)
            val stability = getStabilityScore()
            
            // Adaptive smoothing based on movement and stability
            val baseSmoothing = smoothingFactor + positionSmoothingBoost * movementSpeed
            val adaptiveSmoothing = when {
                movementSpeed > 0.1f -> (baseSmoothing + 0.3f).coerceAtMost(0.9f)  // Heavy smoothing for fast movement
                stability < 0.5f -> (baseSmoothing + 0.2f).coerceAtMost(0.8f)       // Extra smoothing for unstable tracks
                else -> baseSmoothing.coerceIn(0.3f, 0.7f)                        // Normal smoothing
            }
            
            return BoundingBox(
                x1 = lerp(smoothedDetection.x1, newDetection.x1, adaptiveSmoothing),
                y1 = lerp(smoothedDetection.y1, newDetection.y1, adaptiveSmoothing),
                x2 = lerp(smoothedDetection.x2, newDetection.x2, adaptiveSmoothing),
                y2 = lerp(smoothedDetection.y2, newDetection.y2, adaptiveSmoothing),
                cx = lerp(smoothedDetection.cx, newDetection.cx, adaptiveSmoothing),
                cy = lerp(smoothedDetection.cy, newDetection.cy, adaptiveSmoothing),
                w = lerp(smoothedDetection.w, newDetection.w, adaptiveSmoothing * 0.8f),
                h = lerp(smoothedDetection.h, newDetection.h, adaptiveSmoothing * 0.8f),
                cnf = max(smoothedDetection.cnf * 0.9f, newDetection.cnf),  // Slight confidence smoothing
                cls = newDetection.cls,
                clsName = newDetection.clsName,
                trackingId = id
            )
        }
        
        fun isStable(): Boolean {
            return missedFrames <= 3 && 
                   smoothedDetection.cnf > 0.2f &&
                   totalFrames >= MIN_TRACK_STABILITY &&
                   consecutiveDetections >= 1
        }
        
        fun getStabilityScore(): Float {
            val frameStability = min(1f, totalFrames / 10f)
            val confidenceStability = smoothedDetection.cnf
            val consecutiveStability = min(1f, consecutiveDetections / 3f)
            
            return (frameStability * 0.4f + confidenceStability * 0.4f + consecutiveStability * 0.2f)
        }
        
        fun getStability(): Int {
            return totalFrames
        }
        
        private fun updateStabilityTracking(detection: BoundingBox) {
            // Keep limited history for stability calculation
            confidenceHistory.add(detection.cnf)
            if (confidenceHistory.size > 10) confidenceHistory.removeAt(0)
            
            positionHistory.add(detection.centerX() to detection.centerY())
            if (positionHistory.size > 5) positionHistory.removeAt(0)
        }

        fun isActive(): Boolean {
            return missedFrames <= 3 && smoothedDetection.cnf > 0.2f
        }

        fun getCurrentDetection(): BoundingBox {
            return smoothedDetection.copy()
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}
