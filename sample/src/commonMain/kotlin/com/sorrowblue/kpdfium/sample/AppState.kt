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
import com.sorrowblue.kpdfium.sample.data.releasePdfDocument
import com.sorrowblue.kpdfium.sample.data.setupCoil
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.PickerResultLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal interface AppState {
    val uiState: AppUiState
    val pagerState: PagerState
    val pages: List<PageData>
    fun onOpenDocumentClick()
    fun onSliderValueChange(value: Int)
}

@Composable
internal fun rememberAppState(): AppState {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val state = remember { AppStateImpl(context, coroutineScope) }
    val pagerState = rememberPagerState(pageCount = { state.uiState.pageCount })
    state.pagerState = pagerState
    state.launcher = rememberFilePickerLauncher(
        type = FileKitType.File("pdf"),
        onResult = state::onResult
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

    override fun onOpenDocumentClick() {
        launcher.launch()
    }

    override fun onSliderValueChange(value: Int) {
        coroutineScope.launch {
            pagerState.scrollToPage(value)
        }
    }

    fun onResult(file: PlatformFile?) {
        uiState = AppUiState(isLoading = true)

        coroutineScope.launch {
            if (file == null) {
                uiState = AppUiState(isLoading = false, errorMessage = "Please select pdf file")
                return@launch
            }
            runCatching {
                uiState = uiState.copy(fileName = file.name)
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
}
