package com.sorrowblue.kpdfium

/**
 * Interface representing a loaded PDF document.
 * This class implements [AutoCloseable] to ensure that native files or memory streams
 * allocated for the PDF are released when the document is no longer needed.
 */
public interface PdfDocument : AutoCloseable {
    /**
     * The total number of pages in this document.
     */
    public val pageCount: Int

    /**
     * Retrieves a specific page from the document.
     *
     * @param pageIndex The 0-based index of the page (must be in range `0 ..< pageCount`).
     * @return The [PdfPage] instance.
     */
    public suspend fun getPage(pageIndex: Int): PdfPage
}
