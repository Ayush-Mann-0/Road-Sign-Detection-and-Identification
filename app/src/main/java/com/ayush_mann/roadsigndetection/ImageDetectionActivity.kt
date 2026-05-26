package com.ayush_mann.roadsigndetection

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.InputStream

class ImageDetectionActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var detector: Detector
    private lateinit var selectImageCard: MaterialCardView
    private lateinit var takePictureCard: MaterialCardView
    private lateinit var processBtn: MaterialButton
    private lateinit var backBtn: MaterialButton
    private lateinit var selectedImageView: ImageView
    private lateinit var resultOverlay: OverlayView
    private lateinit var statusText: TextView
    private lateinit var inferenceTimeText: TextView
    private lateinit var resultsContainer: MaterialCardView

    private var selectedBitmap: Bitmap? = null
    private var isProcessing = false

    // Launchers for image selection
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImageFromUri(it) }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                imageBitmap.let {
                    val argbBitmap = ensureArgb8888(it)
                    selectedBitmap = argbBitmap
                    displaySelectedImage()
                    enableProcessButton()
                }
            } else {
                selectedBitmap = null
                processBtn.isEnabled = false
                Toast.makeText(this, getString(R.string.camera_capture_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            selectedBitmap = null
            processBtn.isEnabled = false
            Toast.makeText(this, getString(R.string.no_image_captured), Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureArgb8888(src: Bitmap): Bitmap {
        if (src.config == Bitmap.Config.ARGB_8888) return src
        return src.copy(Bitmap.Config.ARGB_8888, false)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_image_detection)

        initViews()
        setupDetector()
        setupAnimations()
        setupClickListeners()
        setupBackPressHandler()
        animateEntrance()
    }

    private fun initViews() {
        selectImageCard = findViewById(R.id.selectImageCard)
        takePictureCard = findViewById(R.id.takePictureCard)
        processBtn = findViewById(R.id.processBtn)
        backBtn = findViewById(R.id.backBtn)
        selectedImageView = findViewById(R.id.selectedImageView)
        resultOverlay = findViewById(R.id.resultOverlay)
        statusText = findViewById(R.id.statusText)
        inferenceTimeText = findViewById(R.id.inferenceTimeText)
        resultsContainer = findViewById(R.id.resultsContainer)

        // Initially disable process button
        processBtn.alpha = 0.5f
        processBtn.isEnabled = false

        // Hide results initially
        resultsContainer.visibility = View.GONE
    }

    private fun setupDetector() {
        detector = Detector(baseContext, Constants.getModelPath(baseContext), Constants.LABELS_PATH, this)
        detector.setupWithSettings(baseContext)
    }

    private fun setupAnimations() {
        setupCardTouchAnimation(selectImageCard)
        setupCardTouchAnimation(takePictureCard)
        setupButtonTouchAnimation(processBtn)
        setupButtonTouchAnimation(backBtn)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardTouchAnimation(card: MaterialCardView) {
        card.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    animateScale(view, 0.95f, 150)
                    performHapticFeedback()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animateScale(view, 1.0f, 200)
                }
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonTouchAnimation(button: MaterialButton) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    animateScale(view, 0.97f, 100)
                    performHapticFeedback()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animateScale(view, 1.0f, 150)
                }
            }
            false
        }
    }

    private fun setupClickListeners() {
        selectImageCard.setOnClickListener {
            animatePulse(selectImageCard)
            selectImageFromGallery()
        }

        takePictureCard.setOnClickListener {
            animatePulse(takePictureCard)
            takePictureFromCamera()
        }

        processBtn.setOnClickListener {
            if (!isProcessing && selectedBitmap != null) {
                processImage()
            }
        }

        backBtn.setOnClickListener {
            handleBackNavigation()
        }
    }

    private fun selectImageFromGallery() {
        if (PermissionHelper.hasMediaPermissions(this)) {
            selectImageLauncher.launch("image/*")
        } else {
            Toast.makeText(this, getString(R.string.media_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getImageDisplayRect(iv: ImageView): RectF {
        val matrix = iv.imageMatrix
        val d = iv.drawable ?: return RectF(0f,0f,0f,0f)
        val src = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        val dst = RectF()
        matrix.mapRect(dst, src)
        return dst   // left, top, right, bottom on-screen in view-coords
    }

    private fun takePictureFromCamera() {
        if (PermissionHelper.hasCameraPermission(this)) {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(takePictureIntent)
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            selectedBitmap?.let {
                displaySelectedImage()
                enableProcessButton()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun displaySelectedImage() {
        selectedBitmap?.let { bitmap ->
            selectedImageView.setImageBitmap(bitmap)
            selectedImageView.visibility = View.VISIBLE

            // Animate image appearance
            selectedImageView.alpha = 0f
            selectedImageView.scaleX = 0.8f
            selectedImageView.scaleY = 0.8f

            selectedImageView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()

            statusText.text = getString(R.string.image_selected_ready_to_process)
            animateStatusText()
        }
    }

    private fun enableProcessButton() {
        processBtn.isEnabled = true

        val fadeIn = ObjectAnimator.ofFloat(processBtn, "alpha", processBtn.alpha, 1.0f)
        val scaleX = ObjectAnimator.ofFloat(processBtn, "scaleX", 0.9f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(processBtn, "scaleY", 0.9f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleX, scaleY)
        animatorSet.duration = 300
        animatorSet.interpolator = OvershootInterpolator(1.2f)
        animatorSet.start()
    }

    private fun processImage() {
        selectedBitmap?.let { bitmap ->
            val argbBitmap = ensureArgb8888(bitmap)
            isProcessing = true
            processBtn.text = getString(R.string.processing_image)
            processBtn.isEnabled = false
            statusText.text = getString(R.string.ai_analyzing_image)
            animateStatusText()
            resultsContainer.visibility = View.GONE
            detector.detect(argbBitmap)
        }
    }

    private fun animateEntrance() {
        // Animate header
        val headerCard = findViewById<MaterialCardView>(R.id.headerCard)
        headerCard.alpha = 0f
        headerCard.translationY = -100f

        headerCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        // Stagger animation for option cards
        val cards = listOf(selectImageCard, takePictureCard)
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 100f

            Handler(Looper.getMainLooper()).postDelayed({
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setInterpolator(OvershootInterpolator(0.8f))
                    .start()
            }, (index * 150).toLong())
        }

        // Animate buttons
        val buttons = listOf(processBtn, backBtn)
        buttons.forEachIndexed { index, button ->
            button.alpha = 0f
            button.translationY = 50f

            Handler(Looper.getMainLooper()).postDelayed({
                button.animate()
                    .alpha(if (button == processBtn) 0.5f else 1f)
                    .translationY(0f)
                    .setDuration(400)
                    .start()
            }, 800 + (index * 100).toLong())
        }
    }

    private fun animateScale(view: View, scale: Float, duration: Long) {
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animatePulse(view: View) {
        val pulse = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.05f, 1.0f)
        val pulseY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.05f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(pulse, pulseY)
        animatorSet.duration = 200
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }

    private fun animateStatusText() {
        statusText.alpha = 0f
        statusText.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun performHapticFeedback() {
        processBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun setupBackPressHandler() {
        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun handleBackNavigation() {
        if (isProcessing) {
            Toast.makeText(this, getString(R.string.please_wait_processing_complete), Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
    }

    // Detector listener implementations
    override fun onEmptyDetect() {
        runOnUiThread {
            isProcessing = false
            processBtn.text = "🔍 Process Image"
            processBtn.isEnabled = true
            statusText.text = getString(R.string.no_road_signs_detected)
            animateStatusText()

            resultsContainer.visibility = View.VISIBLE
            resultOverlay.clear()
            inferenceTimeText.text = getString(R.string.no_detections_found_text)

            animateResultsAppearance()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            // UI resets
            isProcessing = false
            processBtn.text = "🔍 Process Image"
            processBtn.isEnabled = true

            val count = boundingBoxes.size
            statusText.text = "✅ Found $count road sign${if (count != 1) "s" else ""}!"
            animateStatusText()

            // Make sure the image is visible
            selectedImageView.visibility = View.VISIBLE

            // Compute where the image actually sits on screen
            val dispRect = getImageDisplayRect(selectedImageView)

            // Tell overlay both the size *and* the offset
            resultOverlay.setImageDimensions(dispRect.width(), dispRect.height())
            resultOverlay.setImageOffset(dispRect.left, dispRect.top)

            // Finally draw the boxes
            resultOverlay.setResults(boundingBoxes)

            // Show timing & results
            inferenceTimeText.text = getString(R.string.processed_in_time_ms, inferenceTime)
            resultsContainer.visibility = View.VISIBLE
            animateResultsAppearance()

            Toast.makeText(this@ImageDetectionActivity,
                getString(R.string.detection_complete), Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateResultsAppearance() {
        resultsContainer.alpha = 0f
        resultsContainer.translationY = 50f

        resultsContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.6f))
            .start()
    }
}