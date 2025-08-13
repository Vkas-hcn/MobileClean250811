package com.each.cheat.mobileclean

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    const val REQUEST_STORAGE_PERMISSION = 100
    const val REQUEST_MANAGE_STORAGE_PERMISSION = 101


    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageStoragePermission(activity)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_STORAGE_PERMISSION
            )
        }
    }


    private fun requestManageStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION)
            }
        }
    }


    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            }
            else -> false
        }
    }




    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${activity.packageName}")
        activity.startActivity(intent)
    }
}