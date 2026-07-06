package com.sorrowblue.kpdfium.sample.data

import coil3.PlatformContext
import io.github.vinceglb.filekit.PlatformFile

actual suspend fun PlatformFile.listPdfFiles(context: PlatformContext): List<PlatformFile> {
    val dirFile = this.file
    if (!dirFile.exists() || !dirFile.isDirectory) return emptyList()
    val files = dirFile.listFiles() ?: return emptyList()
    return files.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
        .map { PlatformFile(it) }
}
