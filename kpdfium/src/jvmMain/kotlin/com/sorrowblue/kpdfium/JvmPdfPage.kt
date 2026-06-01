package com.sorrowblue.kpdfium

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Sink
import kotlinx.io.asOutputStream

internal class JvmPdfPage(
    private val document: JvmPdfDocument,
    private val docPtr: Long,
    override val pageIndex: Int
) : PdfPage {
    private val pagePtr: Long = PdfiumJni.FPDF_LoadPage(docPtr, pageIndex).also {
        require(it != 0L) {
            "Failed to load PDF page at index $pageIndex. The document might be corrupted or the page index is invalid."
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
            val bufferedImage = BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_ARGB
            )

            // Fast buffer copy using DataBufferInt
            val pixels = (bufferedImage.raster.dataBuffer as DataBufferInt).data
            val intBuffer = byteBuffer.asIntBuffer()
            intBuffer.get(pixels)

            // 4. Compress BufferedImage into byte array
            val baos = ByteArrayOutputStream()
            writeToStream(bufferedImage, format, quality, baos)
            baos.toByteArray()
        }

    override suspend fun render(dpi: Int, format: ImageFormat, quality: Int, sink: Sink): Unit =
        document.mutex.withLock {
            require(dpi > 0) { "DPI must be greater than 0" }
            require(quality in 0..QUALITY_MAX) { "Quality must be between 0 and 100" }
            val scale = dpi / DPI_STANDARD.toFloat()
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

            // 4. Compress BufferedImage into Sink
            writeToStream(bufferedImage, format, quality, sink.asOutputStream())
        }

    private fun writeToStream(
        bufferedImage: BufferedImage,
        format: ImageFormat,
        quality: Int,
        outputStream: java.io.OutputStream
    ) {
        when (format) {
            ImageFormat.PNG -> {
                ImageIO.write(bufferedImage, "png", outputStream)
            }

            ImageFormat.JPEG -> {
                val rgbImage = BufferedImage(
                    bufferedImage.width,
                    bufferedImage.height,
                    BufferedImage.TYPE_INT_RGB
                )
                val g = rgbImage.createGraphics()
                try {
                    g.color = java.awt.Color.WHITE
                    g.fillRect(0, 0, rgbImage.width, rgbImage.height)
                    g.drawImage(bufferedImage, 0, 0, null)
                } finally {
                    g.dispose()
                }

                val writers = ImageIO.getImageWritersByFormatName("jpeg")
                check(writers.hasNext()) { "No JPEG writers found" }
                val writer = writers.next()
                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionQuality = quality / QUALITY_MAX.toFloat()

                ImageIO.createImageOutputStream(outputStream).use { ios ->
                    writer.output = ios
                    writer.write(
                        null,
                        javax.imageio.IIOImage(rgbImage, null, null),
                        writeParam
                    )
                }
                writer.dispose()
            }

            ImageFormat.WEBP -> {
                throw UnsupportedOperationException("WEBP format is not supported on JVM platform")
            }
        }
    }

    override fun close() {
        PdfiumJni.FPDF_ClosePage(pagePtr)
    }
}
