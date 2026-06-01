package com.sorrowblue.kpdfium

import java.nio.ByteBuffer

/**
 * JNI bindings for PDFium on JVM.
 * These methods link directly to the native `pdfium` and `pdfium-jni` libraries.
 */
@Suppress("FunctionNaming")
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
     * Renders a PDF page directly into a JVM Direct ByteBuffer (Zero-copy!).
     */
    @Suppress("LongParameterList")
    external fun FPDF_RenderPageBitmapJvm(
        pagePtr: Long,
        byteBuffer: ByteBuffer,
        targetWidth: Int,
        targetHeight: Int,
        rotate: Int,
        flags: Int
    )
}
