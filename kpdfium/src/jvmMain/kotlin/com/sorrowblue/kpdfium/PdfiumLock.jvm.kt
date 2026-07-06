package com.sorrowblue.kpdfium

@PublishedApi
internal val pdfiumGlobalLock: Any = Any()

public actual inline fun <T> runWithPdfiumLock(block: () -> T): T {
    return synchronized(pdfiumGlobalLock) {
        block()
    }
}
