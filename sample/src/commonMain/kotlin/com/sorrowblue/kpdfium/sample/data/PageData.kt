package com.sorrowblue.kpdfium.sample.data

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PageData(val file: PlatformFile, val pageIndex: Int) : CoilMetadata {
    override fun writeTo(sink: Sink) {
        sink.writeString(Json.encodeToString(this))
    }
}
