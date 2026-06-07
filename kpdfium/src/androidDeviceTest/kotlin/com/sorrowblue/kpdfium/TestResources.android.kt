package com.sorrowblue.kpdfium

import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException

public actual val isResourceLoadingSupported: Boolean = true

public actual val isWebpSupported: Boolean = true

public actual fun loadTestPdf(fileName: String): ByteArray? {

    val context = InstrumentationRegistry.getInstrumentation().context
    return try {
        context.assets.open(fileName).use { it.readBytes() }
    } catch (e: IOException) {
        null
    }
}

public actual fun loadTestPdfSource(fileName: String): SeekableSource? {
    val bytes = loadTestPdf(fileName) ?: return null
    return ByteArraySeekableSource(bytes)
}
