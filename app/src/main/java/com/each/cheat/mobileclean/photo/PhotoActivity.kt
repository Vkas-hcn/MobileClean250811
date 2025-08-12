package com.each.cheat.mobileclean.photo

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.each.cheat.mobileclean.FinishActivity
import com.each.cheat.mobileclean.R
import com.each.cheat.mobileclean.databinding.ActivityPhotoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoActivity : AppCompatActivity() {

    private val binding by lazy { ActivityPhotoBinding.inflate(layoutInflater) }
    private lateinit var photoAdapter: PhotoDateAdapter
    private val allPhotos = mutableListOf<PhotoInfo>()
    private val dateGroups = mutableListOf<PhotoDateGroup>()
    private var rotationAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.photo)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        showLoadingAndScanPhotos()
    }

    private fun setupUI() {
        binding.layoutTip.imgLogo.setImageResource(R.mipmap.ic_image)
        // 设置返回按钮
        binding.imageView3.setOnClickListener {
            finish()
        }
        binding.layoutTip.tvBack.setOnClickListener {
            finish()
        }
        binding.layoutTip.load.setOnClickListener {  }
        binding.llSelectAll.setOnClickListener {  }
        // 设置RecyclerView
        photoAdapter = PhotoDateAdapter {
            updateSelectedInfo()
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@PhotoActivity)
            adapter = photoAdapter
        }

        // 设置全选按钮
        binding.cbSelectAllGlobal.setOnClickListener {
            toggleSelectAll()
        }

        // 设置删除按钮
        binding.btnCleanNow.setOnClickListener {
            showDeleteConfirmDialog()
        }

        // 初始化选中信息
        updateSelectedInfo()
    }

    private fun showLoadingAndScanPhotos() {
        // 显示加载界面
        binding.layoutTip.load.visibility = View.VISIBLE
        binding.layoutTip.tvLoadTip.text = "Scanning…"

        // 开始旋转动画
        startRotationAnimation()

        lifecycleScope.launch {
            // 延迟1秒显示加载效果
            delay(1000)

            // 扫描照片
            val photos = withContext(Dispatchers.IO) {
                scanAllPhotos()
            }

            // 更新UI
            allPhotos.clear()
            allPhotos.addAll(photos)
            groupPhotosByDate()
            photoAdapter.updateData(dateGroups)

            // 隐藏加载界面
            stopRotationAnimation()
            binding.layoutTip.load.visibility = View.GONE

            // 更新扫描结果
            updateScanResult()
        }
    }


    private fun startRotationAnimation() {
        val imgBg1 = binding.layoutTip.imgBg1

        // 如果动画已经在运行，则先取消
        rotationAnimator?.cancel()

        // 只在第一次设置旋转中心点
        if (imgBg1.pivotX == 0f && imgBg1.pivotY == 0f) {
            // 等待布局完成后再设置旋转中心
            imgBg1.post {
                val imgLogo = binding.layoutTip.imgLogo
                val imgBg1 = binding.layoutTip.imgBg1

                // 获取两个View的位置信息
                val logoRect = IntArray(2)
                val bg1Rect = IntArray(2)
                imgLogo.getLocationInWindow(logoRect)
                imgBg1.getLocationInWindow(bg1Rect)

                // 计算img_logo相对于img_bg_1的中心点
                val logocenterX = logoRect[0] + imgLogo.width / 2f
                val logocenterY = logoRect[1] + imgLogo.height / 2f
                val bg1CenterX = bg1Rect[0] + imgBg1.width / 2f
                val bg1CenterY = bg1Rect[1] + imgBg1.height / 2f

                // 设置旋转中心点
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
            // 直接创建动画，不重新设置中心点
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

    private fun scanAllPhotos(): List<PhotoInfo> {
        val photos = mutableListOf<PhotoInfo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.SIZE} > ?"
        val selectionArgs = arrayOf("0")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn) ?: ""
                    val path = c.getString(dataColumn) ?: ""
                    val size = c.getLong(sizeColumn)
                    val dateAdded = c.getLong(dateColumn)

                    // 检查文件是否存在
                    if (File(path).exists()) {
                        photos.add(
                            PhotoInfo(
                                id = id,
                                path = path,
                                name = name,
                                size = size,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return photos
    }

    private fun groupPhotosByDate() {
        dateGroups.clear()
        val groupMap = allPhotos.groupBy { it.getDateKey() }

        groupMap.forEach { (dateKey, photos) ->
            val dateGroup = PhotoDateGroup(
                date = photos.first().getFormattedDate(),
                photos = photos.toMutableList()
            )
            dateGroups.add(dateGroup)
        }

        // 按日期排序（最新的在前面）
        dateGroups.sortByDescending { group ->
            group.photos.firstOrNull()?.dateAdded ?: 0L
        }
    }

    private fun updateScanResult() {
        binding.tvScan.text = "${allPhotos.size} Photos"
    }

    private fun updateSelectedInfo() {
        val selectedPhotos = allPhotos.filter { it.isSelected }
        val totalSize = selectedPhotos.sumOf { it.size }

        // 更新选中大小显示
        val (size, unit) = formatSize(totalSize)
        binding.tvSize.text = size
        binding.tvSizeUn.text = unit

        // 更新全选按钮状态
        val isAllSelectedImg = allPhotos.isNotEmpty() && allPhotos.all { it.isSelected }
        val selectIcon = if (isAllSelectedImg) {
            R.mipmap.ic_selected_yuan
        } else {
            R.mipmap.ic_dis_selected_yuan
        }
        binding.cbSelectAllGlobal.setImageResource(selectIcon)

        // 更新删除按钮状态
        binding.btnCleanNow.isEnabled = selectedPhotos.isNotEmpty()
        binding.btnCleanNow.alpha = if (selectedPhotos.isNotEmpty()) 1.0f else 0.5f
    }

    private fun formatSize(bytes: Long): Pair<String, String> {
        return when {
            bytes >= 1024 * 1024 * 1024 -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                Pair(String.format("%.1f", gb), " GB")
            }
            bytes >= 1024 * 1024 -> {
                val mb = bytes / (1024.0 * 1024.0)
                Pair(String.format("%.1f", mb), " MB")
            }
            bytes >= 1024 -> {
                val kb = bytes / 1024.0
                Pair(String.format("%.1f", kb), " KB")
            }
            else -> {
                Pair(bytes.toString(), " B")
            }
        }
    }

    private fun toggleSelectAll() {
        val isAllSelectedImg = allPhotos.all { it.isSelected }
        val newState = !isAllSelectedImg

        allPhotos.forEach { it.isSelected = newState }
        dateGroups.forEach { it.setAllSelected(newState) }

        photoAdapter.notifyDataSetChanged()
        updateSelectedInfo()
    }

    private fun showDeleteConfirmDialog() {
        val selectedPhotos = allPhotos.filter { it.isSelected }
        if (selectedPhotos.isEmpty()) {
            showToast("Please select photos to delete")
            return
        }

        val totalSize = selectedPhotos.sumOf { it.size }
        val (size, unit) = formatSize(totalSize)

        AlertDialog.Builder(this)
            .setTitle("Delete Photos")
            .setMessage("Are you sure you want to delete ${selectedPhotos.size} photos ($size$unit)?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedPhotos()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedPhotos() {
        startRotationAnimation()
        val selectedPhotos = allPhotos.filter { it.isSelected }
        val totalSize = selectedPhotos.sumOf { it.size }

        // 显示清理中界面
        binding.layoutTip.load.visibility = View.VISIBLE
        binding.layoutTip.tvLoadTip.text = "Cleaning..."

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // 删除选中的照片
                selectedPhotos.forEach { photo ->
                    try {
                        // 通过MediaStore删除
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            photo.id
                        )
                        contentResolver.delete(uri, null, null)

                        // 如果MediaStore删除失败，尝试直接删除文件
                        val file = File(photo.path)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 延迟1秒显示清理效果
            delay(1000)

            // 停止动画
            stopRotationAnimation()
            binding.layoutTip.load.visibility = View.GONE

            // 跳转到完成页面
            val intent = Intent(this@PhotoActivity, FinishActivity::class.java)
            intent.putExtra("cleaned_size", totalSize)
            startActivity(intent)
            finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRotationAnimation()
    }
}