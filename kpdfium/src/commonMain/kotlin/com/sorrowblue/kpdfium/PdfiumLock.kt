package com.sorrowblue.kpdfium

/**
 * PDFium はマルチスレッドに対してスレッドセーフではないため、
 * すべてのプラットフォームで PDFium ネイティブライブラリの呼び出しを
 * このロックで排他制御し、直列化します。
 */
public expect inline fun <T> runWithPdfiumLock(block: () -> T): T
