package com.sorrowblue.kpdfium

public actual object PdfExtractor {
    init {
        runCatching {
            PdfiumJni.FPDF_InitLibrary()
        }.onFailure {
            System.err.println("Failed to initialize native JNI PDFium: ${it.message}")
        }
    }

    public actual suspend fun openDocument(source: SeekableSource): PdfDocument {
        val docPtr = runWithPdfiumLock {
            PdfiumJni.FPDF_LoadCustomDocument(source, source.length(), null)
        }
        require(docPtr != 0L) {
            "Failed to parse PDF document via native JNI FPDF_FILEACCESS Android"
        }
        return AndroidPdfDocument(docPtr, source)
    }
}
