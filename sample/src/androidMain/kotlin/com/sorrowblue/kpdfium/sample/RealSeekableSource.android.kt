package com.sorrowblue.kpdfium.sample

import android.os.ParcelFileDescriptor
import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.toAndroidUri
import java.io.FileInputStream
import java.nio.channels.FileChannel

internal actual class RealSeekableSource actual constructor(private val file: PlatformFile) :
    SeekableSource {

    private val pfd: ParcelFileDescriptor
    private val fileInputStream: FileInputStream
    private val fileChannel: FileChannel
    private val fileLength: Long

    init {
        val context = AppContext.context
            ?: throw IllegalStateException(
                "AppContext.context has not been initialized. Please set it in MainActivity.onCreate."
            )

        pfd = context.contentResolver.openFileDescriptor(file.toAndroidUri(), "r")
            ?: throw IllegalArgumentException(
                "Failed to open file descriptor for URI: ${file.toAndroidUri()}"
            )

        fileInputStream = FileInputStream(pfd.fileDescriptor)
        fileChannel = fileInputStream.channel
        fileLength = fileChannel.size()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val byteBuffer = java.nio.ByteBuffer.wrap(buffer, offset, length)
        return fileChannel.read(byteBuffer)
    }

    override fun seek(position: Long) {
        fileChannel.position(position)
    }

    override fun position(): Long = fileChannel.position()

    override fun length(): Long = fileLength

    override fun close() {
        fileChannel.close()
        fileInputStream.close()
        pfd.close()
    }
}
