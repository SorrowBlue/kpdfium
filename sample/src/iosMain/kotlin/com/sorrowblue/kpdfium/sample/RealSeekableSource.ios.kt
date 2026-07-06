package com.sorrowblue.kpdfium.sample

import coil3.PlatformContext
import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSURL
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.errno
import platform.posix.fclose
import platform.posix.feof
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
internal actual class RealSeekableSource actual constructor(
    context: PlatformContext,
    private val file: PlatformFile
) : SeekableSource {

    private val nsUrl: NSURL = file.nsUrl
    private val isSecurityScoped: Boolean = file.startAccessingSecurityScopedResource()

    private val filePtr: CPointer<FILE>?
    private val fileLength: Long

    init {
        val path = nsUrl.path ?: run {
            if (isSecurityScoped) {
                file.stopAccessingSecurityScopedResource()
            }
            throw IllegalArgumentException("Failed to resolve path from NSURL")
        }

        filePtr = fopen(path, "rb") ?: run {
            if (isSecurityScoped) {
                file.stopAccessingSecurityScopedResource()
            }
            throw IllegalArgumentException("Failed to open file: $path (errno = $errno)")
        }

        val fp = filePtr
        fseek(fp, 0, SEEK_END)
        fileLength = ftell(fp)
        fseek(fp, 0, SEEK_SET)
    }

    actual override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val fp = filePtr ?: return -1
        val readBytes = buffer.usePinned { pinned ->
            fread(pinned.addressOf(offset), 1UL, length.toULong(), fp)
        }
        if (readBytes == 0UL) {
            return if (feof(fp) != 0) -1 else 0
        }
        return readBytes.toInt()
    }

    actual override fun seek(position: Long) {
        val fp = filePtr ?: return
        fseek(fp, position, SEEK_SET)
    }

    actual override fun position(): Long {
        val fp = filePtr ?: return 0L
        return ftell(fp)
    }

    actual override fun length(): Long = fileLength

    actual override fun close() {
        try {
            filePtr?.let { fclose(it) }
        } finally {
            if (isSecurityScoped) {
                file.stopAccessingSecurityScopedResource()
            }
        }
    }
}
