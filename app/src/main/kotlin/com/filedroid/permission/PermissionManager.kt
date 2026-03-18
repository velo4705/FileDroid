package com.filedroid.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

interface PermissionManager {
    /** Returns true iff every permission in [requiredStoragePermissions] is currently granted. */
    fun isStoragePermissionGranted(context: Context): Boolean

    /** Returns the list of permissions required for storage access on the current API level. */
    fun requiredStoragePermissions(): List<String>
}

class PermissionManagerImpl : PermissionManager {

    override fun requiredStoragePermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> listOf(
            // MANAGE_EXTERNAL_STORAGE is a special permission checked via Environment API
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )
        else -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun isStoragePermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            requiredStoragePermissions().all { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
