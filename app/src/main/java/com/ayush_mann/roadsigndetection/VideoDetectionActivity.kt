// UPDATED VideoDetectionActivity.kt with object tracking and smoothing

package com.ayush_mann.roadsigndetection

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import android.widget.TextView
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*

class VideoDetectionActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var detector: Detector
    private lateinit var selectGalleryCard: MaterialCardView
    private lateinit var recordVideoCard: MaterialCardView
    private lateinit var processBtn: MaterialButton
    private lateinit var backBtn: MaterialButton
    private lateinit var playerView: PlayerView
    private lateinit var overlay: OverlayView
    private lateinit var statusText: TextView
    private lateinit var inferenceTimeText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingText: TextView

    private var exoPlayer: ExoPlayer? = null
    private var selectedUri: Uri? = null
    private var isProcessing = false
    private var isVideoProcessed = false

    // Object tracker for smooth video playback
    private lateinit var videoTracker: ObjectTracker

    // Store detections with their timestamps
    private val detectionResults = mutableMapOf<Long, List<BoundingBox>>()
    private var videoDurationMs = 0L
    private var videoWidth = 0
    private var videoHeight = 0

    private var previousTimestampMs: Long? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 100L

    // Coroutine scope for background processing
    private val processingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val updateOverlayRunnable = object : Runnable {
        override fun run() {
            if (isVideoProcessed && exoPlayer != null) {
                val currentPosition = exoPlayer!!.currentPosition
                updateOverlayForPosition(currentPosition)
            }
            handler.postDelayed(this, updateInterval)
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { loadVideo(it) } }

    private val recordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { loadVideo(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_video_detection)

        selectGalleryCard = findViewById(R.id.selectGalleryCard)
        recordVideoCard  = findViewById(R.id.recordVideoCard)
        processBtn       = findViewById(R.id.processBtn)
        backBtn          = findViewById(R.id.backBtn)
        playerView       = findViewById(R.id.playerView)
        overlay          = findViewById(R.id.videoOverlay)
        statusText       = findViewById(R.id.statusText)
        inferenceTimeText= findViewById(R.id.inferenceTimeText)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        loadingText      = findViewById(R.id.loadingText)

        statusText.setText(R.string.select_a_video_to_get_started)
        processBtn.setText(R.string.process_video)
        backBtn.setText(R.string.back_to_menu)
        processBtn.alpha = .5f; processBtn.isEnabled = false
        inferenceTimeText.visibility = View.GONE
        loadingProgressBar.visibility = View.GONE
        loadingText.visibility = View.GONE

        detector = Detector(baseContext, Constants.getModelPath(baseContext), Constants.LABELS_PATH, this)
        detector.setupWithSettings(baseContext)

        val smoothing = 0.4f // Reduced smoothing factor for video

        // Initialize tracker with stable video tracking parameters
        videoTracker = ObjectTracker(
            maxDistance = 75f,      // Reduced distance for stricter association
            maxMissedFrames = 8,     // Faster cleanup of lost tracks
            smoothingFactor = 0.7f,   // Increased smoothing for stability
            confidenceDecay = 0.85f, // Faster confidence decay for unstable tracks
            minConfidence = 0.35f,  // Higher confidence threshold
            overlapThreshold = 0.75f, // Higher overlap threshold to prevent duplicates
            cameraMotionTolerance = 120f, // Reduced tolerance for better accuracy
            velocitySmoothing = 0.8f,     // Increased velocity smoothing
            accelerationSmoothing = 0.85f, // Increased acceleration smoothing
            positionSmoothingBoost = 0.4f  // Increased smoothing for movement
        )

        setupCardTouchAnimations()
        setupButtonTouchAnimations()
        setupClickListeners()

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        Handler(Looper.getMainLooper()).postDelayed({ animateEntrance() }, 200)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardTouchAnimations() {
        listOf(selectGalleryCard, recordVideoCard).forEach { card ->
            card.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        animateScale(v, .95f, 150)
                        performHaptic()
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        animateScale(v, 1f, 200)
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_CANCEL -> animateScale(v, 1f, 200)
                }
                false
            }
        }
    }

    private fun setupButtonTouchAnimations() {
        listOf(processBtn, backBtn).forEach { btn ->
            btn.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        animateScale(v, .97f, 100)
                        performHaptic()
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        animateScale(v, 1f, 150)
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_CANCEL -> animateScale(v, 1f, 150)
                }
                false
            }
        }
    }

    private fun setupClickListeners() {
        selectGalleryCard.setOnClickListener {
            if (PermissionHelper.hasMediaPermissions(this))
                galleryLauncher.launch("video/*")
            else
                Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
        }

        recordVideoCard.setOnClickListener {
            if (PermissionHelper.hasCameraPermission(this)) {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                recordLauncher.launch(intent)
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
            }
        }

        processBtn.setOnClickListener {
            if (!isVideoProcessed && selectedUri != null) {
                startVideoProcessing()
            }
        }

        backBtn.setOnClickListener { finish() }
    }

    private fun loadVideo(uri: Uri) {
        selectedUri = uri
        isVideoProcessed = false
        detectionResults.clear()
        videoTracker.clear() // Clear previous tracking state

        // Get video metadata
        val (width, height, duration) = getVideoMetadata(uri)
        videoWidth = width
        videoHeight = height
        videoDurationMs = duration

        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
            player.repeatMode = Player.REPEAT_MODE_ALL
        }

        playerView.visibility = View.VISIBLE
        playerView.alpha = 0f
        playerView.animate().alpha(1f).setDuration(400).setInterpolator(OvershootInterpolator()).start()

        statusText.setText(R.string.video_loaded_ready_to_process)
        processBtn.setText(R.string.process_video)
        enableProcessButton()

        overlay.clear() // Clear any previous detections
    }

    private fun getVideoMetadata(uri: Uri): Triple<Int, Int, Long> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        retriever.release()
        return Triple(width, height, duration)
    }

    private fun startVideoProcessing() {
        if (selectedUri == null || isProcessing) return

        isProcessing = true
        showLoadingState()

        processingScope.launch {
            try {
                processVideoFrames()
            } catch (e: Exception) {
                runOnUiThread {
                    hideLoadingState()
                    Toast.makeText(this@VideoDetectionActivity, getString(R.string.error_processing_video, e.message), Toast.LENGTH_LONG).show()
                    isProcessing = false
                }
            }
        }
    }

    private suspend fun processVideoFrames() = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this@VideoDetectionActivity, selectedUri)

        // Detection interval - use settings from FPS setting
        val fpsLimit = Constants.getFrameRateLimit(this@VideoDetectionActivity)
        val detectionIntervalMs = if (fpsLimit > 0) (1000L / fpsLimit) else 100L

        val totalFrames = (videoDurationMs / detectionIntervalMs).toInt()
        val frameTimes = mutableListOf<Long>()

        // Generate frame timestamps based on detection interval
        for (i in 0 until totalFrames) {
            frameTimes.add(i * detectionIntervalMs * 1000L) // Convert to microseconds
        }

        var processedFrames = 0
        val startTime = System.currentTimeMillis()

        for (timeUs in frameTimes) {
            if (!isProcessing) break // Allow cancellation

            try {
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                frame?.let { bitmap ->
                    // Process frame synchronously in IO thread
                    val detections = processFrameSync(bitmap)
                    val timestampMs = timeUs / 1000L

                    // Store results
                    detectionResults[timestampMs] = detections

                    processedFrames++
                    val progress = (processedFrames.toFloat() / frameTimes.size * 100).toInt()

                    runOnUiThread {
                        updateLoadingProgress(progress, processedFrames, frameTimes.size)
                    }
                }
            } catch (e: Exception) {
                // Skip this frame if there's an error
                continue
            }
        }

        retriever.release()

        runOnUiThread {
            val totalTime = System.currentTimeMillis() - startTime
            completeProcessing(totalTime)
        }
    }

    private fun processFrameSync(bitmap: Bitmap): List<BoundingBox> {
        return try {
            detector.detectSync(bitmap) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun showLoadingState() {
        // Show loading container
        val loadingContainer = findViewById<View>(R.id.loadingContainer)
        loadingContainer.visibility = View.VISIBLE

        loadingProgressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        loadingText.text = getString(R.string.processing_video_frames)
        processBtn.isEnabled = false
        processBtn.alpha = 0.5f

        // Animate loading elements
        loadingProgressBar.alpha = 0f
        loadingText.alpha = 0f
        loadingProgressBar.animate().alpha(1f).setDuration(300).start()
        loadingText.animate().alpha(1f).setDuration(300).start()
    }

    private fun updateLoadingProgress(progress: Int, processed: Int, total: Int) {
        loadingProgressBar.progress = progress
        loadingText.text = getString(R.string.processing_frames_progress, processed, total, progress)
    }

    private fun hideLoadingState() {
        val loadingContainer = findViewById<View>(R.id.loadingContainer)

        loadingProgressBar.animate().alpha(0f).setDuration(300).withEndAction {
            loadingProgressBar.visibility = View.GONE
        }.start()

        loadingText.animate().alpha(0f).setDuration(300).withEndAction {
            loadingText.visibility = View.GONE
            loadingContainer.visibility = View.GONE
        }.start()
    }

    private fun completeProcessing(processingTime: Long) {
        isProcessing = false
        isVideoProcessed = true
        hideLoadingState()

        statusText.setText(R.string.video_analysis_complete)
        processBtn.setText(R.string.video_processed)
        processBtn.isEnabled = false

        inferenceTimeText.visibility = View.VISIBLE
        inferenceTimeText.text = getString(R.string.video_processing_results, processingTime, detectionResults.size)

        // Setup video dimensions for overlay
        val surfaceView = playerView.videoSurfaceView
        val surfaceW = surfaceView?.width?.toFloat() ?: playerView.width.toFloat()
        val surfaceH = surfaceView?.height?.toFloat() ?: playerView.height.toFloat()

        overlay.setImageDimensions(surfaceW, surfaceH)
        val offsetX = (playerView.width - surfaceW) / 2f
        val offsetY = (playerView.height - surfaceH) / 2f
        overlay.setImageOffset(offsetX, offsetY, overrideScale = true, customScale = 1f)

        // Start updating overlay based on video position
        handler.post(updateOverlayRunnable)

        animateResults()
        Toast.makeText(this, getString(R.string.video_processing_complete_with_tracking), Toast.LENGTH_LONG).show()
    }

    private fun updateOverlayForPosition(currentPositionMs: Long) {
        val timeWindow = 250L
        val relevantDetections = mutableListOf<BoundingBox>()
        detectionResults.forEach { (timestamp, detections) ->
            if (kotlin.math.abs(timestamp - currentPositionMs) <= timeWindow) {
                relevantDetections.addAll(detections)
            }
        }
        
        // Check if tracking is enabled in settings
        val trackingEnabled = SettingsActivity.Settings.isTrackingEnabled(this@VideoDetectionActivity)
        val dtMs = previousTimestampMs?.let { currentPositionMs - it } ?: 33L
        val finalDetections = if (trackingEnabled) {
            videoTracker.update(relevantDetections, dtMs)
        } else {
            relevantDetections
        }
        
        previousTimestampMs = currentPositionMs // update for next frame
        overlay.setResults(finalDetections)
    }

    private fun enableProcessButton() {
        processBtn.isEnabled = true
        android.animation.AnimatorSet().apply {
            playTogether(
                android.animation.ObjectAnimator.ofFloat(processBtn, "alpha", .5f, 1f),
                android.animation.ObjectAnimator.ofFloat(processBtn, "scaleX", .9f, 1f),
                android.animation.ObjectAnimator.ofFloat(processBtn, "scaleY", .9f, 1f)
            )
            duration = 300
            interpolator = OvershootInterpolator()
            start()
        }
    }

    // These methods are now used only during processing, not for display
    override fun onEmptyDetect() {
        // Not used in batch processing mode
    }

    override fun onDetect(boxes: List<BoundingBox>, inferenceTime: Long) {
        // Not used in batch processing mode
    }

    private fun animateResults() {
        inferenceTimeText.alpha = 0f
        inferenceTimeText.translationY = 20f
        inferenceTimeText.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun animateEntrance() {
        listOf(selectGalleryCard, recordVideoCard).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 100f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 150).toLong())
                .setDuration(500)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
        processBtn.alpha = 0f; processBtn.translationY = 50f
        processBtn.animate().alpha(.5f).translationY(0f).setStartDelay(400).setDuration(400).start()
        backBtn.alpha = 0f; backBtn.translationY = 50f
        backBtn.animate().alpha(1f).translationY(0f).setStartDelay(600).setDuration(400).start()
    }

    private fun animateScale(v: View, scale: Float, duration: Long) {
        v.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(duration)
            .start()
    }

    private fun performHaptic() =
        processBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateOverlayRunnable)
        processingScope.cancel()
        exoPlayer?.release()
        detector.clear()
    }
}