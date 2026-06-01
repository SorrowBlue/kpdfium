package com.sorrowblue.kpdfium.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BottomNavigation(
    currentPage: Int,
    pageCount: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "Page ${currentPage + 1} of $pageCount",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalFloatingToolbar(
            expanded = true,
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Slider(
                value = currentPage.toFloat(),
                onValueChange = {
                    onValueChange(it.toInt())
                },
                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                steps = if (pageCount > 2) pageCount - 2 else 0,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
