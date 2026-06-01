package com.sorrowblue.kpdfium.sample

import coil3.PlatformContext
import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile
import java.io.RandomAccessFile

internal actual class RealSeekableSource actual constructor(
    context: PlatformContext,
    private val file: PlatformFile
) : SeekableSource {

    private val raf: RandomAccessFile = RandomAccessFile(file.file, "r")

    actual override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        raf.read(buffer, offset, length)

    actual override fun seek(position: Long) {
        raf.seek(position)
    }

    actual override fun position(): Long = raf.filePointer

    actual override fun length(): Long = raf.length()

    actual override fun close() {
        raf.close()
    }
}
