package com.sorrowblue.kpdfium.sample.data

import coil3.PlatformContext
import io.github.vinceglb.filekit.PlatformFile
import androidx.documentfile.provider.DocumentFile
import io.github.vinceglb.filekit.dialogs.toAndroidUri

actual suspend fun PlatformFile.listPdfFiles(context: PlatformContext): List<PlatformFile> {
    val uri = this.toAndroidUri()
    val documentFile = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
    if (!documentFile.exists() || !documentFile.isDirectory) return emptyList()
    val files = documentFile.listFiles()
    return files.filter { it.isFile && it.name?.endsWith(".pdf", ignoreCase = true) == true }
        .map { PlatformFile(it.uri) }
}
