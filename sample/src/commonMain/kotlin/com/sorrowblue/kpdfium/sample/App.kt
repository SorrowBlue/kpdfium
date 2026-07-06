package com.sorrowblue.kpdfium.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorrowblue.kpdfium.sample.data.PageData
import com.sorrowblue.kpdfium.sample.section.ContentSheet
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name

@Composable
internal fun App() {
    val state = rememberAppState()
    AppScreen(
        uiState = state.uiState,
        pages = state.pages,
        pagerState = state.pagerState,
        onOpenFolderClick = state::onOpenFolderClick,
        onFileClick = state::onFileClick,
        onBackClick = state::onBackClick,
        onSliderValueChange = state::onSliderValueChange
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppScreen(
    uiState: AppUiState,
    pages: List<PageData>,
    pagerState: PagerState,
    onOpenFolderClick: () -> Unit,
    onFileClick: (PlatformFile) -> Unit,
    onBackClick: () -> Unit,
    onSliderValueChange: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (uiState.selectedFile != null) {
                        Button(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("←", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                title = {
                    Text(
                        text = uiState.selectedFile?.name 
                            ?: uiState.folderName 
                            ?: "No folder selected",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                actions = {
                    if (uiState.selectedFile == null) {
                        Button(
                            onClick = onOpenFolderClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (uiState.folderName == null) "OPEN PDF FOLDER" else "CHANGE FOLDER",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigation(
                currentPage = pagerState.currentPage,
                pageCount = uiState.pageCount,
                onValueChange = onSliderValueChange,
                modifier = Modifier.padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .visible(uiState.pageCount > 0 && uiState.selectedFile != null)
            )
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.selectedFile != null) {
                ContentSheet(
                    uiState = uiState,
                    pagerState = pagerState,
                    pages = pages,
                    contentPadding = contentPadding
                )
            } else if (uiState.folderName != null) {
                FolderContentGrid(
                    pdfFiles = uiState.pdfFiles,
                    onFileClick = onFileClick,
                    contentPadding = contentPadding
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Please select a folder to load PDF files",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenFolderClick,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("OPEN PDF FOLDER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            ErrorCard(errorMessage = uiState.errorMessage)
        }
    }
}

@Composable
internal fun FolderContentGrid(
    pdfFiles: List<PlatformFile>,
    onFileClick: (PlatformFile) -> Unit,
    contentPadding: PaddingValues
) {
    if (pdfFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No PDF files found in this folder.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(pdfFiles) { file ->
                PdfGridItem(file = file, onClick = { onFileClick(file) })
            }
        }
    }
}

@Composable
internal fun PdfGridItem(
    file: PlatformFile,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            val pageData = remember(file) { PageData(file, 0) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f),
                contentAlignment = Alignment.Center
            ) {
                coil3.compose.AsyncImage(
                    model = pageData,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun ErrorCard(errorMessage: String?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp).visible(errorMessage != null)
    ) {
        Text(
            text = "Error:\n$errorMessage",
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp
        )
    }
}

@Suppress("MagicNumber", "UnusedPrivateFunction")
@Preview
@Composable
private fun AppScreenPreview(@PreviewParameter(AppUiStateProvider::class) uiState: AppUiState) {
    MaterialTheme {
        AppScreen(
            uiState = remember { uiState },
            pages = List(5) { PageData(file = PlatformFile(""), pageIndex = it) },
            pagerState = rememberPagerState { 0 },
            onOpenFolderClick = {},
            onFileClick = {},
            onBackClick = {},
            onSliderValueChange = {}
        )
    }
}

private class AppUiStateProvider : PreviewParameterProvider<AppUiState> {
    override val values: Sequence<AppUiState>
        get() = sequenceOf(
            AppUiState(
                folderName = null,
                pdfFiles = emptyList(),
                selectedFile = null,
                pageCount = 0,
                isLoading = true,
                errorMessage = null
            ),
            AppUiState(
                folderName = "Documents",
                pdfFiles = List(3) { PlatformFile("") },
                selectedFile = null,
                pageCount = 0,
                isLoading = false,
                errorMessage = null
            ),
            AppUiState(
                folderName = "Documents",
                pdfFiles = List(3) { PlatformFile("") },
                selectedFile = PlatformFile(""),
                pageCount = 10,
                isLoading = false,
                errorMessage = "Failed to render page."
            )
        )
}
