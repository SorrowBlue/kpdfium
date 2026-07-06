package com.sorrowblue.kpdfium

public actual object PdfExtractor {
    init {
        runCatching {
            PdfiumJni.FPDF_InitLibrary()
        }.onFailure { e ->
            System.err.println("Failed to initialize PDFium JVM Library: ${e.message}")
        }
    }

    public actual suspend fun openDocument(source: SeekableSource): PdfDocument {
        val docPtr = runWithPdfiumLock {
            PdfiumJni.FPDF_LoadCustomDocument(source, source.length(), null)
        }
        require(docPtr != 0L) {
            "Failed to parse PDF document via native JNI FPDF_FILEACCESS JVM"
        }
        return JvmPdfDocument(docPtr, source)
    }
}
