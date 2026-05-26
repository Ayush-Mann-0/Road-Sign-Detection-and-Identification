package com.ayush_mann.roadsigndetection

/**
 * App-wide constants.
 * Model paths are resolved through SettingsActivity.Settings (and ModelManager if a
 * downloaded file exists) so activities never hard-code asset paths.
 */
object Constants {
    /** Label file bundled in assets – used as fallback when model metadata is absent. */
    const val LABELS_PATH = "labels.txt"

    /**
     * Returns the active model path.
     * ModelManager.resolveModelPath() returns the absolute path of a downloaded file if
     * one exists; otherwise it returns the plain asset filename for FileUtil.loadMappedFile().
     */
    fun getModelPath(context: android.content.Context): String {
        val assetName = SettingsActivity.getModelPath(context)
        return ModelManager.resolveModelPath(context, assetName)
    }

    fun getFrameRateLimit(context: android.content.Context): Int =
        SettingsActivity.getFrameRateLimit(context)
}
