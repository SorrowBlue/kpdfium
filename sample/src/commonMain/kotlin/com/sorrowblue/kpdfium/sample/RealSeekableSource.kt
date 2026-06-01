package com.sorrowblue.kpdfium.sample

import coil3.PlatformContext
import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile

internal expect class RealSeekableSource(context: PlatformContext, file: PlatformFile) :
    SeekableSource {
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int
    override fun seek(position: Long)
    override fun position(): Long
    override fun length(): Long
    override fun close()
}
