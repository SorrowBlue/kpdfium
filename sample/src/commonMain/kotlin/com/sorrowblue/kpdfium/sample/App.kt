package com.sorrowblue.kpdfium.sample

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.visible
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorrowblue.kpdfium.sample.data.PageData
import com.sorrowblue.kpdfium.sample.section.ContentSheet
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal fun App() {
    val state = rememberAppState()
    state.uiState
    AppScreen(
        uiState = state.uiState,
        pages = state.pages,
        pagerState = state.pagerState,
        onOpenDocumentClick = state::onOpenDocumentClick,
        onSliderValueChange = state::onSliderValueChange
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppScreen(
    uiState: AppUiState,
    pages: List<PageData>,
    pagerState: PagerState,
    onOpenDocumentClick: () -> Unit,
    onSliderValueChange: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Button(
                        onClick = onOpenDocumentClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "OPEN PDF FILE", fontWeight = FontWeight.Bold)
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
                    .visible(uiState.pageCount > 0)
            )
        }
    ) { contentPadding ->
        ContentSheet(
            uiState = uiState,
            pagerState = pagerState,
            pages = pages,
            contentPadding = contentPadding
        )
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
            onOpenDocumentClick = {},
            onSliderValueChange = {}
        )
    }
}

private class AppUiStateProvider : PreviewParameterProvider<AppUiState> {
    override val values: Sequence<AppUiState>
        get() = sequenceOf(
            AppUiState(
                fileName = null,
                pageCount = 0,
                isLoading = true,
                errorMessage = null
            ),
            AppUiState(
                fileName = "SampleDocument.pdf",
                pageCount = 10,
                isLoading = false,
                errorMessage = null
            ),
            AppUiState(
                fileName = "SampleDocument.pdf",
                pageCount = 10,
                isLoading = false,
                errorMessage = "Failed to render page."
            )
        )
}
