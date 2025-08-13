package com.each.cheat.mobileclean

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.each.cheat.mobileclean.databinding.ActivityFileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FileItem(
    val file: File,
    val name: String,
    val size: Long,
    val formattedSize: String,
    val type: FileType,
    val lastModified: Long,
    var isSelected: Boolean = false
)

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCS, DOWNLOAD, ZIP, OTHER
}

enum class SizeFilter(val displayName: String, val minSize: Long) {
    ALL("All Size", 0L),
    SIZE_10MB(">10MB", 10 * 1024 * 1024L),
    SIZE_20MB(">20MB", 20 * 1024 * 1024L),
    SIZE_50MB(">50MB", 50 * 1024 * 1024L),
    SIZE_100MB(">100MB", 100 * 1024 * 1024L),
    SIZE_200MB(">200MB", 200 * 1024 * 1024L),
    SIZE_500MB(">500MB", 500 * 1024 * 1024L)
}

enum class TimeFilter(val displayName: String, val daysAgo: Int) {
    ALL("All Time", 0),
    DAY_1("Within 1 day", 1),
    WEEK_1("Within 1 week", 7),
    MONTH_1("Within 1 month", 30),
    MONTH_3("Within 3 month", 90),
    MONTH_6("Within 6 month", 180)
}

class FileActivity : AppCompatActivity() {
    private val binding by lazy { ActivityFileBinding.inflate(layoutInflater) }
    private lateinit var fileAdapter: FileAdapter
    private var allFiles = mutableListOf<FileItem>()
    private var filteredFiles = mutableListOf<FileItem>()
    private var currentTypeFilter = FileType.values().toList() + null // null means all types
    private var currentSizeFilter = SizeFilter.ALL
    private var currentTimeFilter = TimeFilter.ALL
    private var rotationAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.file)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        showLoadingAndScanFiles()
    }

    private fun setupUI() {
        binding.layoutTip.imgLogo.setImageResource(R.mipmap.ic_file)
        binding.textBack.setOnClickListener {
            finish()
        }

        fileAdapter = FileAdapter { position ->
            filteredFiles[position].isSelected = !filteredFiles[position].isSelected
            fileAdapter.notifyItemChanged(position)
            updateDeleteButtonState()
        }
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@FileActivity)
            adapter = fileAdapter
        }

        binding.tvType.setOnClickListener { showTypePopup() }
        binding.tvSize.setOnClickListener { showSizePopup() }
        binding.tvTime.setOnClickListener { showTimePopup() }

        binding.btnCleanNow.setOnClickListener { deleteSelectedFiles() }
        updateDeleteButtonState()
    }

    private fun showLoadingAndScanFiles() {
        binding.layoutTip.load.visibility = View.VISIBLE
        startRotationAnimation()

        lifecycleScope.launch {
            delay(1000) // 显示1秒loading

            val files = withContext(Dispatchers.IO) {
                scanAllFiles()
            }

            allFiles.clear()
            allFiles.addAll(files)
            applyFilters()

            binding.layoutTip.load.visibility = View.GONE
            stopRotationAnimation()
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

    private suspend fun scanAllFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileItem>()
        val scannedPaths = mutableSetOf<String>()

        val directories = listOf(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        ).filterNotNull()

        for (directory in directories) {
            if (directory.exists() && directory.isDirectory) {
                scanDirectory(directory, files, scannedPaths)
            }
        }

        files.distinctBy { it.file.absolutePath }
    }

    private fun scanDirectory(directory: File, files: MutableList<FileItem>, scannedPaths: MutableSet<String>) {
        try {
            directory.listFiles()?.forEach { file ->
                val absolutePath = file.absolutePath
                if (scannedPaths.contains(absolutePath)) return@forEach
                scannedPaths.add(absolutePath)

                if (file.isFile && file.length() > 0) {
                    val fileItem = FileItem(
                        file = file,
                        name = file.name,
                        size = file.length(),
                        formattedSize = StorageUtils.formatBytes2(file.length()),
                        type = getFileType(file),
                        lastModified = file.lastModified()
                    )
                    files.add(fileItem)
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    scanDirectory(file, files, scannedPaths)
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun getFileType(file: File): FileType {
        val extension = file.extension.lowercase()
        val path = file.absolutePath.lowercase()

        return when (extension) {
            in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff") -> FileType.IMAGE
            in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm") -> FileType.VIDEO
            in listOf("mp3", "wav", "aac", "flac", "m4a", "ogg", "wma") -> FileType.AUDIO
            in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx") -> FileType.DOCS
            in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> FileType.ZIP
            else -> if (path.contains("download")) FileType.DOWNLOAD else FileType.OTHER
        }
    }

    private fun getFileIcon(type: FileType): Int {
        return when (type) {
            FileType.IMAGE -> R.mipmap.ic_image_file
            FileType.VIDEO -> R.mipmap.ic_video_file
            FileType.AUDIO -> R.mipmap.ic_music_file
            FileType.DOCS -> R.mipmap.ic_doc_file
            FileType.ZIP -> R.mipmap.ic_zip_file
            FileType.DOWNLOAD -> R.mipmap.ic_file
            FileType.OTHER -> R.mipmap.ic_file
        }
    }

    private fun showTypePopup() {
        val types = listOf(
            "All Type" to null,
            "Image" to FileType.IMAGE,
            "Video" to FileType.VIDEO,
            "Audio" to FileType.AUDIO,
            "Docs" to FileType.DOCS,
            "Download" to FileType.DOWNLOAD,
            "Zip" to FileType.ZIP
        )

        showPopupMenu(binding.tvType, types) { selectedType ->
            currentTypeFilter = if (selectedType == null) {
                FileType.values().toList() + null
            } else {
                listOf(selectedType)
            }
            binding.tvType.text = types.find { it.second == selectedType }?.first ?: "All Type"
            applyFilters()
        }
    }

    private fun showSizePopup() {
        val sizes = SizeFilter.values().toList()
        val items = sizes.map { it.displayName to it }

        showPopupMenu(binding.tvSize, items) { selectedSize ->
            currentSizeFilter = selectedSize
            binding.tvSize.text = selectedSize.displayName
            applyFilters()
        }
    }

    private fun showTimePopup() {
        val times = TimeFilter.values().toList()
        val items = times.map { it.displayName to it }

        showPopupMenu(binding.tvTime, items) { selectedTime ->
            currentTimeFilter = selectedTime
            binding.tvTime.text = selectedTime.displayName
            applyFilters()
        }
    }

    private fun <T> showPopupMenu(anchorView: View, items: List<Pair<String, T>>, onItemSelected: (T) -> Unit) {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_filter_menu, null)
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.rv_popup)

        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

        val adapter = PopupAdapter(items) { selectedItem ->
            onItemSelected(selectedItem)
            popup.dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        popup.showAsDropDown(anchorView, 0, 10, Gravity.CENTER_HORIZONTAL)
    }

    private fun applyFilters() {
        filteredFiles.clear()

        val currentTime = System.currentTimeMillis()
        val timeThreshold = if (currentTimeFilter.daysAgo > 0) {
            currentTime - (currentTimeFilter.daysAgo * 24 * 60 * 60 * 1000L)
        } else 0L

        filteredFiles.addAll(allFiles.filter { file ->
            // Type filter
            val typeMatch = currentTypeFilter.contains(null) || currentTypeFilter.contains(file.type)

            // Size filter
            val sizeMatch = file.size >= currentSizeFilter.minSize

            // Time filter
            val timeMatch = currentTimeFilter.daysAgo == 0 || file.lastModified >= timeThreshold

            typeMatch && sizeMatch && timeMatch
        })

        fileAdapter.updateFiles(filteredFiles)
        updateDeleteButtonState()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = filteredFiles.isEmpty()

        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateDeleteButtonState() {
        val selectedCount = filteredFiles.count { it.isSelected }
        val isEmpty = filteredFiles.isEmpty()

        binding.btnCleanNow.text = if (selectedCount > 0) "Delete ($selectedCount)" else "Delete"

        binding.btnCleanNow.isEnabled = !isEmpty && selectedCount > 0

        if (isEmpty || selectedCount == 0) {
            binding.btnCleanNow.alpha = 0.5f
        } else {
            binding.btnCleanNow.alpha = 1.0f
        }
    }

    private fun deleteSelectedFiles() {
        val selectedFiles = filteredFiles.filter { it.isSelected }
        if (selectedFiles.isEmpty()) return

        binding.layoutTip.load.visibility = View.VISIBLE
        binding.layoutTip.tvLoadTip.text = "Cleaning..."
        startRotationAnimation()

        lifecycleScope.launch {
            val totalSize = withContext(Dispatchers.IO) {
                var deletedSize = 0L
                selectedFiles.forEach { fileItem ->
                    try {
                        if (fileItem.file.exists()) {
                            deletedSize += fileItem.file.length()
                            fileItem.file.delete()
                        }
                    } catch (e: Exception) {
                    }
                }
                deletedSize
            }

            delay(1000) // 显示1秒cleaning

            val intent = Intent(this@FileActivity, FinishActivity::class.java)
            intent.putExtra("cleaned_size", totalSize)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRotationAnimation()
    }
}

class FileAdapter(private val onItemClick: (Int) -> Unit) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    private var files = mutableListOf<FileItem>()

    fun updateFiles(newFiles: List<FileItem>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgFileLogo = view.findViewById<android.widget.ImageView>(R.id.img_file_logo)
        val tvFileName = view.findViewById<TextView>(R.id.tv_file_name)
        val tvFileSize = view.findViewById<TextView>(R.id.tv_file_size)
        val ivSelectStatus = view.findViewById<android.widget.CheckBox>(R.id.iv_select_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]

        holder.tvFileName.text = file.name
        holder.tvFileSize.text = file.formattedSize
        holder.ivSelectStatus.isChecked = file.isSelected

        // 设置文件图标
        val iconRes = when (file.type) {
            FileType.IMAGE -> R.mipmap.ic_image_file
            FileType.VIDEO -> R.mipmap.ic_video_file
            FileType.AUDIO -> R.mipmap.ic_music_file
            FileType.DOCS -> R.mipmap.ic_doc_file
            FileType.ZIP -> R.mipmap.ic_zip_file
            FileType.DOWNLOAD -> R.mipmap.ic_file
            FileType.OTHER -> R.mipmap.ic_file
        }
        holder.imgFileLogo.setImageResource(iconRes)

        holder.ivSelectStatus.setBackgroundResource(
            if (file.isSelected) R.mipmap.ic_selete else R.mipmap.ic_not_selete
        )

        holder.ivSelectStatus.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount() = files.size
}

class PopupAdapter<T>(
    private val items: List<Pair<String, T>>,
    private val onItemClick: (T) -> Unit
) : RecyclerView.Adapter<PopupAdapter<T>.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView = view.findViewById<TextView>(R.id.tv_popup_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_popup_menu, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item.first
        holder.textView.setOnClickListener {
            onItemClick(item.second)
        }
    }

    override fun getItemCount() = items.size
}