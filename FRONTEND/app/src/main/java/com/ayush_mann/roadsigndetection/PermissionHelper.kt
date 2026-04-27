package com.ayush_mann.roadsigndetection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // Camera permission
        permissions.add(Manifest.permission.CAMERA)

        // Media permissions based on Android version
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> { // API 34+
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> { // API 33
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> { // API 32 and below
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        return permissions.toTypedArray()
    }

    fun hasMediaPermissions(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> { // API 34+
                // Check if we have full access or partial access
                hasFullMediaAccess(context) || hasPartialMediaAccess(context)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> { // API 33
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            }
            else -> { // API 32 and below
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasFullMediaAccess(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPartialMediaAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}