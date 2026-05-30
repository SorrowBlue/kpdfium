package com.sorrowblue.kpdfium

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.asOutputStream
import java.io.ByteArrayOutputStream

internal class AndroidPdfPage(
    private val docPtr: Long,
    override val pageIndex: Int
) : PdfPage {
    private val pagePtr: Long = PdfiumJni.FPDF_LoadPage(docPtr, pageIndex)

    override val width: Int get() = PdfiumJni.FPDF_GetPageWidthF(pagePtr).toInt()
    override val height: Int get() = PdfiumJni.FPDF_GetPageHeightF(pagePtr).toInt()

    override suspend fun renderToPng(scale: Float): ByteArray = withContext(Dispatchers.IO) {
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        // Create an ARGB_8888 Bitmap representing the canvas
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        // Render directly into the Bitmap's raw memory via NDK C++ (Zero-copy!)
        PdfiumJni.FPDF_RenderPageBitmap(
            pagePtr = pagePtr,
            bitmap = bitmap,
            startX = 0, startY = 0,
            sizeX = targetWidth, sizeY = targetHeight,
            rotate = 0, flags = 0
        )

        // Compress bitmap to PNG byte array
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()

        outputStream.toByteArray()
    }

    override suspend fun renderToPng(scale: Float, sink: Sink) {
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        // Create an ARGB_8888 Bitmap representing the canvas
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        // Render directly into the Bitmap's raw memory via NDK C++ (Zero-copy!)
        PdfiumJni.FPDF_RenderPageBitmap(
            pagePtr = pagePtr,
            bitmap = bitmap,
            startX = 0, startY = 0,
            sizeX = targetWidth, sizeY = targetHeight,
            rotate = 0, flags = 0
        )

        // Compress bitmap to PNG byte array
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink.asOutputStream())
        bitmap.recycle()
    }

    override fun close() {
        PdfiumJni.FPDF_ClosePage(pagePtr)
    }
}
