package com.sorrowblue.kpdfium

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AndroidPdfDocument(private val docPtr: Long, private val source: SeekableSource) :
    PdfDocument {
    internal val mutex = Mutex()
    private var isClosed = false

    private fun checkClosed() {
        check(!isClosed) { "PdfDocument is already closed" }
    }

    override val pageCount: Int
        get() {
            checkClosed()
            return PdfiumJni.FPDF_GetPageCount(docPtr)
        }

    override suspend fun getPage(pageIndex: Int): PdfPage = mutex.withLock {
        checkClosed()
        if (pageIndex !in 0..<pageCount) {
            throw IndexOutOfBoundsException(
                "Page index $pageIndex is out of bounds (0 ..< $pageCount)"
            )
        }
        AndroidPdfPage(this, docPtr, pageIndex)
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        PdfiumJni.FPDF_CloseDocument(docPtr)
        source.close() // Close the underlying seekable source
    }
}
