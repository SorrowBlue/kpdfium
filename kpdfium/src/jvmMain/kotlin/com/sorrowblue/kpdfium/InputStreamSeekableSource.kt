package com.sorrowblue.kpdfium

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Java の [InputStream] からランダムアクセスを可能にする [SeekableSource] の実装。
 * [FileInputStream] の場合はメモリを消費せず直接ディスクから読み込みを行い、
 * それ以外の入力ストリームの場合は一時ファイルにコピーして処理します。
 */
public class InputStreamSeekableSource private constructor(
    private val channel: FileChannel,
    private val totalLength: Long,
    private val tempFile: File? = null
) : SeekableSource {

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        val byteBuffer = ByteBuffer.wrap(buffer, offset, length)
        val readBytes = channel.read(byteBuffer)
        return if (readBytes <= 0 && channel.position() >= totalLength) -1 else readBytes
    }

    override fun seek(position: Long) {
        require(position >= 0) { "Position must be non-negative: $position" }
        channel.position(position)
    }

    override fun position(): Long = channel.position()

    override fun length(): Long = totalLength

    override fun close() {
        channel.close()
        tempFile?.delete()
    }

    public companion object {
        /**
         * [InputStream] から [InputStreamSeekableSource] を作成します。
         *
         * @param inputStream 入力元となる InputStream。
         * @return [InputStreamSeekableSource] のインスタンス。
         */
        public fun create(inputStream: InputStream): InputStreamSeekableSource {
            return if (inputStream is FileInputStream) {
                InputStreamSeekableSource(inputStream.channel, inputStream.channel.size())
            } else {
                val tempFile = File.createTempFile("kpdfium_stream_", ".pdf")
                tempFile.deleteOnExit()
                
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                
                val raf = RandomAccessFile(tempFile, "r")
                InputStreamSeekableSource(raf.channel, tempFile.length(), tempFile)
            }
        }
    }
}
