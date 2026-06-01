package com.sorrowblue.kpdfium

import kotlinx.io.Sink

/** Standard resolution (72 DPI). 1 PDF point maps to 1 pixel. Suitable for thumbnails. */
public const val DPI_STANDARD: Int = 72

/** High resolution (144 DPI), equivalent to 2x scale. Suitable for HiDPI/Retina screens. */
public const val DPI_HIGH: Int = 144

/** Very high resolution (300 DPI). Suitable for high-density screens or standard printing. */
public const val DPI_VERY_HIGH: Int = 300

/** Ultra high resolution (600 DPI). Suitable for high-quality printing. */
public const val DPI_ULTRA: Int = 600

/** Minimum quality for lossy formats (0%). */
public const val QUALITY_MAX: Int = 100

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
     * Renders this PDF page to an image byte array in the specified resolution (DPI), format, and quality.
     *
     * @param dpi Resolution in Dots Per Inch (DPI). Default is [DPI_HIGH] (144 DPI). Standard resolution is [DPI_STANDARD] (72 DPI).
     * @param format Output image format ([ImageFormat]). Default is [ImageFormat.PNG].
     * @param quality Compression quality (0 to 100). Applied in lossy formats like JPEG, and ignored in lossless formats like PNG. Default is 100.
     * @return The rendered image bytes in the specified format.
     * @throws IllegalArgumentException If quality is not in range 0..100, or DPI is 0 or less.
     * @throws UnsupportedOperationException If the requested format is not supported on the current platform.
     */
    public suspend fun render(
        dpi: Int = DPI_HIGH,
        format: ImageFormat = ImageFormat.PNG,
        quality: Int = QUALITY_MAX
    ): ByteArray

    /**
     * Renders this PDF page to an image byte array in the specified resolution (DPI), format, and quality, and writes it to a [Sink].
     *
     * @param dpi Resolution in Dots Per Inch (DPI). Default is [DPI_HIGH] (144 DPI).
     * @param format Output image format ([ImageFormat]). Default is [ImageFormat.PNG].
     * @param quality Compression quality (0 to 100). Default is 100.
     * @param sink The target sink to write the image bytes.
     * @throws IllegalArgumentException If quality is not in range 0..100, or DPI is 0 or less.
     * @throws UnsupportedOperationException If the requested format is not supported on the current platform.
     */
    public suspend fun render(
        dpi: Int = DPI_HIGH,
        format: ImageFormat = ImageFormat.PNG,
        quality: Int = QUALITY_MAX,
        sink: Sink
    )
}
