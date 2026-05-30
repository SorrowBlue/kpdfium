package com.sorrowblue.kpdfium

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * JNI bindings for PDFium on Android.
 * These methods link directly to the native `libpdfium.so` library.
 */
internal object PdfiumJni {
    init {
        // Loads the native C++ PDFium library AND the compiled JNI bridge.
        System.loadLibrary("pdfium")
        System.loadLibrary("pdfium-jni")
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
     * Renders a PDF page directly into an Android Bitmap's raw memory buffer using Android NDK's
     * `AndroidBitmap_lockPixels` (Zero-copy, extremely high-performance!).
     */
    external fun FPDF_RenderPageBitmap(
        pagePtr: Long,
        bitmap: Bitmap,
        startX: Int, startY: Int,
        sizeX: Int, sizeY: Int,
        rotate: Int, flags: Int
    )
}

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

    override fun close() {
        PdfiumJni.FPDF_ClosePage(pagePtr)
    }
}

internal class AndroidPdfDocument(
    private val docPtr: Long,
    private val source: SeekableSource
) : PdfDocument {
    override val pageCount: Int get() = PdfiumJni.FPDF_GetPageCount(docPtr)

    override suspend fun getPage(pageIndex: Int): PdfPage {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw IndexOutOfBoundsException("Page index $pageIndex is out of bounds (0 ..< $pageCount)")
        }
        return AndroidPdfPage(docPtr, pageIndex)
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
            System.err.println("Failed to initialize native JNI PDFium: ${e.message}")
        }
    }

    public actual suspend fun openDocument(source: SeekableSource): PdfDocument = withContext(Dispatchers.IO) {
        val docPtr = PdfiumJni.FPDF_LoadCustomDocument(source, source.length(), null)
        if (docPtr == 0L) {
            throw IllegalArgumentException("Failed to parse PDF document via native JNI FPDF_FILEACCESS Android")
        }
        AndroidPdfDocument(docPtr, source)
    }
}
