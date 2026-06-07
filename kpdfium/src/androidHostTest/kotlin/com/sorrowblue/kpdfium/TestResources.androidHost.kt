package com.sorrowblue.kpdfium

public actual val isResourceLoadingSupported: Boolean = false

public actual val isWebpSupported: Boolean = false

public actual fun loadTestPdf(fileName: String): ByteArray? = null

public actual fun loadTestPdfSource(fileName: String): SeekableSource? = null
