package com.sorrowblue.kpdfium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.asOutputStream
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

internal class JvmPdfPage(
    private val document: JvmPdfDocument,
    private val docPtr: Long,
    override val pageIndex: Int
) : PdfPage {
    private val pagePtr: Long = PdfiumJni.FPDF_LoadPage(docPtr, pageIndex).also {
        if (it == 0L) {
            throw IllegalArgumentException("Failed to load PDF page at index $pageIndex. The document might be corrupted or the page index is invalid.")
        }
    }

    override val width: Int get() = PdfiumJni.FPDF_GetPageWidthF(pagePtr).toInt()
    override val height: Int get() = PdfiumJni.FPDF_GetPageHeightF(pagePtr).toInt()

    override suspend fun renderToPng(scale: Float): ByteArray = document.mutex.withLock {
        withContext(Dispatchers.IO) {
            val targetWidth = (width * scale).toInt()
            val targetHeight = (height * scale).toInt()

            // 1. Allocate a Direct ByteBuffer (crucial for zero-copy JNI address retrieval)
            // Stride is targetWidth * 4 bytes (BGRA format)
            val bufferSize = targetWidth * targetHeight * 4
            val byteBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
                order(ByteOrder.nativeOrder())
            }

            // 2. Render Page into the Direct ByteBuffer via native C++
            PdfiumJni.FPDF_RenderPageBitmapJvm(
                pagePtr = pagePtr,
                byteBuffer = byteBuffer,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                rotate = 0,
                flags = 0
            )

            // 3. Construct BufferedImage directly from the ByteBuffer pixels (Zero-copy pixel transfer)
            val bufferedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)

            // Fast buffer copy using DataBufferInt
            val pixels = (bufferedImage.raster.dataBuffer as DataBufferInt).data
            val intBuffer = byteBuffer.asIntBuffer()
            intBuffer.get(pixels)

            // 4. Compress BufferedImage into standard PNG bytes
            val baos = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "png", baos)
            baos.toByteArray()
        }
    }

    override suspend fun renderToPng(scale: Float, sink: Sink): Unit = document.mutex.withLock {
        withContext(Dispatchers.IO) {
            val targetWidth = (width * scale).toInt()
            val targetHeight = (height * scale).toInt()

            // 1. Allocate a Direct ByteBuffer (crucial for zero-copy JNI address retrieval)
            // Stride is targetWidth * 4 bytes (BGRA format)
            val bufferSize = targetWidth * targetHeight * 4
            val byteBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
                order(ByteOrder.nativeOrder())
            }

            // 2. Render Page into the Direct ByteBuffer via native C++
            PdfiumJni.FPDF_RenderPageBitmapJvm(
                pagePtr = pagePtr,
                byteBuffer = byteBuffer,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                rotate = 0,
                flags = 0
            )

            // 3. Construct BufferedImage directly from the ByteBuffer pixels (Zero-copy pixel transfer)
            val bufferedImage =
                BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)

            // Fast buffer copy using DataBufferInt
            val pixels = (bufferedImage.raster.dataBuffer as DataBufferInt).data
            val intBuffer = byteBuffer.asIntBuffer()
            intBuffer.get(pixels)

            // 4. Compress BufferedImage into standard PNG bytes
            ImageIO.write(bufferedImage, "png", sink.asOutputStream())
        }
    }

    override fun close() {
        PdfiumJni.FPDF_ClosePage(pagePtr)
    }
}
