package com.surendramaran.yolov8tflite

object Constants {
    const val MODEL_PATH = "model_float32.tflite"
    val LABELS_PATH: String? = "label.txt" // provide your labels.txt file if the metadata not present in the model
}
