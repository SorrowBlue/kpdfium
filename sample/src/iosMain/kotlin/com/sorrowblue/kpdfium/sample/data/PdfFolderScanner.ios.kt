package com.sorrowblue.kpdfium.sample.data

import coil3.PlatformContext
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list

actual suspend fun PlatformFile.listPdfFiles(context: PlatformContext): List<PlatformFile> =
    this.list().filter {
        it.isRegularFile() && it.extension.equals(
            "pdf",
            ignoreCase = true
        )
    }
