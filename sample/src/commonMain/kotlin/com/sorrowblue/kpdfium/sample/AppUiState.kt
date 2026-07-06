package com.sorrowblue.kpdfium.sample

import io.github.vinceglb.filekit.PlatformFile

internal data class AppUiState(
    val folderName: String? = null,
    val pdfFiles: List<PlatformFile> = emptyList(),
    val selectedFile: PlatformFile? = null,
    val pageCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
