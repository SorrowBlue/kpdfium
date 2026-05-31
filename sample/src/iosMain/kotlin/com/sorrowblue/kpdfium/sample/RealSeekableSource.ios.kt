package com.sorrowblue.kpdfium.sample

import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSFileHandle
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.seekToOffset
import platform.Foundation.readDataOfLength
import platform.Foundation.closeFile

internal actual class RealSeekableSource actual constructor(
    private val file: PlatformFile
) : SeekableSource {

    private val fileHandle: NSFileHandle = NSFileHandle.fileHandleForReadingFromURL(file.nsurl, null)
        ?: throw IllegalArgumentException("Failed to open NSFileHandle for URL: ${file.nsurl}")

    private val fileLength: Long

    init {
        val current = fileHandle.offsetInFile
        val end = fileHandle.seekToEndOfFile()
        fileHandle.seekToOffset(current, null)
        fileLength = end.toLong()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val data = fileHandle.readDataOfLength(length.toULong())
        if (data.length == 0UL) return -1
        
        val bytes = data.bytes
        if (bytes != null) {
            platform.ffi.memcpy(
                buffer.refTo(offset),
                bytes,
                data.length
            )
            return data.length.toInt()
        }
        return -1
    }

    override fun seek(position: Long) {
        fileHandle.seekToOffset(position.toULong(), null)
    }

    override fun position(): Long {
        return fileHandle.offsetInFile.toLong()
    }

    override fun length(): Long {
        return fileLength
    }

    override fun close() {
        fileHandle.closeFile()
    }
}
