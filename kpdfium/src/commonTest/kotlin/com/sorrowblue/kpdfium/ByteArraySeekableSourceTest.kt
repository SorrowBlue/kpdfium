package com.sorrowblue.kpdfium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteArraySeekableSourceTest {

    @Test
    fun testLengthAndInitialPosition() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val source = ByteArraySeekableSource(bytes)
        assertEquals(5L, source.length())
        assertEquals(0L, source.position())
    }

    @Test
    fun testSequentialRead() {
        val bytes = byteArrayOf(10, 20, 30, 40, 50)
        val source = ByteArraySeekableSource(bytes)

        val buffer = ByteArray(3)
        // 1回目の読み出し: 3バイト
        var bytesRead = source.read(buffer, 0, 3)
        assertEquals(3, bytesRead)
        assertEquals(3L, source.position())
        assertEquals(10, buffer[0])
        assertEquals(20, buffer[1])
        assertEquals(30, buffer[2])

        // 2回目の読み出し: 残り2バイトに対して3バイト要求
        val buffer2 = ByteArray(3)
        bytesRead = source.read(buffer2, 0, 3)
        assertEquals(2, bytesRead)
        assertEquals(5L, source.position())
        assertEquals(40, buffer2[0])
        assertEquals(50, buffer2[1])
        assertEquals(0, buffer2[2]) // 未変更

        // 3回目の読み出し: EOF
        bytesRead = source.read(buffer2, 0, 3)
        assertEquals(-1, bytesRead)
        assertEquals(5L, source.position())
    }

    @Test
    fun testReadWithOffsetAndLength() {
        val bytes = byteArrayOf(100, 101, 102)
        val source = ByteArraySeekableSource(bytes)

        val buffer = ByteArray(5)
        val bytesRead = source.read(buffer, 2, 2) // buffer のインデックス2から2バイト読み込む
        assertEquals(2, bytesRead)
        assertEquals(2L, source.position())
        assertEquals(0, buffer[0])
        assertEquals(0, buffer[1])
        assertEquals(100, buffer[2])
        assertEquals(101, buffer[3])
        assertEquals(0, buffer[4])
    }

    @Test
    fun testSeek() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val source = ByteArraySeekableSource(bytes)

        // 2番目のインデックスにシーク
        source.seek(2)
        assertEquals(2L, source.position())

        val buffer = ByteArray(2)
        val bytesRead = source.read(buffer, 0, 2)
        assertEquals(2, bytesRead)
        assertEquals(3, buffer[0]) // インデックス2の値
        assertEquals(4, buffer[1]) // インデックス3の値
        assertEquals(4L, source.position())

        // 逆シーク
        source.seek(0)
        assertEquals(0L, source.position())
        val firstByte = ByteArray(1)
        source.read(firstByte, 0, 1)
        assertEquals(1, firstByte[0])
    }

    @Test
    fun testInvalidSeek() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = ByteArraySeekableSource(bytes)

        // 負のポジションへのシークは例外を投げるべき
        assertFailsWith<IllegalArgumentException> {
            source.seek(-1)
        }
    }

    @Test
    fun testSeekBeyondLength() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = ByteArraySeekableSource(bytes)

        // 長さを超えたシーク
        source.seek(10)
        assertEquals(10L, source.position())

        // 読み出しは EOF (-1) が返るべき
        val buffer = ByteArray(1)
        val bytesRead = source.read(buffer, 0, 1)
        assertEquals(-1, bytesRead)
    }

    @Test
    fun testReadZeroOrNegativeLength() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = ByteArraySeekableSource(bytes)

        val buffer = ByteArray(2)
        // length が 0 の場合
        assertEquals(0, source.read(buffer, 0, 0))
        assertEquals(0L, source.position())

        // length が負の場合
        assertEquals(0, source.read(buffer, 0, -5))
        assertEquals(0L, source.position())
    }
}
