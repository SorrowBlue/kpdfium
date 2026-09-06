package com.sorrowblue.kpdfium

import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit

@PublishedApi
internal val pdfiumGlobalLock: Any = Any()

public actual inline fun <T> runWithPdfiumLock(block: () -> T): T {
    objc_sync_enter(pdfiumGlobalLock)
    try {
        return block()
    } finally {
        objc_sync_exit(pdfiumGlobalLock)
    }
}