package com.ayush_mann.roadsigndetection

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

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

    // Model update UI (optional card in settings layout)
    private var modelUpdateStatusText: TextView? = null
    private var checkUpdateButton: MaterialButton? = null

    companion object Settings {
        private const val PREFS_NAME                 = "RoadSignDetectionSettings"
        private const val KEY_MODEL_TYPE             = "model_type"
        private const val KEY_CONFIDENCE_THRESHOLD   = "confidence_threshold"
        private const val KEY_MAX_DETECTIONS         = "max_detections"
        private const val KEY_TRACKING_ENABLED       = "tracking_enabled"
        private const val KEY_GPU_ACCELERATION       = "gpu_acceleration"
        private const val KEY_FRAME_RATE_LIMIT       = "frame_rate_limit"

        private const val DEFAULT_MODEL_TYPE         = "float16"
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.3f
        private const val DEFAULT_MAX_DETECTIONS     = 10
        private const val DEFAULT_TRACKING_ENABLED   = true
        private const val DEFAULT_GPU_ACCELERATION   = true
        private const val DEFAULT_FRAME_RATE_LIMIT   = 10

        // Static accessor object – used by all activities
        fun getModelType(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_MODEL_TYPE, DEFAULT_MODEL_TYPE) ?: DEFAULT_MODEL_TYPE
        }

        fun getModelPath(context: Context): String {
            return when (getModelType(context)) {
                "float32" -> "model_float32.tflite"
                else      -> "model_float16.tflite"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initViews()
        loadSettings()
        setupClickListeners()
        setupSeekBarListeners()

        // Optional model-update UI (graceful if views not in layout)
        modelUpdateStatusText = findViewById(R.id.modelUpdateStatus)
        checkUpdateButton     = findViewById(R.id.checkUpdateButton)
        checkUpdateButton?.setOnClickListener { checkForModelUpdate() }
    }

    private fun initViews() {
        try {
            backButton          = findViewById(R.id.backButton)
            modelFloat16        = findViewById(R.id.modelFloat16)
            modelFloat32        = findViewById(R.id.modelFloat32)
            confidenceThreshold = findViewById(R.id.confidenceThreshold)
            confidenceValue     = findViewById(R.id.confidenceValue)
            maxDetections       = findViewById(R.id.maxDetections)
            maxDetectionsValue  = findViewById(R.id.maxDetectionsValue)
            trackingEnabled     = findViewById(R.id.trackingEnabled)
            gpuAcceleration     = findViewById(R.id.gpuAcceleration)
            frameRateLimit      = findViewById(R.id.frameRateLimit)
            frameRateValue      = findViewById(R.id.frameRateValue)
            resetButton         = findViewById(R.id.resetButton)
            saveButton          = findViewById(R.id.saveButton)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "View init error", e)
            Toast.makeText(this, getString(R.string.error_initializing_views), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadSettings() {
        try {
            val modelType = sharedPreferences.getString(KEY_MODEL_TYPE, DEFAULT_MODEL_TYPE)
            when (modelType) { "float32" -> modelFloat32.isChecked = true; else -> modelFloat16.isChecked = true }
            val conf = sharedPreferences.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
            confidenceThreshold.progress = (conf * 100).toInt()
            confidenceValue.text = "${confidenceThreshold.progress}%"
            val maxDet = sharedPreferences.getInt(KEY_MAX_DETECTIONS, DEFAULT_MAX_DETECTIONS)
            maxDetections.progress = maxDet; maxDetectionsValue.text = maxDet.toString()
            trackingEnabled.isChecked = sharedPreferences.getBoolean(KEY_TRACKING_ENABLED, DEFAULT_TRACKING_ENABLED)
            gpuAcceleration.isChecked = sharedPreferences.getBoolean(KEY_GPU_ACCELERATION, DEFAULT_GPU_ACCELERATION)
            val fr = sharedPreferences.getInt(KEY_FRAME_RATE_LIMIT, DEFAULT_FRAME_RATE_LIMIT)
            frameRateLimit.progress = fr; frameRateValue.text = "$fr FPS"
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Load settings error", e)
            Toast.makeText(this, getString(R.string.error_loading_settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
            try { overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) } catch (_: Exception) {}
        }
        resetButton.setOnClickListener { resetToDefaults() }
        saveButton.setOnClickListener  { saveSettings() }
    }

    private fun setupSeekBarListeners() {
        confidenceThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { confidenceValue.text = "$p%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        maxDetections.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { maxDetectionsValue.text = p.toString() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        frameRateLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { frameRateValue.text = "$p FPS" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun resetToDefaults() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_to_defaults_title))
            .setMessage(getString(R.string.reset_to_defaults_message))
            .setPositiveButton(getString(R.string.reset_button)) { _, _ ->
                modelFloat16.isChecked = true
                confidenceThreshold.progress = (DEFAULT_CONFIDENCE_THRESHOLD * 100).toInt()
                confidenceValue.text = "${confidenceThreshold.progress}%"
                maxDetections.progress = DEFAULT_MAX_DETECTIONS; maxDetectionsValue.text = DEFAULT_MAX_DETECTIONS.toString()
                trackingEnabled.isChecked = DEFAULT_TRACKING_ENABLED
                gpuAcceleration.isChecked = DEFAULT_GPU_ACCELERATION
                frameRateLimit.progress = DEFAULT_FRAME_RATE_LIMIT; frameRateValue.text = "$DEFAULT_FRAME_RATE_LIMIT FPS"
                ObjectAnimator.ofFloat(resetButton, "scaleX", 1f, 0.9f, 1f).apply { duration = 300; start() }
                Toast.makeText(this, getString(R.string.settings_reset_to_defaults), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { d, _ -> d.dismiss() }
            .show()
    }

    private fun saveSettings() {
        try {
            sharedPreferences.edit().apply {
                putString(KEY_MODEL_TYPE, if (modelFloat32.isChecked) "float32" else "float16")
                putFloat(KEY_CONFIDENCE_THRESHOLD, confidenceThreshold.progress / 100f)
                putInt(KEY_MAX_DETECTIONS, maxDetections.progress)
                putBoolean(KEY_TRACKING_ENABLED, trackingEnabled.isChecked)
                putBoolean(KEY_GPU_ACCELERATION, gpuAcceleration.isChecked)
                putInt(KEY_FRAME_RATE_LIMIT, frameRateLimit.progress)
                apply()
            }
            showSaveSuccess()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1000)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Save error", e)
            Toast.makeText(this, getString(R.string.error_saving_settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveSuccess() {
        ObjectAnimator.ofFloat(saveButton, "scaleX", 1f, 0.9f, 1f).apply { duration = 300; start() }
        val orig = saveButton.text
        saveButton.text = getString(R.string.settings_saved)
        Toast.makeText(this, getString(R.string.settings_saved_successfully), Toast.LENGTH_SHORT).show()
        saveButton.postDelayed({ saveButton.text = orig }, 1500)
    }

    // ── Model version / update check ─────────────────────────────────────────
    @SuppressLint("SetTextI18n")
    private fun checkForModelUpdate() {
        val modelFile = if (modelFloat32.isChecked) "model_float32.tflite" else "model_float16.tflite"
        modelUpdateStatusText?.text = "Checking for updates…"
        checkUpdateButton?.isEnabled = false

        lifecycleScope.launch {
            val status = ModelManager.checkVersion(this@SettingsActivity, modelFile)
            checkUpdateButton?.isEnabled = true
            when (status) {
                is ModelManager.ModelStatus.Ready ->
                    modelUpdateStatusText?.text = "✓ Model is up to date"
                is ModelManager.ModelStatus.Outdated -> {
                    modelUpdateStatusText?.text =
                        "Update available: ${status.currentVersion} → ${status.newVersion}"
                    showDownloadDialog(modelFile, status.config)
                }
                is ModelManager.ModelStatus.NoNetwork ->
                    modelUpdateStatusText?.text = "Check failed: No network connection."
            }
        }
    }

    private fun showDownloadDialog(modelFile: String, config: ModelManager.RemoteModelConfig) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Model Update Available")
            .setMessage("A new model version (${config.version}) is available. Download now?")
            .setPositiveButton("Download") { _, _ -> startModelDownload(modelFile, config) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startModelDownload(modelFile: String, config: ModelManager.RemoteModelConfig) {
        modelUpdateStatusText?.text = "Downloading… 0%"
        checkUpdateButton?.isEnabled = false

        lifecycleScope.launch {
            val success = ModelManager.downloadModel(this@SettingsActivity, modelFile, config) { progress ->
                runOnUiThread { modelUpdateStatusText?.text = "Downloading… $progress%" }
            }
            checkUpdateButton?.isEnabled = true
            if (success) {
                modelUpdateStatusText?.text = "✓ Download complete. Restart the app to apply."
                Toast.makeText(this@SettingsActivity, "Model updated! Restart required.", Toast.LENGTH_LONG).show()
            } else {
                modelUpdateStatusText?.text = "Download failed. Check your connection."
            }
        }
    }
}
