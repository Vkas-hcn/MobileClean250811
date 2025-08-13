package com.each.cheat.mobileclean

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.each.cheat.mobileclean.clean.CleanAppActivity
import com.each.cheat.mobileclean.databinding.ActivityHomeBinding
import com.each.cheat.mobileclean.photo.PhotoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private val binding by lazy { ActivityHomeBinding.inflate(layoutInflater) }

    // 添加一个变量来记录哪个功能触发了权限请求
    private var permissionRequestSource: String? = null

    companion object {
        private const val REQUEST_SOURCE_BOOST = "boost"
        private const val REQUEST_SOURCE_PHOTO = "photo"
        private const val REQUEST_SOURCE_FOLDER = "folder"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()
        setupUI()
        loadStorageInfo()
    }

    private fun setupUI() {
        // 设置Boost按钮点击事件
        binding.tvBoost.setOnClickListener {
            permissionRequestSource = REQUEST_SOURCE_BOOST
            checkPermissionAndStartScan()
        }

        // 设置权限弹窗按钮事件
        binding.inPermiss.tvYes.setOnClickListener {
            hidePermissionDialog()
            PermissionUtils.requestStoragePermission(this)
        }

        binding.inPermiss.tvNo.setOnClickListener {
            hidePermissionDialog()
        }

        // 设置功能按钮点击事件 - 修改为照片管理功能
        binding.llImg.setOnClickListener {
            permissionRequestSource = REQUEST_SOURCE_PHOTO
            checkPermissionAndStartPhotoActivity()
        }

        binding.llFolder.setOnClickListener {
            permissionRequestSource = REQUEST_SOURCE_FOLDER
            checkPermissionAndStartFileActivity()
        }
        onBackPressedDispatcher.addCallback {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
              finish()
            }
        }
        binding.imgMenu.setOnClickListener {
            binding.drawerLayout.open()
        }
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.customDrawer.setOnClickListener {
        }
        binding.tvShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(
                Intent.EXTRA_TEXT,
                "https://play.google.com/store/apps/details?id=${this.packageName}"
            )
            try {
                startActivity(Intent.createChooser(intent, "Share via"))
            } catch (ex: Exception) {
                Toast.makeText(this, "Failed to share", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvPP.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            //TODO
            intent.data = Uri.parse("https://sites.google.com/view/sprightly--cleaner/%E9%A6%96%E9%A1%B5")
            startActivity(intent)
        }
    }

    private fun checkPermissionAndStartPhotoActivity() {
        if (PermissionUtils.hasStoragePermission(this)) {
            // 有权限，直接跳转到照片页面
            startPhotoActivity()
        } else {
            // 没有权限，显示权限申请弹窗
            showPermissionDialog()
        }
    }

    // 添加检查权限并启动文件Activity的方法
    private fun checkPermissionAndStartFileActivity() {
        if (PermissionUtils.hasStoragePermission(this)) {
            // 有权限，直接跳转到文件页面
            startFileActivity()
        } else {
            // 没有权限，显示权限申请弹窗
            showPermissionDialog()
        }
    }

    private fun startPhotoActivity() {
        val intent = Intent(this, PhotoActivity::class.java)
        startActivity(intent)
    }

    // 添加启动文件Activity的方法
    private fun startFileActivity() {
        val intent = Intent(this, FileActivity::class.java)
        startActivity(intent)
    }

    private fun loadStorageInfo() {
        lifecycleScope.launch {
            try {
                val storageInfo = withContext(Dispatchers.IO) {
                    StorageUtils.getStorageInfo(this@HomeActivity)
                }

                // 更新UI
                binding.tvPro.text = storageInfo.usagePercentage.toString()
                binding.tvStorageInfo.text = storageInfo.formattedInfo

            } catch (e: Exception) {
                e.printStackTrace()
                // 设置默认值
                binding.tvPro.text = "35"
                binding.tvStorageInfo.text = "36.5 GB Used / 128 GB Total"
            }
        }
    }

    private fun checkPermissionAndStartScan() {
        if (PermissionUtils.hasStoragePermission(this)) {
            // 有权限，直接跳转
            startCleanActivity()
        } else {
            // 没有权限，显示权限申请弹窗
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        binding.inPermiss.conPermiss.visibility = android.view.View.VISIBLE
    }

    private fun hidePermissionDialog() {
        binding.inPermiss.conPermiss.visibility = android.view.View.GONE
    }

    private fun startCleanActivity() {
        val intent = Intent(this, CleanAppActivity::class.java)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PermissionUtils.REQUEST_STORAGE_PERMISSION -> {
                if (PermissionUtils.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
                    // 权限授予成功 - 根据触发源跳转到相应页面
                    navigateToRequestedActivity()
                } else {
                    // 权限被拒绝
                    showPermissionDeniedDialog()
                }
                // 处理完后重置触发源
                permissionRequestSource = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PermissionUtils.REQUEST_MANAGE_STORAGE_PERMISSION -> {
                if (PermissionUtils.hasStoragePermission(this)) {
                    // 权限授予成功 - 根据触发源跳转到相应页面
                    navigateToRequestedActivity()
                } else {
                    // 权限被拒绝
                    showPermissionDeniedDialog()
                }
                // 处理完后重置触发源
                permissionRequestSource = null
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission request")
            .setMessage("Storage permission is required to scan and clean junk files, please manually turn on the permission in the settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                PermissionUtils.openAppSettings(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    override fun onResume() {
        super.onResume()
        loadStorageInfo()
    }

    private fun navigateToRequestedActivity() {
        when (permissionRequestSource) {
            REQUEST_SOURCE_BOOST -> startCleanActivity()
            REQUEST_SOURCE_PHOTO -> startPhotoActivity()
            REQUEST_SOURCE_FOLDER -> startFileActivity()
        }
    }
}
