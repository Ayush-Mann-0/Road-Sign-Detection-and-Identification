package com.ayush_mann.roadsigndetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import androidx.core.graphics.scale

/**
 * YOLOv8-TFLite Detector — integrated from BACKEND's MetaData extraction
 * while keeping all FRONTEND features (settings, detectSync, stabilization).
 */
class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {
    private var interpreter: Interpreter? = null
    private val labels = mutableListOf<String>()

    private var tensorWidth  = 0
    private var tensorHeight = 0
    private var numChannel   = 0
    private var numElements  = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    // Dynamic settings
    var confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
        private set
    var maxDetections: Int = DEFAULT_MAX_DETECTIONS
        private set
    var isGpuAccelerationEnabled: Boolean = true
        private set

    fun setup() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, modelPath)

            // Try metadata first (from BACKEND), then fall back to label file
            labels.clear()
            labels.addAll(MetaData.extractNamesFromMetadata(modelBuffer))
            if (labels.isEmpty()) {
                labels.addAll(MetaData.extractNamesFromLabelFile(context, labelPath))
            }
            if (labels.isEmpty()) {
                Log.w(TAG, "No labels found; using placeholder names")
                labels.addAll(MetaData.TEMP_CLASSES.take(100))
            }

            val compatList = CompatibilityList()
            val options = Interpreter.Options().apply {
                if (isGpuAccelerationEnabled && compatList.isDelegateSupportedOnThisDevice) {
                    addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
                } else {
                    setNumThreads(4)
                    setUseNNAPI(true)
                }
            }

            interpreter = Interpreter(modelBuffer, options)
            val inputShape  = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()

            // Support [1,H,W,3] and [1,3,H,W]
            tensorWidth  = if (inputShape[1] == 3) inputShape[2] else inputShape[1]
            tensorHeight = if (inputShape[1] == 3) inputShape[3] else inputShape[2]
            numChannel   = outputShape[1]
            numElements  = outputShape[2]

            Log.d(TAG, "Ready: ${tensorWidth}x${tensorHeight}, ${labels.size} labels, gpu=$isGpuAccelerationEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Setup failed", e)
        }
    }

    fun restart(isGpu: Boolean) {
        isGpuAccelerationEnabled = isGpu
        clear()
        setup()
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        if (!isReady()) return
        val t = SystemClock.uptimeMillis()
        val result = runInference(frame)
        val inferenceTime = SystemClock.uptimeMillis() - t
        if (result == null) detectorListener.onEmptyDetect()
        else detectorListener.onDetect(result, inferenceTime)
    }

    fun detectSync(frame: Bitmap): List<BoundingBox>? {
        if (!isReady()) return null
        return try { runInference(frame) } catch (e: Exception) { null }
    }

    fun updateSettings(context: Context) {
        confidenceThreshold      = SettingsActivity.Settings.getConfidenceThreshold(context)
        maxDetections            = SettingsActivity.Settings.getMaxDetections(context)
        isGpuAccelerationEnabled = SettingsActivity.Settings.isGpuAccelerationEnabled(context)
    }

    fun setupWithSettings(context: Context) {
        updateSettings(context)
        setup()
    }

    private fun isReady() =
        interpreter != null && tensorWidth > 0 && tensorHeight > 0 && numChannel > 0 && numElements > 0

    private fun runInference(frame: Bitmap): List<BoundingBox>? {
        val resized   = frame.scale(tensorWidth, tensorHeight, false)
        val ti        = TensorImage(INPUT_IMAGE_TYPE).also { it.load(resized) }
        val processed = imageProcessor.process(ti)
        val output    = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter!!.run(processed.buffer, output.buffer)
        return bestBox(output.floatArray)
    }

    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val candidates = mutableListOf<BoundingBox>()
        for (c in 0 until numElements) {
            var maxConf = -1f; var maxIdx = -1; var j = 4; var idx = c + numElements * j
            while (j < numChannel) {
                if (array[idx] > maxConf) { maxConf = array[idx]; maxIdx = j - 4 }
                j++; idx += numElements
            }
            if (maxConf < confidenceThreshold || maxIdx < 0 || maxIdx >= labels.size) continue
            val cx = array[c]; val cy = array[c + numElements]
            val w  = array[c + numElements * 2]; val h = array[c + numElements * 3]
            val x1 = cx - w / 2f; val y1 = cy - h / 2f; val x2 = cx + w / 2f; val y2 = cy + h / 2f
            if (x1 < 0f || x1 > 1f || y1 < 0f || y1 > 1f || x2 < 0f || x2 > 1f || y2 < 0f || y2 > 1f) continue
            candidates.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, labels[maxIdx]))
        }
        if (candidates.isEmpty()) return null
        val nms = applyNMS(candidates)
        val stable = nms.filter { b -> b.area() > MIN_AREA && b.cnf > STABLE_CONFIDENCE }
        return stable.ifEmpty { null }
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val sorted   = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selected = mutableListOf<BoundingBox>()
        while (sorted.isNotEmpty() && selected.size < maxDetections) {
            val first = sorted.removeAt(0)
            selected.add(first)
            sorted.removeAll { iou(first, it) >= IOU_THRESHOLD }
        }
        return selected
    }

    private fun iou(a: BoundingBox, b: BoundingBox): Float {
        val ix = maxOf(0f, minOf(a.x2, b.x2) - maxOf(a.x1, b.x1))
        val iy = maxOf(0f, minOf(a.y2, b.y2) - maxOf(a.y1, b.y1))
        val inter = ix * iy
        return inter / (a.w * a.h + b.w * b.h - inter)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val TAG = "Detector"
        private const val INPUT_MEAN                = 0f
        private const val INPUT_STANDARD_DEVIATION  = 255f
        private val   INPUT_IMAGE_TYPE              = DataType.FLOAT32
        private val   OUTPUT_IMAGE_TYPE             = DataType.FLOAT32
        const val     DEFAULT_CONFIDENCE_THRESHOLD  = 0.3f
        private const val IOU_THRESHOLD             = 0.5f
        private const val MIN_AREA                  = 0.001f
        private const val STABLE_CONFIDENCE         = 0.25f
        private const val DEFAULT_MAX_DETECTIONS    = 10
    }
}
