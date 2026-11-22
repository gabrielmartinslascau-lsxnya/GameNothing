package com.kyant.backdrop.catalog.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

fun ImageBitmap.scale(width: Int, height: Int): ImageBitmap {
    return Bitmap.createScaledBitmap(this.asAndroidBitmap(), width.coerceAtLeast(1), height.coerceAtLeast(1), false)
        .copy(Bitmap.Config.ARGB_8888, false)
        .asImageBitmap()
}
