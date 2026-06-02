@file:OptIn(ExperimentalForeignApi::class)
package com.sorrowblue.kpdfium

import kotlinx.cinterop.*
import com.sorrowblue.kpdfium.native.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class IosPdfDocument(
    private val docPtr: FPDF_DOCUMENT,
    private val source: SeekableSource,
    private val stableRef: StableRef<SeekableSource>,
    private val fileAccess: CPointer<FPDF_FILEACCESS>
) : PdfDocument {
    internal val mutex = Mutex()
    private var isClosed = false

    private fun checkClosed() {
        check(!isClosed) { "PdfDocument is already closed" }
    }
    
    override val pageCount: Int
        get() {
            checkClosed()
            return FPDF_GetPageCount(docPtr)
        }

    override suspend fun getPage(pageIndex: Int): PdfPage = mutex.withLock {
        checkClosed()
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw IndexOutOfBoundsException("Page index $pageIndex is out of bounds (0 ..< $pageCount)")
        }
        IosPdfPage(this, docPtr, pageIndex)
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        FPDF_CloseDocument(docPtr)
        stableRef.dispose()
        nativeHeap.free(fileAccess)
        source.close()
    }
}
