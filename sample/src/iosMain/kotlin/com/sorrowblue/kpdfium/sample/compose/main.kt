@file:OptIn(ExperimentalForeignApi::class)
package com.sorrowblue.kpdfium.sample.compose

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import com.sorrowblue.kpdfium.sample.App
import platform.UIKit.*
import platform.Foundation.*
import platform.UniformTypeIdentifiers.*
import kotlinx.cinterop.*
import platform.posix.memcpy

fun main(): UIViewController = ComposeUIViewController {
    var onFileLoadedCallback by remember { mutableStateOf<((ByteArray, String) -> Unit)?>(null) }

    App(
        onOpenFilePicker = { onFileLoaded ->
            onFileLoadedCallback = onFileLoaded

            // Trigger iOS native Document Picker for PDF files
            val documentPicker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypePDF),
                asCopy = true
            )

            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                    val data = NSData.dataWithContentsOfURL(url) ?: return
                    val bytes = ByteArray(data.length.toInt()).apply {
                        usePinned { pinned ->
                            memcpy(pinned.addressOf(0), data.bytes, data.length)
                        }
                    }
                    val fileName = url.lastPathComponent ?: "Document.pdf"
                    onFileLoadedCallback?.invoke(bytes, fileName)
                }
            }

            // Retain delegate via strongly referenced association
            objc_setAssociatedObject(
                documentPicker,
                "picker_delegate".cstr,
                delegate,
                OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
            documentPicker.setDelegate(delegate)

            // Present document picker from active view controller
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootViewController?.presentViewController(
                documentPicker,
                animated = true,
                completion = null
            )
        }
    )
}
