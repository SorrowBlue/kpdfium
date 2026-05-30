package com.sorrowblue.kpdfium

internal class AndroidPdfDocument(
    private val docPtr: Long,
    private val source: SeekableSource
) : PdfDocument {
    override val pageCount: Int get() = PdfiumJni.FPDF_GetPageCount(docPtr)

    override suspend fun getPage(pageIndex: Int): PdfPage {
        if (pageIndex !in 0..<pageCount) {
            throw IndexOutOfBoundsException("Page index $pageIndex is out of bounds (0 ..< $pageCount)")
        }
        return AndroidPdfPage(docPtr, pageIndex)
    }

    override fun close() {
        PdfiumJni.FPDF_CloseDocument(docPtr)
        source.close() // Close the underlying seekable source
    }
}
