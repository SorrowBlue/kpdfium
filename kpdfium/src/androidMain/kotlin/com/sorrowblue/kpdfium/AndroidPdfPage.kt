package com.sorrowblue.kpdfium

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Sink
import kotlinx.io.asOutputStream

internal class AndroidPdfPage(
    private val document: AndroidPdfDocument,
    private val docPtr: Long,
    override val pageIndex: Int
) : PdfPage {
    private val pagePtr: Long = PdfiumJni.FPDF_LoadPage(docPtr, pageIndex).also {
        require(it != 0L) {
            """Failed to load PDF page at index $pageIndex. The document might be corrupted or the page index is invalid."""
        }
    }

    override val width: Int get() = PdfiumJni.FPDF_GetPageWidthF(pagePtr).toInt()
    override val height: Int get() = PdfiumJni.FPDF_GetPageHeightF(pagePtr).toInt()

    override suspend fun render(dpi: Int, format: ImageFormat, quality: Int): ByteArray =
        document.mutex.withLock {
            require(dpi > 0) { "DPI must be greater than 0" }
            require(quality in 0..QUALITY_MAX) { "Quality must be between 0 and 100" }

            val scale = dpi / 72.0f
            val targetWidth = (width * scale).toInt()
            val targetHeight = (height * scale).toInt()

            // Create an ARGB_8888 Bitmap representing the canvas
            val bitmap = createBitmap(targetWidth, targetHeight)
            bitmap.eraseColor(Color.WHITE)

            // Render directly into the Bitmap's raw memory via NDK C++ (Zero-copy!)
            PdfiumJni.FPDF_RenderPageBitmap(
                pagePtr = pagePtr,
                bitmap = bitmap,
                startX = 0,
                startY = 0,
                sizeX = targetWidth,
                sizeY = targetHeight,
                rotate = 0,
                flags = 0
            )

            val compressFormat = when (format) {
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
            }

            // Compress bitmap to byte array
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(compressFormat, quality, outputStream)
            bitmap.recycle()

            outputStream.toByteArray()
        }

    override suspend fun render(dpi: Int, format: ImageFormat, quality: Int, sink: Sink): Unit =
        document.mutex.withLock {
            require(dpi > 0) { "DPI must be greater than 0" }
            require(quality in 0..QUALITY_MAX) { "Quality must be between 0 and 100" }

            val scale = dpi / 72.0f
            val targetWidth = (width * scale).toInt()
            val targetHeight = (height * scale).toInt()

            // Create an ARGB_8888 Bitmap representing the canvas
            val bitmap = createBitmap(targetWidth, targetHeight)
            bitmap.eraseColor(Color.WHITE)

            // Render directly into the Bitmap's raw memory via NDK C++ (Zero-copy!)
            PdfiumJni.FPDF_RenderPageBitmap(
                pagePtr = pagePtr,
                bitmap = bitmap,
                startX = 0,
                startY = 0,
                sizeX = targetWidth,
                sizeY = targetHeight,
                rotate = 0,
                flags = 0
            )

            val compressFormat = when (format) {
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
            }

            // Compress bitmap to Sink
            bitmap.compress(compressFormat, quality, sink.asOutputStream())
            bitmap.recycle()
        }

    override fun close() {
        PdfiumJni.FPDF_ClosePage(pagePtr)
    }
}
