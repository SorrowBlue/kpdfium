package com.sorrowblue.kpdfium.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorrowblue.kpdfium.PdfDocument
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.sample.data.PageData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.PickerResultLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AppState {
    val uiState: AppUiState
    val pagerState: PagerState
    val pages: List<PageData>
    fun onOpenDocumentClick()
}

@Composable
private fun rememberAppState(): AppState {
    val coroutineScope = rememberCoroutineScope()
    val state = remember { AppStateImpl(coroutineScope) }
    val pagerState = rememberPagerState(pageCount = { state.uiState.pageCount })
    state.pagerState = pagerState
    state.launcher = rememberFilePickerLauncher(
        type = FileKitType.File("pdf"),
        onResult = state::onResult
    )
    DisposableEffect(state) {
        onDispose(state::release)
    }
    return state
}

private class AppStateImpl(
    private val coroutineScope: CoroutineScope
) : AppState {

    private var pdfDocument: PdfDocument? = null
    lateinit var launcher: PickerResultLauncher
    override lateinit var pagerState: PagerState
    override var uiState by mutableStateOf(AppUiState())
    override val pages = mutableListOf<PageData>()

    override fun onOpenDocumentClick() {
        launcher.launch()
    }

    fun onResult(file: PlatformFile?) {
        uiState = AppUiState(isLoading = true)

        // Close previous document if open
        pdfDocument?.close()
        pdfDocument = null

        if (file == null) {
            uiState = AppUiState(isLoading = false, errorMessage = "Please select pdf file")
            return
        }

        coroutineScope.launch {
            try {
                uiState = uiState.copy(fileName = file.name)
                val doc = PdfExtractor.openDocument(RealSeekableSource(file))
                pdfDocument = doc
                pages.clear()
                pages.addAll(List(doc.pageCount) { PageData(file.path, it) })
                uiState = uiState.copy(pageCount = doc.pageCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                uiState = uiState.copy(
                    errorMessage = e.localizedMessage ?: e.message ?: "Failed to open PDF document."
                )
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun release() {
        pdfDocument?.close()
        pdfDocument = null
    }
}

data class AppUiState(
    val fileName: String? = null,
    val pageCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@Composable
fun App() {
    val state = rememberAppState()
    val uiState = state.uiState

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Simple Top Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = state::onOpenDocumentClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "OPEN PDF FILE", fontWeight = FontWeight.Bold)
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.visible(0 < uiState.pageCount)
                ) {
                    Text(
                        text = "Pages: ${uiState.pageCount}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simple picked file info
            Text(
                text = "File: ${uiState.fileName}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().visible(!uiState.fileName.isNullOrEmpty())
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Start,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else if (uiState.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(
                            text = "Error:\n${uiState.errorMessage}",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp
                        )
                    }
                } else if (uiState.pageCount > 0) {
                    // High-performance Page-by-Page Snapping Viewer with Hoisted Cache
                    HorizontalPager(
                        state = state.pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp,
                        verticalAlignment = Alignment.CenterVertically
                    ) { pageIndex ->
                        PdfPageItem(
                            data = state.pages[pageIndex],
                            isLoading = uiState.isLoading,
                            error = uiState.errorMessage
                        )
                    }
                } else {
                    Text(
                        text = "No PDF loaded.\nTap the button above to choose a file.",
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }

            // Bottom controls for rapid page navigation
            if (uiState.pageCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Current Page of Total Pages Indicator
                    Text(
                        text = "Page ${state.pagerState.currentPage + 1} of ${uiState.pageCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val coroutineScope = rememberCoroutineScope()
                    // 2. Horizontal Slider for rapid page scrubbing
                    Slider(
                        value = state.pagerState.currentPage.toFloat(),
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                state.pagerState.scrollToPage(newValue.toInt())
                            }
                        },
                        valueRange = 0f..(uiState.pageCount - 1).coerceAtLeast(1).toFloat(),
                        steps = if (uiState.pageCount > 2) uiState.pageCount - 2 else 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

data class PdfPageState(
    val image: ImageBitmap? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@Composable
fun PdfPageItem(
    data: PageData?,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(), // Expand to cover the Pager slot entirely
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Page ${data?.pageIndex?.plus(1)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Let the box occupy remaining space, centering the image
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else if (data != null) {
                    coil3.compose.AsyncImage(
                        model = data,
                        contentDescription = "Page ${data.pageIndex + 1}"
                    )
                } else if (error != null) {
                    Text(
                        text = "Error rendering page: $error",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
