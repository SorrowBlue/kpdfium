package com.sorrowblue.kpdfium

public actual val isResourceLoadingSupported: Boolean = true

public actual val isWebpSupported: Boolean = false

public actual fun loadTestPdf(fileName: String): ByteArray? {
    val pdfStream = TestResources::class.java.classLoader.getResourceAsStream(fileName)
    return pdfStream?.use { it.readBytes() }
}

public actual fun loadTestPdfSource(fileName: String): SeekableSource? {
    val pdfStream = TestResources::class.java.classLoader.getResourceAsStream(
        fileName
    ) ?: return null
    return InputStreamSeekableSource.create(pdfStream)
}

private object TestResources
