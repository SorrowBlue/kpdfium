package com.sorrowblue.kpdfium.sample

internal data class AppUiState(
    val fileName: String? = null,
    val pageCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
