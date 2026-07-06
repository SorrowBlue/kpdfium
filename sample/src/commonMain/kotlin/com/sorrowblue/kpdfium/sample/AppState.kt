package com.sorrowblue.kpdfium.sample

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.sample.data.PageData
import com.sorrowblue.kpdfium.sample.data.listPdfFiles
import com.sorrowblue.kpdfium.sample.data.releasePdfDocument
import com.sorrowblue.kpdfium.sample.data.setupCoil
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.PickerResultLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal interface AppState {
    val uiState: AppUiState
    val pagerState: PagerState
    val pages: List<PageData>
    fun onOpenFolderClick()
    fun onFileClick(file: PlatformFile)
    fun onBackClick()
    fun onSliderValueChange(value: Int)
}

@Composable
internal fun rememberAppState(): AppState {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val state = remember { AppStateImpl(context, coroutineScope) }
    val pagerState = rememberPagerState(pageCount = { state.uiState.pageCount })
    state.pagerState = pagerState
    state.launcher = rememberDirectoryPickerLauncher(
        onResult = state::onFolderResult
    )
    DisposableEffect(Unit) {
        setupCoil()
        onDispose {
            releasePdfDocument()
        }
    }
    return state
}

private class AppStateImpl(
    private val context: PlatformContext,
    private val coroutineScope: CoroutineScope
) : AppState {

    lateinit var launcher: PickerResultLauncher
    override lateinit var pagerState: PagerState
    override var uiState by mutableStateOf(AppUiState())
    override val pages = mutableListOf<PageData>()

    override fun onOpenFolderClick() {
        launcher.launch()
    }

    override fun onSliderValueChange(value: Int) {
        coroutineScope.launch {
            pagerState.scrollToPage(value)
        }
    }

    fun onFolderResult(directory: PlatformFile?) {
        uiState = AppUiState(isLoading = true)

        coroutineScope.launch {
            if (directory == null) {
                uiState = AppUiState(isLoading = false, errorMessage = "Please select a folder")
                return@launch
            }
            runCatching {
                val files = directory.listPdfFiles(context)
                val folderName = directory.path.substringAfterLast('/').substringAfterLast('\\')
                uiState = uiState.copy(
                    folderName = folderName,
                    pdfFiles = files,
                    selectedFile = null,
                    pageCount = 0
                )
            }.onFailure {
                if (it is CancellationException) {
                    throw it
                } else {
                    it.printStackTrace()
                    uiState = uiState.copy(
                        errorMessage = it.message ?: "Failed to read folder contents."
                    )
                }
            }
            uiState = uiState.copy(isLoading = false)
        }
    }

    override fun onFileClick(file: PlatformFile) {
        uiState = uiState.copy(isLoading = true, selectedFile = file)

        coroutineScope.launch {
            runCatching {
                PdfExtractor.openDocument(RealSeekableSource(context, file)).use { document ->
                    pages.clear()
                    pages.addAll(List(document.pageCount) { PageData(file, it) })
                    uiState = uiState.copy(pageCount = document.pageCount)
                }
            }.onFailure {
                if (it is CancellationException) {
                    throw it
                } else {
                    it.printStackTrace()
                    uiState = uiState.copy(
                        errorMessage = it.message ?: "Failed to open PDF document."
                    )
                }
            }
            uiState = uiState.copy(isLoading = false)
        }
    }

    override fun onBackClick() {
        pages.clear()
        uiState = uiState.copy(selectedFile = null, pageCount = 0)
    }
}
