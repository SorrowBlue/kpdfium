package com.sorrowblue.kpdfium

/**
 * Interface representing a random-access, seekable read-only source.
 */
public interface SeekableSource : AutoCloseable {
    /**
     * Reads bytes into the specified buffer.
     * Returns the number of bytes read, or -1 if EOF is reached.
     */
    public fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /**
     * Seeks to the specified absolute byte position in the stream.
     */
    public fun seek(position: Long)

    /**
     * Returns the current absolute read position (in bytes) in the stream.
     */
    public fun position(): Long

    /**
     * Returns the total length of the stream in bytes.
     */
    public fun length(): Long
}

/**
 * A standard in-memory implementation of [SeekableSource] backed by a [ByteArray].
 */
public class ByteArraySeekableSource(private val bytes: ByteArray) : SeekableSource {
    private var pos: Long = 0L

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= bytes.size) return -1
        val available = (bytes.size - pos).toInt()
        val toRead = minOf(length, available)
        if (toRead <= 0) return 0
        bytes.copyInto(buffer, offset, pos.toInt(), pos.toInt() + toRead)
        pos += toRead
        return toRead
    }

    override fun seek(position: Long) {
        require(position >= 0) { "Position must be non-negative: $position" }
        pos = position
    }

    override fun position(): Long = pos

    override fun length(): Long = bytes.size.toLong()

    override fun close() {
        // No-op for in-memory byte array
    }
}
