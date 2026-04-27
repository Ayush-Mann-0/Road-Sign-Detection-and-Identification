package com.ayush_mann.roadsigndetection

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class OptionsActivity : AppCompatActivity() {

    private var selectedMode: DetectionMode? = null
    private lateinit var imageCard: MaterialCardView
    private lateinit var videoCard: MaterialCardView
    private lateinit var liveCard: MaterialCardView
    private lateinit var continueBtn: MaterialButton

    private lateinit var settingsButton: AppCompatImageButton

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }


    enum class DetectionMode {
        IMAGE, VIDEO, LIVE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_options)

        initViews()
        setupAnimations()
        setupClickListeners()
        setupBackPressHandler()
        animateEntrance()
    }

    private fun initViews() {
        imageCard = findViewById(R.id.imageDetectionCard)
        videoCard = findViewById(R.id.videoDetectionCard)
        liveCard = findViewById(R.id.liveDetectionCard)
        continueBtn = findViewById(R.id.continueBtn)
        settingsButton = findViewById<AppCompatImageButton>(R.id.settingsButton)

        // Initially disable continue button
        continueBtn.alpha = 0.5f
        continueBtn.isEnabled = false
    }

    private fun setupAnimations() {
        // Add touch animations to all cards
        setupCardTouchAnimation(imageCard)
        setupCardTouchAnimation(videoCard)
        setupCardTouchAnimation(liveCard)
        setupButtonTouchAnimation(continueBtn)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardTouchAnimation(card: MaterialCardView) {
        card.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Scale down animation with haptic feedback
                    animateScale(view, 0.95f, 150)
                    performHapticFeedback()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Scale back up
                    animateScale(view, 1.0f, 200)
                }
            }
            false // Let click listeners handle the actual click
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
        imageCard.setOnClickListener {
            selectDetectionMode(DetectionMode.IMAGE, imageCard)
            animatePulse(imageCard)
        }

        videoCard.setOnClickListener {
            selectDetectionMode(DetectionMode.VIDEO, videoCard)
            animatePulse(videoCard)
        }

        liveCard.setOnClickListener {
            selectDetectionMode(DetectionMode.LIVE, liveCard)
            animatePulse(liveCard)
        }

        continueBtn.setOnClickListener {
            if (selectedMode != null) {
                animateButtonSuccess()
                proceedToNextActivity()
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            try {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                // Animations not available, continue without them
            }
        }
    }

    private fun selectDetectionMode(mode: DetectionMode, selectedCard: MaterialCardView) {
        // Toggle behavior: if clicking the same mode, unselect it
        if (selectedMode == mode) {
            selectedMode = null
            animateCardDeselect(selectedCard)
            disableContinueButton()
            return
        }

        selectedMode = mode

        // Reset all cards to default state
        resetCardSelections()

        // Highlight selected card with enhanced animation
        highlightSelectedCard(selectedCard)

        // Enable continue button with animation
        enableContinueButton()
    }

    private fun resetCardSelections() {
        val cards = listOf(imageCard, videoCard, liveCard)
        cards.forEach { card ->
            animateCardDeselect(card)
        }
    }

    private fun highlightSelectedCard(card: MaterialCardView) {
        // Simple selection animation - just scale down slightly
        
        // Scale down animation
        val scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.95f)
        val scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.95f)
        
        // Background color change
        val originalColor = when (card.id) {
            R.id.imageDetectionCard -> ContextCompat.getColor(this, R.color.primary_btn_bg)
            R.id.videoDetectionCard -> ContextCompat.getColor(this, R.color.soft_purple)
            R.id.liveDetectionCard -> ContextCompat.getColor(this, R.color.sage_green)
            else -> ContextCompat.getColor(this, R.color.primary_btn_bg)
        }
        
        val highlightColor = when (card.id) {
            R.id.imageDetectionCard -> ContextCompat.getColor(this, R.color.primary_btn_pressed)
            R.id.videoDetectionCard -> ContextCompat.getColor(this, R.color.soft_purple_pressed)
            R.id.liveDetectionCard -> ContextCompat.getColor(this, R.color.sage_green_pressed)
            else -> ContextCompat.getColor(this, R.color.primary_btn_pressed)
        }
        
        val colorAnimation = ObjectAnimator.ofArgb(card, "cardBackgroundColor", originalColor, highlightColor)
        
        // Stroke for selection indicator
        card.strokeWidth = 4
        card.strokeColor = ContextCompat.getColor(this, R.color.accent_primary)
        
        // Combine animations
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, colorAnimation)
        animatorSet.duration = 200
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }

    private fun animateCardDeselect(card: MaterialCardView) {
        // Reset scale smoothly
        val scaleResetX = ObjectAnimator.ofFloat(card, "scaleX", card.scaleX, 1f)
        val scaleResetY = ObjectAnimator.ofFloat(card, "scaleY", card.scaleY, 1f)
        
        // Reset background color to original
        val originalColor = when (card.id) {
            R.id.imageDetectionCard -> ContextCompat.getColor(this, R.color.primary_btn_bg)
            R.id.videoDetectionCard -> ContextCompat.getColor(this, R.color.soft_purple)
            R.id.liveDetectionCard -> ContextCompat.getColor(this, R.color.sage_green)
            else -> ContextCompat.getColor(this, R.color.primary_btn_bg)
        }
        
        val currentColor = card.cardBackgroundColor
        val colorReset = ObjectAnimator.ofArgb(card, "cardBackgroundColor", 
            if (currentColor != null) currentColor.defaultColor else originalColor, originalColor)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleResetX, scaleResetY, colorReset)
        animatorSet.duration = 200
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
        
        // Remove stroke immediately
        card.strokeWidth = 0
    }

    private fun enableContinueButton() {
        // Enable immediately to prevent timing issues
        continueBtn.isEnabled = true

        // Fade in and scale up animation
        val fadeIn = ObjectAnimator.ofFloat(continueBtn, "alpha", continueBtn.alpha, 1.0f)
        val scaleX = ObjectAnimator.ofFloat(continueBtn, "scaleX", 0.9f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(continueBtn, "scaleY", 0.9f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleX, scaleY)
        animatorSet.duration = 300
        animatorSet.interpolator = OvershootInterpolator(1.2f)
        animatorSet.start()
    }

    private fun animateEntrance() {
        // Animate logo entrance
        val logoCard = findViewById<MaterialCardView>(R.id.logoCard)
        logoCard.alpha = 0f
        logoCard.translationY = -100f

        logoCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        // Stagger animation for detection cards
        val cards = listOf(imageCard, videoCard, liveCard, settingsButton)
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

        // Animate continue button last
        continueBtn.alpha = 0f
        continueBtn.translationY = 50f
        Handler(Looper.getMainLooper()).postDelayed({
            continueBtn.animate()
                .alpha(if (continueBtn.isEnabled) 1.0f else 0.5f) // Respect current enabled state
                .translationY(0f)
                .setDuration(400)
                .start()
        }, 800)
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
        val pulse = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.08f, 1.0f)
        val pulseY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.08f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(pulse, pulseY)
        animatorSet.duration = 250
        animatorSet.interpolator = OvershootInterpolator(0.5f)
        animatorSet.start()
    }
    
    private fun startSelectionPulse(card: MaterialCardView) {
        // Removed continuous pulse animation for cleaner selection
    }
    
    private fun isCardSelected(card: MaterialCardView): Boolean {
        return when (card.id) {
            R.id.imageDetectionCard -> selectedMode == DetectionMode.IMAGE
            R.id.videoDetectionCard -> selectedMode == DetectionMode.VIDEO
            R.id.liveDetectionCard -> selectedMode == DetectionMode.LIVE
            else -> false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun animateButtonSuccess() {
        // Success animation for continue button
        val scaleX = ObjectAnimator.ofFloat(continueBtn, "scaleX", 1.0f, 1.1f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(continueBtn, "scaleY", 1.0f, 1.1f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = 200
        animatorSet.start()

        // Change button text briefly
        val originalText = continueBtn.text
        continueBtn.text = getString(R.string.loading_with_checkmark)

        Handler(Looper.getMainLooper()).postDelayed({
            continueBtn.text = originalText
        }, 1000)
    }

    private fun performHapticFeedback() {
        // Modern haptic feedback
        continueBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun proceedToNextActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            when (selectedMode) {
                DetectionMode.IMAGE -> {
                    checkAndRequestPermissions(DetectionMode.IMAGE)
                }
                DetectionMode.VIDEO -> {
                    checkAndRequestPermissions(DetectionMode.VIDEO)
                }
                DetectionMode.LIVE -> {
                    checkAndRequestPermissions(DetectionMode.LIVE)
                }
                null -> {
                    Toast.makeText(this, getString(R.string.please_select_detection_mode), Toast.LENGTH_SHORT).show()
                }
            }
        }, 200)
    }

    private fun setupBackPressHandler() {
        // Modern back press handling using OnBackPressedDispatcher
        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }

        // Register the callback with the dispatcher
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun handleBackNavigation() {
        // Direct exit without clearing selection
        animateExit {
            finish()
        }
    }

    private fun animateExit(onComplete: () -> Unit) {
        val views = listOf(imageCard, videoCard, liveCard, continueBtn)
        var completedAnimations = 0

        views.forEach { view ->
            view.animate()
                .alpha(0f)
                .translationY(100f)
                .setDuration(300)
                .withEndAction {
                    completedAnimations++
                    if (completedAnimations == views.size) {
                        onComplete()
                    }
                }
                .start()
        }
    }

    private fun disableContinueButton() {
        // Disable immediately to prevent timing issues
        continueBtn.isEnabled = false

        val fadeOut = ObjectAnimator.ofFloat(continueBtn, "alpha", continueBtn.alpha, 0.5f)
        fadeOut.duration = 200
        fadeOut.start()
    }

    /**
     * Permission handling methods
     */
    private fun checkAndRequestPermissions(mode: DetectionMode) {
        val requiredPermissions = getRequiredPermissionsForMode(mode)
        
        if (requiredPermissions.isEmpty()) {
            navigateToActivity(mode)
            return
        }

        val notGrantedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isEmpty()) {
            navigateToActivity(mode)
        } else {
            // Check if we should show rationale for any permission
            val shouldShowRationale = notGrantedPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }

            if (shouldShowRationale) {
                showPermissionRationaleDialog(notGrantedPermissions.toTypedArray(), mode)
            } else {
                // First time requesting permissions
                permissionLauncher.launch(notGrantedPermissions.toTypedArray())
            }
        }
    }

    private fun getRequiredPermissionsForMode(mode: DetectionMode): Array<String> {
        return when (mode) {
            DetectionMode.IMAGE -> {
                // Need both camera (for take picture option) and media permissions for image detection
                arrayOf(Manifest.permission.CAMERA) + getMediaPermissions()
            }
            DetectionMode.VIDEO -> {
                // Need both camera (for record video option) and media permissions for video detection
                arrayOf(Manifest.permission.CAMERA) + getMediaPermissions()
            }
            DetectionMode.LIVE -> {
                // Need camera permission for live detection
                arrayOf(Manifest.permission.CAMERA)
            }
        }
    }

    private fun getMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> { // API 34+
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> { // API 33
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            else -> { // API 32 and below
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        
        if (allGranted) {
            // All permissions granted, proceed to activity
            selectedMode?.let { mode ->
                navigateToActivity(mode)
            }
        } else {
            // Some permissions denied
            val permanentlyDenied = permissions.entries.any { (permission, granted) ->
                !granted && !shouldShowRequestPermissionRationale(permission)
            }

            if (permanentlyDenied) {
                showPermissionDeniedDialog()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permissions_required_for_feature),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToActivity(mode: DetectionMode) {
        val intent = when (mode) {
            DetectionMode.IMAGE -> Intent(this, ImageDetectionActivity::class.java)
            DetectionMode.VIDEO -> Intent(this, VideoDetectionActivity::class.java)
            DetectionMode.LIVE -> Intent(this, LiveDetectionActivity::class.java)
        }
        
        startActivity(intent)
    }

    private fun showPermissionRationaleDialog(permissions: Array<String>, mode: DetectionMode) {
        val permissionNames = permissions.joinToString(", ") { permission ->
            when (permission) {
                Manifest.permission.CAMERA -> "Camera"
                Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                Manifest.permission.READ_MEDIA_IMAGES -> "Image Access"
                Manifest.permission.READ_MEDIA_VIDEO -> "Video Access"
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> "Media Access"
                else -> "Required Permission"
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.permissions_required_title))
            .setMessage(getString(R.string.permissions_rationale_message, permissionNames))
            .setPositiveButton(getString(R.string.grant_permissions_button)) { _, _ ->
                permissionLauncher.launch(permissions)
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.permissions_denied), Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.permissions_required_title))
            .setMessage(getString(R.string.permissions_permanently_denied_message))
            .setPositiveButton(getString(R.string.go_to_settings_button)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}