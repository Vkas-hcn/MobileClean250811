package com.each.cheat.mobileclean.photo

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.each.cheat.mobileclean.R
import com.each.cheat.mobileclean.oht.MobileCleanApplication
import java.io.File

object ImageLoader {

    fun loadImage(imageView: ImageView, imagePath: String) {
        val request = ImageRequest.Builder(MobileCleanApplication.Companion.instance)
            .data(File(imagePath))
            .target(imageView)
            .size(Size(105, 105))
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .fallback(R.mipmap.ic_launcher)
            .crossfade(true)
            .crossfade(200)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .listener(
                onStart = { request ->
                },
                onSuccess = { request, result ->
                },
                onError = { request, result ->
                    result.throwable.printStackTrace()
                }
            )
            .build()

        ImageLoader(imageView.context).enqueue(request)
    }

}