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
            if (rvItemFile.layoutManager == null) {
                val layoutManager = LinearLayoutManager(itemView.context)
                rvItemFile.layoutManager = layoutManager
                rvItemFile.setHasFixedSize(true)
                rvItemFile.setItemViewCacheSize(20)
                rvItemFile.isNestedScrollingEnabled = false
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

        holder.tvTitle.text = category.name

        val sizeText = if (category.files.isEmpty()) {
            "0 files • 0 KB"
        } else {
            "${category.getFileCountInfo()} • ${category.getTotalSizeInMB()}"
        }
        holder.tvSize.text = sizeText

        setSelectIcon(holder.imgSelect, category.isSelected && category.files.isNotEmpty())
        setExpandIcon(holder.imgInstruct, category.isExpanded)
        setupUIForCategory(holder, category)

        handleFileListOptimized(holder, category, position)

        setupClickListeners(holder, category, position)
    }

    private fun handleFileListOptimized(
        holder: CategoryViewHolder,
        category: JunkCategory,
        position: Int
    ) {
        if (category.isExpanded && category.files.isNotEmpty()) {
            holder.rvItemFile.visibility = View.VISIBLE

            coroutineScope.launch {
                val fileAdapter = getOrCreateFileAdapterAsync(position, category)
                withContext(Dispatchers.Main) {
                    if (holder.rvItemFile.adapter != fileAdapter) {
                        holder.rvItemFile.adapter = fileAdapter
                    }
                }
            }
        } else {
            holder.rvItemFile.visibility = View.GONE
            holder.rvItemFile.adapter = null
        }
    }

    private suspend fun getOrCreateFileAdapterAsync(position: Int, category: JunkCategory): FileAdapter = withContext(Dispatchers.IO) {
        fileAdapterCache[position]?.let { existingAdapter ->
            existingAdapter.updateFilesAsync(category.files)
            return@withContext existingAdapter
        }

        val newAdapter = FileAdapter(category.files.toMutableList()) {
            coroutineScope.launch(Dispatchers.Main) {
                category.updateSelectionState()
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
        holder.llCategory.setOnClickListener {
            if (category.files.isNotEmpty()) {
                category.isExpanded = !category.isExpanded
                Log.d(TAG, "${category.name} 展开状态: ${category.isExpanded}")

                notifyItemChanged(position, "expand_changed")
            } else {
                category.isExpanded = !category.isExpanded
                notifyItemChanged(position, "expand_changed")
                Toast.makeText(holder.itemView.context,
                    "No junk files found in ${category.name}", Toast.LENGTH_SHORT).show()
            }
        }

        holder.imgSelect.setOnClickListener {
            if (category.files.isNotEmpty()) {
                category.isSelected = !category.isSelected

                coroutineScope.launch(Dispatchers.IO) {
                    category.files.forEach { it.isSelected = category.isSelected }
                    category.updateSelectionState()

                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "${category.name} 选中状态: ${category.isSelected}")
                        notifyItemChanged(position, "selection_changed")
                        onSelectionChanged()

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
                    val sizeText = if (category.files.isEmpty()) {
                        "0 files • 0 KB"
                    } else {
                        "${category.getFileCountInfo()} • ${category.getTotalSizeInMB()}"
                    }
                    holder.tvSize.text = sizeText
                    setupUIForCategory(holder, category)

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
            imageView.setImageResource(
                if (isExpanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )
        }
    }
}

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
        if (position >= files.size) return

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



    suspend fun updateFilesAsync(newFiles: List<JunkFile>) = withContext(Dispatchers.IO) {
        val filesToAdd = newFiles.toList()
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