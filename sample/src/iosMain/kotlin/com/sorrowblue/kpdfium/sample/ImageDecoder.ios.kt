package com.sorrowblue.kpdfium.sample

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.io.Source
import kotlinx.io.readByteArray
import org.jetbrains.skia.Image

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val skiaImage = Image.makeFromEncoded(this)
    return skiaImage.toComposeImageBitmap()
}

actual fun Source.asImageBitmap(): ImageBitmap = readByteArray().toImageBitmap()
