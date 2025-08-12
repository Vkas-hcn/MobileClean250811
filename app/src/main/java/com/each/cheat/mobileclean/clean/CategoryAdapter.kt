package com.each.cheat.mobileclean.clean

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.each.cheat.mobileclean.R
import kotlinx.coroutines.*

class CategoryAdapter(
    private val categories: MutableList<JunkCategory>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private val TAG = "CategoryAdapter"
    private val fileAdapterCache = mutableMapOf<Int, FileAdapter>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvSize: TextView = itemView.findViewById(R.id.tv_size)
        val imgSelect: ImageView = itemView.findViewById(R.id.img_select)
        val imgInstruct: ImageView = itemView.findViewById(R.id.img_instruct)
        val llCategory: View = itemView.findViewById(R.id.ll_category)
        val rvItemFile: RecyclerView = itemView.findViewById(R.id.rv_item_file)

        init {
            // 为每个ViewHolder预设布局管理器和优化设置
            if (rvItemFile.layoutManager == null) {
                val layoutManager = LinearLayoutManager(itemView.context)
                rvItemFile.layoutManager = layoutManager
                // 设置RecyclerView优化参数
                rvItemFile.setHasFixedSize(true)
                rvItemFile.setItemViewCacheSize(20) // 增加缓存大小
                rvItemFile.isNestedScrollingEnabled = false // 禁用嵌套滚动
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]

        Log.d(TAG, "绑定分类 $position: ${category.name}, 文件数: ${category.files.size}")

        // 基本信息设置
        holder.tvTitle.text = category.name

        val sizeText = if (category.files.isEmpty()) {
            "0 files • 0 KB"
        } else {
            "${category.getFileCountInfo()} • ${category.getTotalSizeInMB()}"
        }
        holder.tvSize.text = sizeText

        // 设置图标
        setSelectIcon(holder.imgSelect, category.isSelected && category.files.isNotEmpty())
        setExpandIcon(holder.imgInstruct, category.isExpanded)
        setupUIForCategory(holder, category)

        // 处理文件列表的设置 - 优化性能
        handleFileListOptimized(holder, category, position)

        // 点击事件处理
        setupClickListeners(holder, category, position)
    }

    private fun handleFileListOptimized(
        holder: CategoryViewHolder,
        category: JunkCategory,
        position: Int
    ) {
        if (category.isExpanded && category.files.isNotEmpty()) {
            // 显示文件列表容器
            holder.rvItemFile.visibility = View.VISIBLE

            // 异步加载文件列表，避免阻塞主线程
            coroutineScope.launch {
                val fileAdapter = getOrCreateFileAdapterAsync(position, category)
                withContext(Dispatchers.Main) {
                    if (holder.rvItemFile.adapter != fileAdapter) {
                        holder.rvItemFile.adapter = fileAdapter
                    }
                }
            }
        } else {
            // 隐藏文件列表
            holder.rvItemFile.visibility = View.GONE
            // 清除适配器以释放内存，但保留在缓存中
            holder.rvItemFile.adapter = null
        }
    }

    private suspend fun getOrCreateFileAdapterAsync(position: Int, category: JunkCategory): FileAdapter = withContext(Dispatchers.IO) {
        // 检查缓存
        fileAdapterCache[position]?.let { existingAdapter ->
            // 在后台线程更新数据
            existingAdapter.updateFilesAsync(category.files)
            return@withContext existingAdapter
        }

        // 创建新适配器
        val newAdapter = FileAdapter(category.files.toMutableList()) {
            // 更新分类的选择状态
            coroutineScope.launch(Dispatchers.Main) {
                category.updateSelectionState()
                // 通知UI更新
                notifyItemChanged(position, "selection_changed")
                onSelectionChanged()
            }
        }

        fileAdapterCache[position] = newAdapter
        return@withContext newAdapter
    }

    private fun setupClickListeners(
        holder: CategoryViewHolder,
        category: JunkCategory,
        position: Int
    ) {
        // 展开/折叠点击
        holder.llCategory.setOnClickListener {
            if (category.files.isNotEmpty()) {
                category.isExpanded = !category.isExpanded
                Log.d(TAG, "${category.name} 展开状态: ${category.isExpanded}")

                // 使用payload避免完整重绑定
                notifyItemChanged(position, "expand_changed")
            } else {
                category.isExpanded = !category.isExpanded
                notifyItemChanged(position, "expand_changed")
                Toast.makeText(holder.itemView.context,
                    "No junk files found in ${category.name}", Toast.LENGTH_SHORT).show()
            }
        }

        // 选择点击
        holder.imgSelect.setOnClickListener {
            if (category.files.isNotEmpty()) {
                category.isSelected = !category.isSelected

                // 在后台线程批量更新文件选择状态
                coroutineScope.launch(Dispatchers.IO) {
                    category.files.forEach { it.isSelected = category.isSelected }
                    category.updateSelectionState()

                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "${category.name} 选中状态: ${category.isSelected}")
                        notifyItemChanged(position, "selection_changed")
                        onSelectionChanged()

                        // 如果文件列表正在显示，也更新文件适配器
                        if (category.isExpanded) {
                            fileAdapterCache[position]?.notifyDataSetChanged()
                        }
                    }
                }
            } else {
                Toast.makeText(holder.itemView.context,
                    "No files to select in ${category.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 支持部分更新，避免完整重绑定
    override fun onBindViewHolder(
        holder: CategoryViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val category = categories[position]

        for (payload in payloads) {
            when (payload) {
                "selection_changed" -> {
                    setSelectIcon(holder.imgSelect, category.isSelected && category.files.isNotEmpty())
                    setupUIForCategory(holder, category)

                    // 更新文件数显示
                    val sizeText = if (category.files.isEmpty()) {
                        "0 files • 0 KB"
                    } else {
                        "${category.getFileCountInfo()} • ${category.getTotalSizeInMB()}"
                    }
                    holder.tvSize.text = sizeText
                }
                "expand_changed" -> {
                    setExpandIcon(holder.imgInstruct, category.isExpanded)
                    handleFileListOptimized(holder, category, position)
                }
                "files_updated" -> {
                    // 实时文件更新
                    val sizeText = if (category.files.isEmpty()) {
                        "0 files • 0 KB"
                    } else {
                        "${category.getFileCountInfo()} • ${category.getTotalSizeInMB()}"
                    }
                    holder.tvSize.text = sizeText
                    setupUIForCategory(holder, category)

                    // 如果正在展开状态，更新文件适配器
                    if (category.isExpanded && category.files.isNotEmpty()) {
                        coroutineScope.launch {
                            val fileAdapter = getOrCreateFileAdapterAsync(position, category)
                            withContext(Dispatchers.Main) {
                                if (holder.rvItemFile.adapter != fileAdapter) {
                                    holder.rvItemFile.adapter = fileAdapter
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupUIForCategory(holder: CategoryViewHolder, category: JunkCategory) {
        if (category.files.isNotEmpty()) {
            holder.imgSelect.alpha = 1.0f
            holder.imgInstruct.alpha = 1.0f
            holder.tvTitle.alpha = 1.0f
            holder.tvSize.setTextColor(0xFF888A8F.toInt())
            holder.imgSelect.visibility = View.VISIBLE
        } else {
            holder.imgSelect.alpha = 0.3f
            holder.imgInstruct.alpha = 0.6f
            holder.tvTitle.alpha = 0.7f
            holder.tvSize.setTextColor(0xFFBBBBBB.toInt())
            holder.imgSelect.visibility = View.VISIBLE
            holder.imgSelect.alpha = 0.2f
        }
    }

    override fun getItemCount(): Int {
        return categories.size
    }

    // 实时更新分类，性能优化版本
    fun updateCategoryFiles(categoryName: String, newFile: JunkFile) {
        val categoryIndex = categories.indexOfFirst { it.name == categoryName }
        if (categoryIndex != -1) {
            val category = categories[categoryIndex]
            if (category.files.size < JunkCategory.MAX_FILES_PER_CATEGORY) {
                category.files.add(newFile)
                // 使用payload更新，避免完整重绑定
                notifyItemChanged(categoryIndex, "files_updated")
            }
        }
    }

    fun updateCategories(newCategories: List<JunkCategory>) {
        Log.d(TAG, "updateCategories: 新分类数=${newCategories.size}")

        // 清理缓存
        fileAdapterCache.clear()

        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()

        categories.forEachIndexed { index, category ->
            Log.d(TAG, "分类 $index: ${category.name} (${category.files.size} files)")
        }
    }

    // 清理资源
    fun cleanup() {
        coroutineScope.cancel()
        fileAdapterCache.clear()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        cleanup()
    }

    private fun setSelectIcon(imageView: ImageView, isSelected: Boolean) {
        try {
            val resourceId = if (isSelected) {
                try {
                    R.mipmap.ic_selete
                } catch (e: Exception) {
                    android.R.drawable.checkbox_on_background
                }
            } else {
                try {
                    R.mipmap.ic_not_selete
                } catch (e: Exception) {
                    android.R.drawable.checkbox_off_background
                }
            }
            imageView.setImageResource(resourceId)
        } catch (e: Exception) {
            Log.w(TAG, "设置选中图标失败", e)
            imageView.setImageResource(
                if (isSelected) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )
        }
    }

    private fun setExpandIcon(imageView: ImageView, isExpanded: Boolean) {
        try {
            val resourceId = if (isExpanded) {
                try {
                    R.mipmap.ic_retract
                } catch (e: Exception) {
                    android.R.drawable.arrow_up_float
                }
            } else {
                try {
                    R.mipmap.ic_expand
                } catch (e: Exception) {
                    android.R.drawable.arrow_down_float
                }
            }
            imageView.setImageResource(resourceId)
        } catch (e: Exception) {
            Log.w(TAG, "设置展开图标失败", e)
            imageView.setImageResource(
                if (isExpanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )
        }
    }
}

// 优化后的FileAdapter
class FileAdapter(
    private val files: MutableList<JunkFile>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private val TAG = "FileAdapter"

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFileName: TextView = itemView.findViewById(R.id.tv_file_name)
        val ivSelectStatus: View = itemView.findViewById(R.id.iv_select_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clean_detail, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        if (position >= files.size) return // 安全检查

        val file = files[position]
        val fileInfo = "${file.name}\n${file.getSizeInMB()}"
        holder.tvFileName.text = fileInfo
        setFileSelectIcon(holder.ivSelectStatus, file.isSelected)

        val clickListener = View.OnClickListener {
            file.isSelected = !file.isSelected
            Log.d(TAG, "文件 ${file.name} 选中状态: ${file.isSelected}")
            holder.ivSelectStatus.isSelected = file.isSelected
            setFileSelectIcon(holder.ivSelectStatus, file.isSelected)
            onSelectionChanged()
        }

        holder.itemView.setOnClickListener(clickListener)
        holder.ivSelectStatus.setOnClickListener(clickListener)
    }

    override fun getItemCount(): Int = files.size

    // 同步更新文件列表
    fun updateFiles(newFiles: List<JunkFile>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    // 异步更新文件列表，提高性能
    suspend fun updateFilesAsync(newFiles: List<JunkFile>) = withContext(Dispatchers.IO) {
        val filesToAdd = newFiles.toList() // 创建副本避免并发修改
        withContext(Dispatchers.Main) {
            files.clear()
            files.addAll(filesToAdd)
            notifyDataSetChanged()
        }
    }

    private fun setFileSelectIcon(view: View, isSelected: Boolean) {
        try {
            val resourceId = if (isSelected) {
                try {
                    R.mipmap.ic_selete
                } catch (e: Exception) {
                    android.R.drawable.checkbox_on_background
                }
            } else {
                try {
                    R.mipmap.ic_not_selete
                } catch (e: Exception) {
                    android.R.drawable.checkbox_off_background
                }
            }
            view.setBackgroundResource(resourceId)
        } catch (e: Exception) {
            view.setBackgroundResource(
                if (isSelected) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )
        }
    }
}