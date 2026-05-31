package com.sorrowblue.kpdfium.sample

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.io.Source

expect fun ByteArray.toImageBitmap(): ImageBitmap

expect fun Source.asImageBitmap(): ImageBitmap
