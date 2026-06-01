package com.sorrowblue.kpdfium.sample.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.visible
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
internal fun FileName(fileName: String?, modifier: Modifier = Modifier) {
    Text(
        text = "File: $fileName",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.fillMaxWidth().visible(!fileName.isNullOrEmpty()),
        textAlign = TextAlign.Start
    )
}
