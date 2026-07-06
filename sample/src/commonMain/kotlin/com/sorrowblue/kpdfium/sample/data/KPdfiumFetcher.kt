package com.sorrowblue.kpdfium.sample.data

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.crossfade
import com.sorrowblue.kpdfium.DPI_STANDARD
import com.sorrowblue.kpdfium.ImageFormat
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.sample.RealSeekableSource
import io.github.vinceglb.filekit.path
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem

fun setupCoil() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader(context)
            .newBuilder()
            .components(
                ComponentRegistry.Builder()
                    .apply {
                        add(KPdfiumFetcher.Factory(), PageData::class)
                    }
                    .build()
            )
            .crossfade(true)
            .build()
    }
}

internal class KPdfiumFetcher(
    private val data: PageData,
    protected val options: Options,
    private val diskCache: Lazy<DiskCache?>
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        var snapshot = readFromDiskCache()
        runCatching {
            // Fast path: fetch the image from the disk cache without performing a network request.
            val result = fastPath(snapshot)
            if (result != null) {
                return result
            }

            PdfExtractor.openDocument(RealSeekableSource(options.context, data.file)).use { document ->
                val newSnapshot =
                    writeToDiskCache(snapshot = snapshot) { sink ->
                        document.getPage(data.pageIndex).use {
                            it.render(dpi = DPI_STANDARD, format = ImageFormat.JPEG, sink = sink)
                        }
                    }
                snapshot = newSnapshot

                if (newSnapshot != null) {
                    return SourceFetchResult(
                        source = newSnapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK
                    )
                }

                // 新しいスナップショットの読み取りに失敗した場合は、応答本文が空でない場合はそれを読み取ります。
                val source = Buffer().also { sink ->
                    document.getPage(data.pageIndex).use {
                        it.render(dpi = DPI_STANDARD, format = ImageFormat.JPEG, sink = sink)
                    }
                }
                return SourceFetchResult(
                    source = source.toImageSource(),
                    mimeType = null,
                    dataSource = DataSource.NETWORK
                )
            }
        }.onFailure {
            println("KPdfiumFetcher fetch error: " + it.message.orEmpty())
            snapshot?.closeQuietly()
        }.getOrThrow()
    }

    private fun fastPath(snapshot: DiskCache.Snapshot?): SourceFetchResult? {
        // Fast path: fetch the image from the disk cache without performing a network request.
        if (snapshot != null) {
            // Always return files with empty metadata as it's likely they've been written
            // to the disk cache manually.
            if (fileSystem.metadataOrNull(snapshot.metadata.asKotlinxPath())?.size == 0L) {
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK
                )
            }

            // Return the image from the disk cache if the cache strategy agrees.
            if (snapshot.toMetadataOrNull() == data) {
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK
                )
            }
        }
        return null
    }

    private fun DiskCache.Snapshot.toMetadataOrNull(): PageData? = try {
        fileSystem.source(metadata.asKotlinxPath()).buffered().use {
            CoilMetadata.from<PageData>(it)
        }
    } catch (_: IOException) {
        // If we can't parse the metadata, ignore this entry.
        null
    }

    private suspend fun writeToDiskCache(
        snapshot: DiskCache.Snapshot?,
        writeTo: suspend (Sink) -> Unit
    ): DiskCache.Snapshot? {
        // Short circuit if we're not allowed to cache this response.
        if (!options.diskCachePolicy.writeEnabled) {
            snapshot?.closeQuietly()
            return null
        }

        // Open a new editor. Return null if we're unable to write to this entry.
        val editor = if (snapshot != null) {
            snapshot.closeAndOpenEditor()
        } else {
            diskCache.value?.openEditor(diskCacheKey)
        } ?: return null

        // Write the network request metadata and the network response body to disk.
        runCatching {
            fileSystem.sink(editor.metadata.asKotlinxPath()).buffered().use {
                data.writeTo(it)
            }
            fileSystem.sink(editor.data.asKotlinxPath()).buffered().use {
                writeTo(it)
            }
            return editor.commitAndOpenSnapshot()
        }.onFailure {
            editor.abortQuietly()
        }.getOrThrow()
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource = ImageSource(
        file = data.asKotlinxPath(),
        fileSystem = fileSystem,
        diskCacheKey = diskCacheKey,
        closeable = this
    )

    private fun Source.toImageSource(): ImageSource = ImageSource(
        source = this,
        fileSystem = fileSystem
    )

    private fun readFromDiskCache(): DiskCache.Snapshot? =
        if (options.diskCachePolicy.readEnabled) {
            diskCache.value?.openSnapshot(diskCacheKey)
        } else {
            null
        }

    val diskCacheKey: String = data.file.path

    private val fileSystem: FileSystem
        get() = SystemFileSystem

    class Factory : Fetcher.Factory<PageData> {
        override fun create(data: PageData, options: Options, imageLoader: ImageLoader): Fetcher =
            KPdfiumFetcher(data, options, lazy { imageLoader.diskCache })
    }
}
