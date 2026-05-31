package com.sorrowblue.kpdfium.sample.data

import coil3.decode.ImageSource
import coil3.disk.DiskCache
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.okio.asOkioSource
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer

@Suppress("TooGenericExceptionCaught")
internal fun AutoCloseable.closeQuietly() {
    try {
        close()
    } catch (e: RuntimeException) {
        throw e
    } catch (_: Exception) {
    }
}

internal fun DiskCache.Editor.abortQuietly() {
    try {
        abort()
    } catch (_: Exception) {
    }
}
internal fun ImageSource(
    source: Source,
    fileSystem: FileSystem,
    metadata: ImageSource.Metadata? = null,
): ImageSource =
    ImageSource(source.asOkioSource().buffer(), fileSystem.asOkioFileSystem(), metadata)

internal fun ImageSource(
    file: kotlinx.io.files.Path,
    fileSystem: FileSystem,
    diskCacheKey: String? = null,
    closeable: AutoCloseable? = null,
    metadata: ImageSource.Metadata? = null,
): ImageSource =
    ImageSource(file.asOkioPath(), fileSystem.asOkioFileSystem(), diskCacheKey, closeable, metadata)

internal fun FileSystem.asOkioFileSystem() = okio.FileSystem.SYSTEM

internal fun kotlinx.io.files.Path.asOkioPath() = toString().toPath()

internal fun Path.asKotlinxPath() = kotlinx.io.files.Path(toString())

internal interface CoilMetadata {
    fun writeTo(sink: Sink)

    companion object {
        inline fun <reified T : CoilMetadata> from(source: Source) =
            Json.decodeFromString<T>(source.readString())
    }
}
