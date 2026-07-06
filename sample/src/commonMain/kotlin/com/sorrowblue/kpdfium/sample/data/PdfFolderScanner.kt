package com.sorrowblue.kpdfium.sample.data

import coil3.PlatformContext
import io.github.vinceglb.filekit.PlatformFile

expect suspend fun PlatformFile.listPdfFiles(context: PlatformContext): List<PlatformFile>
