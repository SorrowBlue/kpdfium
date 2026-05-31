package com.sorrowblue.kpdfium

import android.graphics.Bitmap

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
    external fun FPDF_LoadCustomDocument(
        source: SeekableSource,
        length: Long,
        password: String?
    ): Long

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
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    )
}
