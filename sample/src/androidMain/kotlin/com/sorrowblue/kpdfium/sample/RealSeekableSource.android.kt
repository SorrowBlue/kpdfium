package com.sorrowblue.kpdfium.sample

import coil3.PlatformContext
import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.toAndroidUri
import java.io.FileInputStream

internal actual class RealSeekableSource actual constructor(
    context: PlatformContext,
    file: PlatformFile
) : SeekableSource {

    private val pfd =
        context.contentResolver.openFileDescriptor(file.toAndroidUri(), "r")
            ?: throw IllegalArgumentException(
                "Failed to open file descriptor for URI: ${file.toAndroidUri()}"
            )
    private val fileInputStream = FileInputStream(pfd.fileDescriptor)
    private val fileChannel = fileInputStream.channel
    private val fileLength = fileChannel.size()

    actual override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val byteBuffer = java.nio.ByteBuffer.wrap(buffer, offset, length)
        return fileChannel.read(byteBuffer)
    }

    actual override fun seek(position: Long) {
        fileChannel.position(position)
    }

    actual override fun position(): Long = fileChannel.position()

    actual override fun length(): Long = fileLength

    actual override fun close() {
        fileChannel.close()
        fileInputStream.close()
        pfd.close()
    }
}
