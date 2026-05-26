package com.ayush_mann.roadsigndetection

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.ayush_mann.roadsigndetection.databinding.ActivityLiveDetectionBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * LiveDetectionActivity — real-time camera road-sign detection.
 *
 * Added features vs. original FRONTEND:
 *  • Live Mode: keeps the screen on (FLAG_KEEP_SCREEN_ON) while this activity is active.
 *  • Cleans up the flag on pause so it doesn't leak to other activities.
 */
class LiveDetectionActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityLiveDetectionBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectTracker: ObjectTracker

    private var cameraImageWidth = 0
    private var cameraImageHeight = 0
    private var lastLiveFrameTimeMs: Long? = null
    private var lastProcessTime = 0L
    private var minFrameInterval = 100L

    @Volatile private var isFinishingActivity = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (PermissionHelper.hasCameraPermission(this)) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Live Mode: keep screen on ─────────────────────────────────────────
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        detector = Detector(baseContext, Constants.getModelPath(baseContext), Constants.LABELS_PATH, this)
        detector.setupWithSettings(baseContext)

        objectTracker = ObjectTracker(
            maxDistance            = 50f,
            maxMissedFrames        = 5,
            smoothingFactor        = 0.6f,
            confidenceDecay        = 0.85f,
            minConfidence          = 0.4f,
            overlapThreshold       = 0.7f,
            cameraMotionTolerance  = 100f,
            velocitySmoothing      = 0.8f,
            accelerationSmoothing  = 0.85f,
            positionSmoothingBoost = 0.4f
        )

        val fpsLimit = Constants.getFrameRateLimit(this)
        minFrameInterval = if (fpsLimit > 0) (1000L / fpsLimit) else 100L

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (PermissionHelper.hasCameraPermission(this)) startCamera()
        else requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener({
                cameraProvider = future.get()
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun bindCameraUseCases() {
        val cp = cameraProvider ?: return
        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation).build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val now = System.currentTimeMillis()
                if (isFinishingActivity || isDestroyed) { imageProxy.close(); return@setAnalyzer }
                if (now - lastProcessTime < minFrameInterval) { imageProxy.close(); return@setAnalyzer }
                lastProcessTime = now

                val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                }
                val rotated = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
                cameraImageWidth  = rotated.width
                cameraImageHeight = rotated.height

                runOnUiThread { if (!isFinishingActivity && !isDestroyed) updateOverlayDimensionsWithLetterboxing() }
                imageProxy.close()

                if (!isFinishingActivity && !isDestroyed) detector.detect(rotated)
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error", e)
                try { imageProxy.close() } catch (_: Exception) {}
            }
        }

        cp.unbindAll()
        try {
            camera = cp.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.surfaceProvider = binding.viewFinder.surfaceProvider
            binding.viewFinder.post { updateOverlayDimensions() }
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    private fun updateOverlayDimensionsWithLetterboxing() {
        if (cameraImageWidth <= 0 || cameraImageHeight <= 0) return
        val vw = binding.viewFinder.width.toFloat()
        val vh = binding.viewFinder.height.toFloat()
        val imgAR = cameraImageWidth.toFloat() / cameraImageHeight
        val viewAR = vw / vh
        val (dw, dh, ox, oy) = if (imgAR > viewAR) {
            val dw = vw; val dh = vw / imgAR; arrayOf(dw, dh, 0f, (vh - dh) / 2f)
        } else {
            val dh = vh; val dw = vh * imgAR; arrayOf(dw, dh, (vw - dw) / 2f, 0f)
        }
        binding.overlay.setImageDimensions(dw, dh)
        binding.overlay.setImageOffset(ox, oy)
    }

    private fun updateOverlayDimensions() {
        if (cameraImageWidth <= 0) return
        val rotation = binding.viewFinder.display.rotation
        val portrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
        val (w, h) = if (portrait) cameraImageHeight.toFloat() to cameraImageWidth.toFloat()
                     else cameraImageWidth.toFloat() to cameraImageHeight.toFloat()
        binding.overlay.setImageDimensions(w, h)
    }

    override fun onResume() {
        super.onResume()
        isFinishingActivity = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            if (PermissionHelper.hasCameraPermission(this)) {
                if (cameraProvider == null) startCamera() else bindCameraUseCases()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resume error", e)
            Toast.makeText(this, "Camera error. Restarting…", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        isFinishingActivity = true
        // Release screen-on flag when app is backgrounded
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            imageAnalyzer?.clearAnalyzer()
            objectTracker.clear()
            cameraProvider?.unbind(imageAnalyzer)
        } catch (e: Exception) {
            Log.e(TAG, "Pause error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isFinishingActivity = true
        try {
            cameraProvider?.unbindAll()
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null
            detector.clear()
            if (!cameraExecutor.isShutdown) cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Destroy error", e)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            if (isFinishingActivity || isDestroyed) return@runOnUiThread
            val now = System.currentTimeMillis()
            val dt  = lastLiveFrameTimeMs?.let { now - it } ?: 33L
            val trackingEnabled = SettingsActivity.Settings.isTrackingEnabled(this)
            val finals = if (trackingEnabled) objectTracker.update(boundingBoxes, dt) else boundingBoxes
            lastLiveFrameTimeMs = now
            binding.overlay.setResults(finals)
            val count = if (trackingEnabled) objectTracker.getActiveTrackCount() else boundingBoxes.size
            binding.tvInferenceTime.text = getString(R.string.live_detection_stats, inferenceTime, count)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onEmptyDetect() {
        runOnUiThread {
            if (isFinishingActivity || isDestroyed) return@runOnUiThread
            val now = System.currentTimeMillis()
            val dt  = lastLiveFrameTimeMs?.let { now - it } ?: 33L
            val trackingEnabled = SettingsActivity.Settings.isTrackingEnabled(this)
            val finals = if (trackingEnabled) objectTracker.update(emptyList(), dt) else emptyList()
            lastLiveFrameTimeMs = now
            binding.overlay.setResults(finals)
            val count = if (trackingEnabled) objectTracker.getActiveTrackCount() else 0
            binding.tvInferenceTime.text = getString(R.string.live_detections_count, count)
        }
    }

    companion object { private const val TAG = "LiveDetection" }
}
