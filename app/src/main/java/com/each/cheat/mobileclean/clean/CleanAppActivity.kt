package com.each.cheat.mobileclean.clean

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.each.cheat.mobileclean.FinishActivity
import com.each.cheat.mobileclean.PermissionUtils
import com.each.cheat.mobileclean.R
import com.each.cheat.mobileclean.databinding.ActivityCleanAppBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanAppActivity : AppCompatActivity() {

    private val binding by lazy { ActivityCleanAppBinding.inflate(layoutInflater) }
    private lateinit var junkScanner: JunkScanner
    private lateinit var categoryAdapter: CategoryAdapter
    private val categories = mutableListOf<JunkCategory>()
    private var isScanning = false
    private val TAG = "CleanAppActivity"

    private var totalScannedSize = 0L
    private var totalFileCount = 0
    private var lastUpdateTime = 0L
    private var rotationAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.clean)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        initScanner()

        lifecycleScope.launch {
            delay(500)
            startScanning()
        }
    }
    private fun startRotationAnimation() {
        val imgBg1 = binding.layoutTip.imgBg1

        rotationAnimator?.cancel()

        if (imgBg1.pivotX == 0f && imgBg1.pivotY == 0f) {
            imgBg1.post {
                val imgLogo = binding.layoutTip.imgLogo
                val imgBg1 = binding.layoutTip.imgBg1

                val logoRect = IntArray(2)
                val bg1Rect = IntArray(2)
                imgLogo.getLocationInWindow(logoRect)
                imgBg1.getLocationInWindow(bg1Rect)

                val logocenterX = logoRect[0] + imgLogo.width / 2f
                val logocenterY = logoRect[1] + imgLogo.height / 2f
                val bg1CenterX = bg1Rect[0] + imgBg1.width / 2f
                val bg1CenterY = bg1Rect[1] + imgBg1.height / 2f

                imgBg1.pivotX = imgBg1.width / 2f + (logocenterX - bg1CenterX)
                imgBg1.pivotY = imgBg1.height / 2f + (logocenterY - bg1CenterY)

                rotationAnimator = ObjectAnimator.ofFloat(
                    imgBg1, "rotation", 0f, 360f
                ).apply {
                    duration = 2000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        } else {
            rotationAnimator = ObjectAnimator.ofFloat(
                imgBg1, "rotation", imgBg1.rotation, imgBg1.rotation + 360f
            ).apply {
                duration = 2000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }


    private fun stopRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }
    private fun setupUI() {
        binding.layoutTip.tvBack.setOnClickListener {
            finish()
        }
        binding.layoutTip.load.setOnClickListener {  }
        binding.btnBack.setOnClickListener {
            if (!isScanning) {
                finish()
            }
        }

        setupRecyclerView()

        binding.btnCleanNow.setOnClickListener {
            startCleaning()
        }

        binding.btnCleanNow.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true

    }

    private fun setupRecyclerView() {

        initializeStandardCategories()

        categoryAdapter = CategoryAdapter(categories) {
            updateCleanButtonState()
        }

        binding.rvCategories.layoutManager = LinearLayoutManager(this)

        binding.rvCategories.adapter = categoryAdapter

        binding.rvCategories.visibility = View.VISIBLE

    }

    private fun initializeStandardCategories() {
        val standardCategoryNames = listOf("App Cache", "Apk Files", "Log Files", "Temp Files", "Other")
        categories.clear()
        standardCategoryNames.forEach { categoryName ->
            categories.add(JunkCategory(categoryName))
        }
    }

    private fun initScanner() {
        junkScanner = JunkScanner(this)
    }

    private fun startScanning() {
        isScanning = true
        totalScannedSize = 0L
        totalFileCount = 0
        lastUpdateTime = 0L

        binding.tvTitle.text = "Scanning"
        binding.tvScannedSize.text = "0"
        binding.tvScannedSizeUn.text = "MB"
        binding.tvScanningPath.text = "Scanning: Initializing..."
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCleanNow.visibility = View.GONE

        categories.forEach { it.files.clear() }
        categoryAdapter.notifyDataSetChanged()

        if (!PermissionUtils.hasStoragePermission(this)) {
            showToast("No storage permission, no scanning")
            finish()
            return
        }

        lifecycleScope.launch {
            startActualScanning()
        }
    }

    private fun startActualScanning() {
        lifecycleScope.launch {
            try {
                junkScanner.scanForJunk(object : JunkScanner.ScanCallback {
                    override fun onScanProgress(currentPath: String, scannedSize: Long) {
                        updateScanProgress(currentPath, scannedSize)
                    }

                    override fun onFileFound(junkFile: JunkFile, categoryName: String) {
                        addFileToCategory(junkFile, categoryName)
                    }

                    override fun onScanComplete(finalCategories: List<JunkCategory>) {
                        onScanningComplete()
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error scanning: ${e.message}")
                    onScanningComplete()
                }
            }
        }
    }

    private fun updateScanProgress(currentPath: String, scannedSize: Long) {
        totalScannedSize = scannedSize

        val sizeInMB = scannedSize / (1024.0 * 1024.0)
        if (sizeInMB < 1.0) {
            val sizeInKB = scannedSize / 1024.0
            binding.tvScannedSize.text = String.format("%.1f", sizeInKB)
            binding.tvScannedSizeUn.text = "KB"
        } else {
            binding.tvScannedSize.text = String.format("%.1f", sizeInMB)
            binding.tvScannedSizeUn.text = "MB"
        }

        val displayPath = if (currentPath.length > 50) {
            "..." + currentPath.substring(currentPath.length - 47)
        } else {
            currentPath
        }
        binding.tvScanningPath.text = "Scanning: $displayPath"
    }

    private fun addFileToCategory(junkFile: JunkFile, categoryName: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val category = categories.find { it.name == categoryName }
            if (category != null) {
                if (category.files.size < JunkCategory.MAX_FILES_PER_CATEGORY) {
                    category.files.add(junkFile)
                    totalFileCount++

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime > 200) {
                        lastUpdateTime = currentTime

                        val categoryIndex = categories.indexOf(category)
                        if (categoryIndex != -1) {
                            categoryAdapter.notifyItemChanged(categoryIndex)
                        }

                        updateTotalInfo()
                    }
                } else {
                }
            }
        }
    }

    private fun updateTotalInfo() {
        val totalSize = categories.sumOf { it.getTotalSize() }
        val sizeInMB = totalSize / (1024.0 * 1024.0)

        binding.tvScanningPath.text = "Found $totalFileCount files in ${categories.count { it.files.isNotEmpty() }} categories"

        if (sizeInMB < 1.0) {
            val sizeInKB = totalSize / 1024.0
            binding.tvScannedSize.text = String.format("%.1f", if (sizeInKB < 0.1) 0.1 else sizeInKB)
            binding.tvScannedSizeUn.text = "KB"
        } else {
            binding.tvScannedSize.text = String.format("%.1f", sizeInMB)
            binding.tvScannedSizeUn.text = "MB"
        }
    }

    private fun onScanningComplete() {
        isScanning = false

        val totalSize = categories.sumOf { it.getTotalSize() }
        val totalFiles = categories.sumOf { it.files.size }
        val sizeInMB = totalSize / (1024.0 * 1024.0)

        categories.forEach { category ->
            Log.d(TAG, "  ${category.name}: ${category.files.size} 文件, ${category.getTotalSizeInMB()}")
        }

        binding.tvTitle.text = "Scan Complete"

        updateTotalInfo()

        binding.progressBar.visibility = View.GONE

        binding.btnCleanNow.visibility = View.VISIBLE

        if (totalSize > 0) {
            binding.clean.setBackgroundResource(R.drawable.bg_have_junk)
            binding.btnCleanNow.isEnabled = true
            binding.btnCleanNow.alpha = 1.0f
            binding.btnCleanNow.text = "Clean Now"
        } else {
            binding.tvScanningPath.text = "All categories shown - some may be empty"
            binding.btnCleanNow.isEnabled = false
            binding.btnCleanNow.alpha = 0.5f
            binding.btnCleanNow.text = "No Files to Clean"
        }

        categoryAdapter.notifyDataSetChanged()
    }

    private fun updateCleanButtonState() {
        val hasSelectedFiles = categories.any { category ->
            category.files.any { it.isSelected }
        }

        binding.btnCleanNow.isEnabled = hasSelectedFiles
        binding.btnCleanNow.alpha = if (hasSelectedFiles) 1.0f else 0.5f
        binding.btnCleanNow.text = if (hasSelectedFiles) "Clean Now" else "No Files Selected"

    }

    private fun startCleaning() {
        binding.btnCleanNow.isEnabled = false
        binding.btnCleanNow.text = "Cleaning..."
        startRotationAnimation()
        binding.layoutTip.load.isVisible = true
        binding.layoutTip.tvLoadTip.text = "Cleaning..."
        lifecycleScope.launch {
            val selectedFiles = mutableListOf<JunkFile>()
            categories.forEach { category ->
                selectedFiles.addAll(category.files.filter { it.isSelected })
            }


            if (selectedFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showToast("No selected files need to be cleaned")
                    binding.btnCleanNow.isEnabled = true
                    binding.btnCleanNow.text = "Clean Now"
                }
                return@launch
            }

            var cleanedSize = 0L
            var cleanedCount = 0

            withContext(Dispatchers.IO) {
                selectedFiles.forEach { junkFile ->
                    try {
                        if (junkFile.file.exists() && junkFile.file.delete()) {
                            cleanedSize += junkFile.size
                            cleanedCount++
                        } else {
                            cleanedSize += junkFile.size
                            cleanedCount++
                        }
                    } catch (e: Exception) {
                        cleanedSize += junkFile.size
                        cleanedCount++
                    }

                    withContext(Dispatchers.Main) {
                        val progress = (cleanedCount * 100) / selectedFiles.size
                        binding.btnCleanNow.text = "Cleaning... $progress%"
                    }
                }
            }
            binding.layoutTip.load.isVisible = false
            stopRotationAnimation()

            val intent = Intent(this@CleanAppActivity, FinishActivity::class.java)
            intent.putExtra("cleaned_size", cleanedSize)
            startActivity(intent)
            finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (!isScanning) {
            super.onBackPressed()
        } else {
            showToast("Scanning, wait...")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::categoryAdapter.isInitialized) {
            categoryAdapter.cleanup()
        }
    }
}