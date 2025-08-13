package com.each.cheat.mobileclean.photo

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.each.cheat.mobileclean.R
// 移除Glide依赖，使用自定义ImageLoader
import com.each.cheat.mobileclean.databinding.ItemPhotoDateBinding
import com.each.cheat.mobileclean.databinding.ItemPhotoImgBinding

/**
 * 照片日期分组适配器
 */
class PhotoDateAdapter(
    private val onPhotoSelectionChanged: () -> Unit
) : RecyclerView.Adapter<PhotoDateAdapter.DateViewHolder>() {

    private val dateGroups = mutableListOf<PhotoDateGroup>()

    fun updateData(newGroups: List<PhotoDateGroup>) {
        dateGroups.clear()
        dateGroups.addAll(newGroups)
        notifyDataSetChanged()
    }

    fun getDateGroups(): List<PhotoDateGroup> = dateGroups

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val binding = ItemPhotoDateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(dateGroups[position])
    }

    override fun getItemCount(): Int = dateGroups.size

    inner class DateViewHolder(
        private val binding: ItemPhotoDateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val photoAdapter = PhotoAdapter { photo ->
            photo.isSelected = !photo.isSelected
            val dateGroup = dateGroups[adapterPosition]
            dateGroup.updateSelectAllState()
            notifyItemChanged(adapterPosition)
            onPhotoSelectionChanged()
        }

        init {
            binding.rvItemFile.apply {
                layoutManager = GridLayoutManager(context, 3)
                adapter = photoAdapter
            }
        }

        fun bind(dateGroup: PhotoDateGroup) {
            binding.tvDate.text = dateGroup.date

            // 设置日期全选图标
            val selectIcon = if (dateGroup.isAllSelectedImg) {
                R.mipmap.ic_selected_yuan
            } else {
                R.mipmap.ic_dis_selected_yuan
            }
            binding.root.findViewById<ImageView>(R.id.img_date_select)?.apply {
                setImageResource(selectIcon)
                setOnClickListener {
                    dateGroup.setAllSelected(!dateGroup.isAllSelectedImg)
                    notifyItemChanged(adapterPosition)
                    photoAdapter.notifyDataSetChanged()
                    onPhotoSelectionChanged()
                }
            }

            // 更新照片列表
            photoAdapter.updatePhotos(dateGroup.photos)
        }
    }
}

class PhotoAdapter(
    private val onPhotoClick: (PhotoInfo) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<PhotoInfo>()

    fun updatePhotos(newPhotos: List<PhotoInfo>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoImgBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(
        private val binding: ItemPhotoImgBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoInfo) {
            // 加载图片
            ImageLoader.loadImage(binding.imgData, photo.path)

            // 设置选中状态图标
            val selectIcon = if (photo.isSelected) {
                R.mipmap.ic_selected_yuan
            } else {
                R.mipmap.ic_dis_selected_yuan
            }
            binding.root.findViewById<ImageView>(R.id.img_select)?.apply {
                setImageResource(selectIcon)
            }

            // 设置点击事件
            binding.root.setOnClickListener {
                onPhotoClick(photo)
            }
        }
    }
}