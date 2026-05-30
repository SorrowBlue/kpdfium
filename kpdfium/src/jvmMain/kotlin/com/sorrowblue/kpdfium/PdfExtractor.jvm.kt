package com.sorrowblue.kpdfium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * JNI bindings for PDFium on JVM.
 * These methods link directly to the native `pdfium` and `pdfium-jni` libraries.
 */
internal object PdfiumJni {
    init {
        JniLoader.load()
    }

    external fun FPDF_InitLibrary()
    external fun FPDF_DestroyLibrary()
    
    /**
     * Loads a PDF document using JNI callback.
     * The C++ JNI bridge will hold a global reference to the [source] object and call its
     * seek() and read() methods dynamically whenever PDFium requests data.
     */
    external fun FPDF_LoadCustomDocument(source: SeekableSource, length: Long, password: String?): Long
    external fun FPDF_CloseDocument(docPtr: Long)
    
    external fun FPDF_GetPageCount(docPtr: Long): Int
    external fun FPDF_LoadPage(docPtr: Long, pageIndex: Int): Long
    external fun FPDF_ClosePage(pagePtr: Long)
    
    external fun FPDF_GetPageWidthF(pagePtr: Long): Float
    external fun FPDF_GetPageHeightF(pagePtr: Long): Float
    
    /**
     * Renders a PDF page directly into a JVM Direct ByteBuffer (Zero-copy!).
     */
    external fun FPDF_RenderPageBitmapJvm(
        pagePtr: Long,
        byteBuffer: java.nio.ByteBuffer,
        targetWidth: Int,
        targetHeight: Int,
        rotate: Int,
        flags: Int
    )
}

internal class JvmPdfPage(
    private val docPtr: Long,
    override val pageIndex: Int
) : PdfPage {
    private val pagePtr: Long = PdfiumJni.FPDF_LoadPage(docPtr, pageIndex)

    override val width: Int get() = PdfiumJni.FPDF_GetPageWidthF(pagePtr).toInt()
    override val height: Int get() = PdfiumJni.FPDF_GetPageHeightF(pagePtr).toInt()

    override suspend fun renderToPng(scale: Float): ByteArray = withContext(Dispatchers.IO) {
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        // 1. Allocate a Direct ByteBuffer (crucial for zero-copy JNI address retrieval)
        // Stride is targetWidth * 4 bytes (BGRA format)
        val bufferSize = targetWidth * targetHeight * 4
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize).apply {
            order(java.nio.ByteOrder.nativeOrder())
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

    override fun close() {
        PdfiumJni.FPDF_ClosePage(pagePtr)
    }
}

internal class JvmPdfDocument(
    private val docPtr: Long,
    private val source: SeekableSource
) : PdfDocument {
    override val pageCount: Int get() = PdfiumJni.FPDF_GetPageCount(docPtr)

    override suspend fun getPage(pageIndex: Int): PdfPage {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw IndexOutOfBoundsException("Page index $pageIndex is out of bounds (0 ..< $pageCount)")
        }
        return JvmPdfPage(docPtr, pageIndex)
    }

    override fun close() {
        PdfiumJni.FPDF_CloseDocument(docPtr)
        source.close() // Close the underlying seekable source
    }
}

public actual object PdfExtractor {
    init {
        try {
            PdfiumJni.FPDF_InitLibrary()
        } catch (e: Throwable) {
            System.err.println("Failed to initialize PDFium JVM Library: ${e.message}")
        }
    }

    public actual suspend fun openDocument(source: SeekableSource): PdfDocument = withContext(Dispatchers.IO) {
        val docPtr = PdfiumJni.FPDF_LoadCustomDocument(source, source.length(), null)
        if (docPtr == 0L) {
            throw IllegalArgumentException("Failed to parse PDF document via native JNI FPDF_FILEACCESS JVM")
        }
        JvmPdfDocument(docPtr, source)
    }
}
