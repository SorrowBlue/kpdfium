package com.sorrowblue.kpdfium

/**
 * Interface representing a single rendered page from a PDF document.
 * This class implements [AutoCloseable] to ensure that native resources allocated
 * for the rendering engine are properly freed after rendering.
 */
public interface PdfPage : AutoCloseable {
    /**
     * The 0-based index of this page in the PDF document.
     */
    public val pageIndex: Int

    /**
     * The width of the page in PDF points (72 points = 1 inch).
     */
    public val width: Int

    /**
     * The height of the page in PDF points (72 points = 1 inch).
     */
    public val height: Int

    /**
     * Renders this PDF page to a PNG formatted image byte array.
     *
     * @param scale Resolution multiplier. 1.0f is standard resolution (72 DPI).
     *              2.0f is high-resolution (144 DPI) which yields a sharper rendering.
     * @return The rendered image bytes in PNG format.
     */
    public suspend fun renderToPng(scale: Float = 2.0f): ByteArray
}
