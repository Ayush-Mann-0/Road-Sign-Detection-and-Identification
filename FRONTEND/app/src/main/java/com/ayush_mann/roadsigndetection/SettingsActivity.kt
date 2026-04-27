package com.ayush_mann.roadsigndetection

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    
    // UI Components
    private lateinit var backButton: AppCompatImageButton
    private lateinit var modelFloat16: RadioButton
    private lateinit var modelFloat32: RadioButton
    private lateinit var confidenceThreshold: SeekBar
    private lateinit var confidenceValue: TextView
    private lateinit var maxDetections: SeekBar
    private lateinit var maxDetectionsValue: TextView
    private lateinit var trackingEnabled: Switch
    private lateinit var gpuAcceleration: Switch
    private lateinit var frameRateLimit: SeekBar
    private lateinit var frameRateValue: TextView
    private lateinit var resetButton: MaterialButton
    private lateinit var saveButton: MaterialButton

    // Default values
    companion object {
        private const val PREFS_NAME = "RoadSignDetectionSettings"
        private const val KEY_MODEL_TYPE = "model_type"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_MAX_DETECTIONS = "max_detections"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
        private const val KEY_GPU_ACCELERATION = "gpu_acceleration"
        private const val KEY_FRAME_RATE_LIMIT = "frame_rate_limit"
        
        private const val DEFAULT_MODEL_TYPE = "float16"
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.3f
        private const val DEFAULT_MAX_DETECTIONS = 10
        private const val DEFAULT_TRACKING_ENABLED = true
        private const val DEFAULT_GPU_ACCELERATION = true
        private const val DEFAULT_FRAME_RATE_LIMIT = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initViews()
        loadSettings()
        setupClickListeners()
        setupSeekBarListeners()
    }

    private fun initViews() {
        try {
            backButton = findViewById(R.id.backButton)
            modelFloat16 = findViewById(R.id.modelFloat16)
            modelFloat32 = findViewById(R.id.modelFloat32)
            confidenceThreshold = findViewById(R.id.confidenceThreshold)
            confidenceValue = findViewById(R.id.confidenceValue)
            maxDetections = findViewById(R.id.maxDetections)
            maxDetectionsValue = findViewById(R.id.maxDetectionsValue)
            trackingEnabled = findViewById(R.id.trackingEnabled)
            gpuAcceleration = findViewById(R.id.gpuAcceleration)
            frameRateLimit = findViewById(R.id.frameRateLimit)
            frameRateValue = findViewById(R.id.frameRateValue)
            resetButton = findViewById(R.id.resetButton)
            saveButton = findViewById(R.id.saveButton)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_initializing_views), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadSettings() {
        try {
            // Load model type
            val modelType = sharedPreferences.getString(KEY_MODEL_TYPE, DEFAULT_MODEL_TYPE)
            when (modelType) {
                "float32" -> modelFloat32.isChecked = true
                else -> modelFloat16.isChecked = true
            }

            // Load confidence threshold
            val confidence = sharedPreferences.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
            confidenceThreshold.progress = (confidence * 100).toInt()
            confidenceValue.text = "${confidenceThreshold.progress}%"

            // Load max detections
            val maxDet = sharedPreferences.getInt(KEY_MAX_DETECTIONS, DEFAULT_MAX_DETECTIONS)
            maxDetections.progress = maxDet
            maxDetectionsValue.text = maxDet.toString()

            // Load tracking enabled
            val tracking = sharedPreferences.getBoolean(KEY_TRACKING_ENABLED, DEFAULT_TRACKING_ENABLED)
            trackingEnabled.isChecked = tracking

            // Load GPU acceleration
            val gpu = sharedPreferences.getBoolean(KEY_GPU_ACCELERATION, DEFAULT_GPU_ACCELERATION)
            gpuAcceleration.isChecked = gpu

            // Load frame rate limit
            val frameRate = sharedPreferences.getInt(KEY_FRAME_RATE_LIMIT, DEFAULT_FRAME_RATE_LIMIT)
            frameRateLimit.progress = frameRate
            frameRateValue.text = "$frameRate FPS"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_loading_settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
            try {
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            } catch (e: Exception) {
                // Animations not available, continue without them
            }
        }

        resetButton.setOnClickListener {
            resetToDefaults()
        }

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupSeekBarListeners() {
        confidenceThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                confidenceValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        maxDetections.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maxDetectionsValue.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        frameRateLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                frameRateValue.text = "$progress FPS"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun resetToDefaults() {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_to_defaults_title))
            .setMessage(getString(R.string.reset_to_defaults_message))
            .setPositiveButton(getString(R.string.reset_button)) { _, _ ->
                modelFloat16.isChecked = true
                confidenceThreshold.progress = (DEFAULT_CONFIDENCE_THRESHOLD * 100).toInt()
                confidenceValue.text = "${confidenceThreshold.progress}%"
                maxDetections.progress = DEFAULT_MAX_DETECTIONS
                maxDetectionsValue.text = DEFAULT_MAX_DETECTIONS.toString()
                trackingEnabled.isChecked = DEFAULT_TRACKING_ENABLED
                gpuAcceleration.isChecked = DEFAULT_GPU_ACCELERATION
                frameRateLimit.progress = DEFAULT_FRAME_RATE_LIMIT
                frameRateValue.text = "$DEFAULT_FRAME_RATE_LIMIT FPS"
                
                // Animate reset action
                animateReset()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveSettings() {
        try {
            val editor = sharedPreferences.edit()
            
            // Save model type
            val modelType = if (modelFloat32.isChecked) "float32" else "float16"
            editor.putString(KEY_MODEL_TYPE, modelType)
            
            // Save confidence threshold
            val confidence = confidenceThreshold.progress / 100f
            editor.putFloat(KEY_CONFIDENCE_THRESHOLD, confidence)
            
            // Save max detections
            editor.putInt(KEY_MAX_DETECTIONS, maxDetections.progress)
            
            // Save tracking enabled
            editor.putBoolean(KEY_TRACKING_ENABLED, trackingEnabled.isChecked)
            
            // Save GPU acceleration
            editor.putBoolean(KEY_GPU_ACCELERATION, gpuAcceleration.isChecked)
            
            // Save frame rate limit
            editor.putInt(KEY_FRAME_RATE_LIMIT, frameRateLimit.progress)
            
            // Apply changes asynchronously
            editor.apply()
            
            // Show success feedback
            showSaveSuccess()
            
            // Finish activity after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 1000)
            
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving settings", e)
            Toast.makeText(this, getString(R.string.error_saving_settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateReset() {
        val resetAnimation = ObjectAnimator.ofFloat(resetButton, "scaleX", 1f, 0.9f, 1f)
        resetAnimation.duration = 300
        resetAnimation.start()
        
        Toast.makeText(this, getString(R.string.settings_reset_to_defaults), Toast.LENGTH_SHORT).show()
    }

    private fun showSaveSuccess() {
        // Animate save button
        val saveAnimation = ObjectAnimator.ofFloat(saveButton, "scaleX", 1f, 0.9f, 1f)
        saveAnimation.duration = 300
        saveAnimation.start()
        
        // Change button text temporarily
        val originalText = saveButton.text
        saveButton.text = getString(R.string.settings_saved)
        
        Toast.makeText(this, getString(R.string.settings_saved_successfully), Toast.LENGTH_SHORT).show()
        
        // Reset button text after delay
        saveButton.postDelayed({
            saveButton.text = originalText
        }, 1500)
    }

    // Static methods to access settings from other activities
    object Settings {
        fun getModelType(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_MODEL_TYPE, DEFAULT_MODEL_TYPE) ?: DEFAULT_MODEL_TYPE
        }

        fun getModelPath(context: Context): String {
            val modelType = getModelType(context)
            return when (modelType) {
                "float32" -> "model_float32.tflite"
                else -> "model_float16.tflite"
            }
        }

        fun getConfidenceThreshold(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
        }

        fun getMaxDetections(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_MAX_DETECTIONS, DEFAULT_MAX_DETECTIONS)
        }

        fun isTrackingEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_TRACKING_ENABLED, DEFAULT_TRACKING_ENABLED)
        }

        fun isGpuAccelerationEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_GPU_ACCELERATION, DEFAULT_GPU_ACCELERATION)
        }

        fun getFrameRateLimit(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_FRAME_RATE_LIMIT, DEFAULT_FRAME_RATE_LIMIT)
        }
    }
}