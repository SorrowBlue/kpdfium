@file:OptIn(ExperimentalForeignApi::class)
package com.sorrowblue.kpdfium

import kotlinx.cinterop.*
import com.sorrowblue.kpdfium.native.*

internal class IosPdfDocument(
    private val docPtr: FPDF_DOCUMENT,
    private val source: SeekableSource,
    private val stableRef: StableRef<SeekableSource>,
    private val fileAccess: CPointer<FPDF_FILEACCESS>
) : PdfDocument {
    
    override val pageCount: Int
        get() = FPDF_GetPageCount(docPtr)

    override suspend fun getPage(pageIndex: Int): PdfPage {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw IndexOutOfBoundsException("Page index $pageIndex is out of bounds (0 ..< $pageCount)")
        }
        return IosPdfPage(docPtr, pageIndex)
    }

    override fun close() {
        FPDF_CloseDocument(docPtr)
        stableRef.dispose()
        nativeHeap.free(fileAccess)
        source.close()
    }
}
