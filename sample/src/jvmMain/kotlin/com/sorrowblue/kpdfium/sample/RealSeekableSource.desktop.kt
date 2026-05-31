package com.sorrowblue.kpdfium.sample

import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile
import java.io.RandomAccessFile

internal actual class RealSeekableSource actual constructor(
    private val file: PlatformFile
) : SeekableSource {

    private val raf: RandomAccessFile = RandomAccessFile(file.file, "r")

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return raf.read(buffer, offset, length)
    }

    override fun seek(position: Long) {
        raf.seek(position)
    }

    override fun position(): Long {
        return raf.filePointer
    }

    override fun length(): Long {
        return raf.length()
    }

    override fun close() {
        raf.close()
    }
}
