package com.sorrowblue.kpdfium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public actual object PdfExtractor {
    init {
        try {
            PdfiumJni.FPDF_InitLibrary()
        } catch (e: Throwable) {
            System.err.println("Failed to initialize PDFium JVM Library: ${e.message}")
        }
    }

    public actual suspend fun openDocument(source: SeekableSource): PdfDocument =
        withContext(Dispatchers.IO) {
            val docPtr = PdfiumJni.FPDF_LoadCustomDocument(source, source.length(), null)
            if (docPtr == 0L) {
                throw IllegalArgumentException(
                    "Failed to parse PDF document via native JNI FPDF_FILEACCESS JVM"
                )
            }
            JvmPdfDocument(docPtr, source)
        }
}
