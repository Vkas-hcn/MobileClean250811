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
        binding.tvBoost.setOnClickListener {
            permissionRequestSource = REQUEST_SOURCE_BOOST
            checkPermissionAndStartScan()
        }

        binding.inPermiss.tvYes.setOnClickListener {
            hidePermissionDialog()
            PermissionUtils.requestStoragePermission(this)
        }

        binding.inPermiss.tvNo.setOnClickListener {
            hidePermissionDialog()
        }

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
            startPhotoActivity()
        } else {
            showPermissionDialog()
        }
    }

    private fun checkPermissionAndStartFileActivity() {
        if (PermissionUtils.hasStoragePermission(this)) {
            startFileActivity()
        } else {
            showPermissionDialog()
        }
    }

    private fun startPhotoActivity() {
        val intent = Intent(this, PhotoActivity::class.java)
        startActivity(intent)
    }

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

                binding.tvPro.text = storageInfo.usagePercentage.toString()
                binding.tvStorageInfo.text = storageInfo.formattedInfo

            } catch (e: Exception) {
                e.printStackTrace()
                binding.tvPro.text = "35"
                binding.tvStorageInfo.text = "36.5 GB Used / 128 GB Total"
            }
        }
    }

    private fun checkPermissionAndStartScan() {
        if (PermissionUtils.hasStoragePermission(this)) {
            startCleanActivity()
        } else {
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
                    navigateToRequestedActivity()
                } else {
                    showPermissionDeniedDialog()
                }
                permissionRequestSource = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PermissionUtils.REQUEST_MANAGE_STORAGE_PERMISSION -> {
                if (PermissionUtils.hasStoragePermission(this)) {
                    navigateToRequestedActivity()
                } else {
                    showPermissionDeniedDialog()
                }
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
