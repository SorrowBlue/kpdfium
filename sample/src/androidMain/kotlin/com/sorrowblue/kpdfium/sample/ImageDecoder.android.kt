package com.sorrowblue.kpdfium.sample

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size)
        ?: throw IllegalArgumentException("Failed to decode ImageBitmap bytes on Android")
    return bitmap.asImageBitmap()
}

actual fun Source.asImageBitmap(): ImageBitmap {
    val bitmap = BitmapFactory.decodeStream(asInputStream())
        ?: throw IllegalArgumentException("Failed to decode ImageBitmap bytes on Android")
    return bitmap.asImageBitmap()
}
