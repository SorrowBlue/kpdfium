package com.sorrowblue.kpdfium

public actual inline fun <T> runWithPdfiumLock(block: () -> T): T = block()
