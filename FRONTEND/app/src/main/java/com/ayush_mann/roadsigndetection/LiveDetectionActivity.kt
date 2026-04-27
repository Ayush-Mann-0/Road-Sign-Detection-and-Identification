package com.ayush_mann.roadsigndetection

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import android.view.Surface
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.ayush_mann.roadsigndetection.databinding.ActivityLiveDetectionBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveDetectionActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityLiveDetectionBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector

    private lateinit var cameraExecutor: ExecutorService

    // Add object tracker for smooth tracking
    private lateinit var objectTracker: ObjectTracker

    // Track the actual camera feed dimensions
    private var cameraImageWidth = 0
    private var cameraImageHeight = 0

    private var lastLiveFrameTimeMs: Long? = null

    private var lastProcessTime = 0L
    private var minFrameInterval = 100L // Process at most every 100ms - will be updated from settings

    @Volatile
    private var isFinishingActivity = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (PermissionHelper.hasCameraPermission(this)) {
            startCamera()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = Detector(baseContext, Constants.getModelPath(baseContext), Constants.LABELS_PATH, this)
        detector.setupWithSettings(baseContext)

        // Initialize object tracker with stable tracking parameters
        objectTracker = ObjectTracker(
            maxDistance = 50f,      // Reduced distance for stricter association
            maxMissedFrames = 5,     // Faster cleanup of lost tracks
            smoothingFactor = 0.6f,   // Increased smoothing for stability
            confidenceDecay = 0.85f, // Faster confidence decay for unstable tracks
            minConfidence = 0.4f,    // Higher confidence threshold
            overlapThreshold = 0.7f,  // Higher overlap threshold to prevent duplicate tracks
            cameraMotionTolerance = 100f, // Reduced tolerance for better accuracy
            velocitySmoothing = 0.8f,     // Increased velocity smoothing
            accelerationSmoothing = 0.85f, // Increased acceleration smoothing
            positionSmoothingBoost = 0.4f  // Increased smoothing for movement
        )

        // Load frame rate from settings
        val fpsLimit = Constants.getFrameRateLimit(this)
        minFrameInterval = if (fpsLimit > 0) (1000L / fpsLimit) else 100L

        if (PermissionHelper.hasCameraPermission(this)) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun updateOverlayDimensionsWithLetterboxing() {
        if (cameraImageWidth > 0 && cameraImageHeight > 0) {
            val previewView = binding.viewFinder
            val viewWidth = previewView.width.toFloat()
            val viewHeight = previewView.height.toFloat()

            // Calculate the actual preview area considering letterboxing
            val imageAspectRatio = cameraImageWidth.toFloat() / cameraImageHeight.toFloat()
            val viewAspectRatio = viewWidth / viewHeight

            val (displayWidth, displayHeight, offsetX, offsetY) = if (imageAspectRatio > viewAspectRatio) {
                // Image is wider - letterboxing on top/bottom
                val displayWidth = viewWidth
                val displayHeight = viewWidth / imageAspectRatio
                val offsetY = (viewHeight - displayHeight) / 2f
                arrayOf(displayWidth, displayHeight, 0f, offsetY)
            } else {
                // Image is taller - letterboxing on left/right
                val displayHeight = viewHeight
                val displayWidth = viewHeight * imageAspectRatio
                val offsetX = (viewWidth - displayWidth) / 2f
                arrayOf(displayWidth, displayHeight, offsetX, 0f)
            }

            // Set the overlay dimensions with proper letterboxing offset
            binding.overlay.setImageDimensions(displayWidth, displayHeight)
            binding.overlay.setImageOffset(offsetX, offsetY)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val currentTime = System.currentTimeMillis()
                if (isFinishingActivity || isDestroyed) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                if (currentTime - lastProcessTime < minFrameInterval) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                lastProcessTime = currentTime
                
                // Create bitmap safely
                val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) {
                        postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                    }
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                    matrix, true
                )

                // Store the actual frame dimensions
                cameraImageWidth = rotatedBitmap.width
                cameraImageHeight = rotatedBitmap.height

                // Calculate the actual preview display area
                runOnUiThread {
                    if (!isFinishingActivity && !isDestroyed) {
                        updateOverlayDimensionsWithLetterboxing()
                    }
                }

                imageProxy.close()
                
                // Only detect if activity is still valid
                if (!isFinishingActivity && !isDestroyed) {
                    detector.detect(rotatedBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
                try {
                    imageProxy.close()
                } catch (closeException: Exception) {
                    Log.e(TAG, "Error closing image proxy", closeException)
                }
            }
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview?.surfaceProvider = binding.viewFinder.surfaceProvider

            // Update overlay dimensions after binding
            binding.viewFinder.post {
                updateOverlayDimensions()
            }
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun updateOverlayDimensions() {
        if (cameraImageWidth > 0 && cameraImageHeight > 0) {
            val rotation = binding.viewFinder.display.rotation
            val portrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180

            val (w, h) = if (portrait) {
                cameraImageHeight.toFloat() to cameraImageWidth.toFloat()
            } else {
                cameraImageWidth.toFloat() to cameraImageHeight.toFloat()
            }

            binding.overlay.setImageDimensions(w, h)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isFinishingActivity = true
        
        try {
            // Stop camera processing first
            camera?.let { cam ->
                cameraProvider?.unbindAll()
            }
            
            // Clear analyzer safely
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null
            
            // Clear detector resources
            detector.clear()
            
            // Shutdown executor
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isFinishingActivity = false
        
        try {
            if (PermissionHelper.hasCameraPermission(this)) {
                if (cameraProvider == null) {
                    startCamera()
                } else {
                    // Rebind camera use cases if camera provider exists but analyzer was cleared
                    bindCameraUseCases()
                }
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during resume", e)
            Toast.makeText(this, "Camera error. Restarting...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Flag activity is finishing or backgrounded.
        isFinishingActivity = true

        try {
            // Remove analyzer first to stop processing
            imageAnalyzer?.clearAnalyzer()
            
            // Clear tracker to avoid memory leaks
            objectTracker.clear()
            
            // Unbind camera use cases but keep camera provider
            cameraProvider?.unbind(imageAnalyzer)
        } catch (e: Exception) {
            Log.e(TAG, "Error during pause", e)
        }
    }

    companion object {
        private const val TAG = "Camera"
    }

    @SuppressLint("SetTextI18n")
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            try {
                if (isFinishingActivity || isDestroyed) {
                    return@runOnUiThread
                }
                
                val nowMs = System.currentTimeMillis()
                val dtMs = lastLiveFrameTimeMs?.let { nowMs - it } ?: 33L
                
                // Check if tracking is enabled in settings
                val trackingEnabled = SettingsActivity.Settings.isTrackingEnabled(this@LiveDetectionActivity)
                val finalObjects = if (trackingEnabled) {
                    objectTracker.update(boundingBoxes, dtMs)
                } else {
                    boundingBoxes
                }
                
                lastLiveFrameTimeMs = nowMs
                binding.overlay.setResults(finalObjects)
                val activeCount = if (trackingEnabled) objectTracker.getActiveTrackCount() else boundingBoxes.size
                binding.tvInferenceTime.text = getString(R.string.live_detection_stats, inferenceTime, activeCount)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDetect callback", e)
            }
        }
    }

    // Do the same for onEmptyDetect
    @SuppressLint("SetTextI18n")
    override fun onEmptyDetect() {
        runOnUiThread {
            try {
                if (isFinishingActivity || isDestroyed) {
                    return@runOnUiThread
                }
                
                val nowMs = System.currentTimeMillis()
                val dtMs = lastLiveFrameTimeMs?.let { nowMs - it } ?: 33L
                
                // Check if tracking is enabled in settings
                val trackingEnabled = SettingsActivity.Settings.isTrackingEnabled(this@LiveDetectionActivity)
                val finalObjects = if (trackingEnabled) {
                    objectTracker.update(emptyList(), dtMs)
                } else {
                    emptyList()
                }
                
                lastLiveFrameTimeMs = nowMs
                binding.overlay.setResults(finalObjects)
                val activeCount = if (trackingEnabled) objectTracker.getActiveTrackCount() else 0
                binding.tvInferenceTime.text = getString(R.string.live_detections_count, activeCount)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onEmptyDetect callback", e)
            }
        }
    }

}