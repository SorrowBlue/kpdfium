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
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.Precision
import com.sorrowblue.kpdfium.PdfDocument
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.sample.RealSeekableSource
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem
import kotlin.reflect.KClass

private var path: String? = null
private var pdfDocument: PdfDocument? = null
private val mutex = Mutex()

fun setupCoil() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader(context)
            .newBuilder()
            .components(ComponentRegistry.Builder()
                .apply {
                    add(KPdfiumFetcher.Factory(), PageData::class)
                }
                .build())
            .crossfade(true)
            .build()
    }
}

internal class KPdfiumFetcher(
    private val data: PageData,
    protected val options: Options,
    private val diskCache: Lazy<DiskCache?>,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        var snapshot = readFromDiskCache()
        try {
            // Fast path: fetch the image from the disk cache without performing a network request.
            val result = fastPath(snapshot)
            if (result != null) {
                return result
            }

            mutex.withLock {
                if (path != data.path || pdfDocument == null) {
                    path = data.path
                    pdfDocument =
                        PdfExtractor.openDocument(RealSeekableSource(PlatformFile(data.path)))
                }
            }

            snapshot =
                writeToDiskCache(snapshot = snapshot) { sink ->
                    pdfDocument!!.getPage(data.pageIndex).renderToPng(scale = 1.5f, sink = sink)
                }

            if (snapshot != null) {
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.NETWORK,
                )
            }

            // 新しいスナップショットの読み取りに失敗した場合は、応答本文が空でない場合はそれを読み取ります。
            val source = Buffer().also {
                pdfDocument!!.getPage(data.pageIndex).renderToPng(scale = 1.5f, sink = it)
            }
            return SourceFetchResult(
                source = source.toImageSource(),
                mimeType = null,
                dataSource = DataSource.NETWORK
            )
        } catch (e: Exception) {
            snapshot?.closeQuietly()
            throw e
        }
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
                    dataSource = DataSource.DISK,
                )
            }

            // Return the image from the disk cache if the cache strategy agrees.
            if (snapshot.toMetadataOrNull() == data) {
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }
        }
        return null
    }

    private fun DiskCache.Snapshot.toMetadataOrNull(): PageData? {
        return try {
            fileSystem.source(metadata.asKotlinxPath()).buffered().use {
                CoilMetadata.from<PageData>(it)
            }
        } catch (_: IOException) {
            // If we can't parse the metadata, ignore this entry.
            null
        }
    }

    private suspend fun writeToDiskCache(
        snapshot: DiskCache.Snapshot?,
        writeTo: suspend (Sink) -> Unit,
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
        try {
            fileSystem.sink(editor.metadata.asKotlinxPath()).buffered().use {
                data.writeTo(it)
            }
            fileSystem.sink(editor.data.asKotlinxPath()).buffered().use {
                writeTo(it)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            editor.abortQuietly()
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data.asKotlinxPath(),
            fileSystem = fileSystem,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun Source.toImageSource(): ImageSource {
        return ImageSource(
            source = this,
            fileSystem = fileSystem,
        )
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        if (options.diskCachePolicy.readEnabled) {
            return diskCache.value?.openSnapshot(diskCacheKey)
        } else {
            return null
        }
    }

    val diskCacheKey: String = data.path

    private val fileSystem: FileSystem
        get() = SystemFileSystem

    class Factory : Fetcher.Factory<PageData> {
        override fun create(
            data: PageData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return KPdfiumFetcher(data, options, lazy { imageLoader.diskCache })
        }
    }
}
