package com.sorrowblue.kpdfium

/**
 * Entry point for loading PDF documents from random-access seekable sources.
 */
public expect object PdfExtractor {
    /**
     * Asynchronously opens a PDF document from a seekable source.
     *
     * @param source The seekable, random-access source containing the PDF data.
     * @return The [PdfDocument] instance representing the parsed PDF.
     */
    public suspend fun openDocument(source: SeekableSource): PdfDocument
}

/**
 * Convenience extension function to asynchronously open a PDF document from its raw binary bytes.
 * This internally wraps the bytes inside a [ByteArraySeekableSource].
 *
 * @param pdfBytes The binary contents of the PDF file.
 * @return The [PdfDocument] instance representing the parsed PDF.
 */
public suspend fun PdfExtractor.openDocument(pdfBytes: ByteArray): PdfDocument =
    openDocument(ByteArraySeekableSource(pdfBytes))
