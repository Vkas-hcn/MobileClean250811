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

    /**
     * 检查是否有存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要管理所有文件权限
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 需要读写存储权限
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 6以下默认有权限
            true
        }
    }

    /**
     * 请求存储权限
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 请求管理所有文件权限
            requestManageStoragePermission(activity)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 请求读写存储权限
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

    /**
     * Android 11+ 请求管理所有文件权限
     */
    private fun requestManageStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION)
            } catch (e: Exception) {
                // 如果上面的方式不支持，使用通用设置页面
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION)
            }
        }
    }

    /**
     * 检查权限请求结果
     */
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

    /**
     * 检查是否应该显示权限说明
     */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            false
        }
    }

    /**
     * 跳转到应用设置页面
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${activity.packageName}")
        activity.startActivity(intent)
    }
}