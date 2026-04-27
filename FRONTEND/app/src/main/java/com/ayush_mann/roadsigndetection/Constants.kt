package com.ayush_mann.roadsigndetection

object Constants {
    const val LABELS_PATH = "labels.txt"
    
    fun getModelPath(context: android.content.Context): String {
        return SettingsActivity.Settings.getModelPath(context)
    }
    
    fun getFrameRateLimit(context: android.content.Context): Int {
        return SettingsActivity.Settings.getFrameRateLimit(context)
    }
}
