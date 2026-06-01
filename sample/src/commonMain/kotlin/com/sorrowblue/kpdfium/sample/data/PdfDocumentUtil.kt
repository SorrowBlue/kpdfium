package com.sorrowblue.kpdfium.sample.data

import coil3.PlatformContext
import com.sorrowblue.kpdfium.PdfDocument
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.sample.RealSeekableSource
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private var cachePath: PlatformFile? = null
private var pdfDocument: PdfDocument? = null
private val mutex = Mutex()

suspend fun getPdfDocument(context: PlatformContext, file: PlatformFile): PdfDocument =
    mutex.withLock {
        suspend fun reOpenDocument() =
            PdfExtractor.openDocument(RealSeekableSource(context, file)).also {
                pdfDocument = it
            }
        pdfDocument?.let {
            if (cachePath != file) {
                cachePath = file
                reOpenDocument()
            } else {
                it
            }
        } ?: reOpenDocument()
    }

internal fun releasePdfDocument() {
    pdfDocument?.closeQuietly()
    pdfDocument = null
    cachePath = null
}
