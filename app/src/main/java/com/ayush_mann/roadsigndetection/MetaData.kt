package com.ayush_mann.roadsigndetection

import android.content.Context
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer

/**
 * Ported from BACKEND (com.surendramaran.yolov8tflite.MetaData) — package changed only.
 * Extracts class names from TFLite model metadata or a label file in assets.
 */
object MetaData {

    fun extractNamesFromMetadata(model: MappedByteBuffer): List<String> {
        return try {
            val extractor = MetadataExtractor(model)
            val inputStream = extractor.getAssociatedFile("temp_meta.txt")
            val metadata = inputStream?.bufferedReader()?.use { it.readText() } ?: return emptyList()

            val regex = Regex("'names': \\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(metadata)
            val namesContent = match?.groups?.get(1)?.value ?: return emptyList()

            val regex2 = Regex("\"([^\"]*)\"|'([^']*)'")
            regex2.findAll(namesContent)
                .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun extractNamesFromLabelFile(context: Context, labelPath: String): List<String> {
        val labels = mutableListOf<String>()
        return try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                labels.add(line.trim())
                line = reader.readLine()
            }
            reader.close()
            inputStream.close()
            labels
        } catch (_: IOException) {
            emptyList()
        }
    }

    /** Fallback placeholder class names if no labels are found. */
    val TEMP_CLASSES = List(1000) { "class${it + 1}" }
}
