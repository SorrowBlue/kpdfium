package com.sorrowblue.kpdfium.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KPdfium Compose Desktop"
    ) {
        App()
    }
}
