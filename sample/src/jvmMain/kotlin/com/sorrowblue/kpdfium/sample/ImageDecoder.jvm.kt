package com.sorrowblue.kpdfium.sample

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.io.Source
import kotlinx.io.asInputStream

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val image = ImageIO.read(ByteArrayInputStream(this))
        ?: throw IllegalArgumentException("Failed to decode ImageBitmap bytes on JVM")
    return image.toComposeImageBitmap()
}

actual fun Source.asImageBitmap(): ImageBitmap {
    val image = ImageIO.read(asInputStream())
        ?: throw IllegalArgumentException("Failed to decode ImageBitmap bytes on JVM")
    return image.toComposeImageBitmap()
}
