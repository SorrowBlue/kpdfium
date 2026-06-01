package com.sorrowblue.kpdfium.sample.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorrowblue.kpdfium.sample.AppUiState
import com.sorrowblue.kpdfium.sample.ErrorCard
import com.sorrowblue.kpdfium.sample.PdfPageItem
import com.sorrowblue.kpdfium.sample.component.FileName
import com.sorrowblue.kpdfium.sample.data.PageData

@Composable
internal fun ContentSheet(
    uiState: AppUiState,
    pagerState: PagerState,
    pages: List<PageData>,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileName(fileName = uiState.fileName, modifier = Modifier.padding(top = 12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.errorMessage != null) {
                ErrorCard(errorMessage = uiState.errorMessage)
            } else if (uiState.pageCount > 0) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 16.dp,
                    verticalAlignment = Alignment.CenterVertically
                ) { pageIndex ->
                    PdfPageItem(
                        data = pages[pageIndex],
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
    }
}
