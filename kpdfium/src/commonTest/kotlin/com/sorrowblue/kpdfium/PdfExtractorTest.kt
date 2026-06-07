package com.sorrowblue.kpdfium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class PdfExtractorTest {

    private fun getRequiredTestPdf(fileName: String): ByteArray {
        val pdfBytes = loadTestPdf(fileName)
        assertNotNull(pdfBytes, "$fileName was not found or failed to load")
        return pdfBytes
    }

    @Test
    fun testOpenDocumentAndGetPageProperties() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            assertNotNull(document)

            try {
                // sample_test.pdf は 1 ページであるべき
                assertEquals(1, document.pageCount)

                // 0ページ目を取得
                val page = document.getPage(0)
                assertNotNull(page)

                try {
                    assertEquals(0, page.pageIndex)
                    // 縦横のサイズが正の値であることを検証
                    assertTrue(page.width > 0, "Page width must be greater than 0")
                    assertTrue(page.height > 0, "Page height must be greater than 0")
                } finally {
                    page.close()
                }
            } finally {
                document.close()
            }
        }
    }

    @Test
    fun testRenderPageToPng() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            try {
                // PNG形式でのレンダリング
                val pngBytes = page.render(dpi = DPI_STANDARD, format = ImageFormat.PNG)
                assertNotNull(pngBytes)
                assertTrue(pngBytes.isNotEmpty(), "Rendered PNG bytes should not be empty")
            } finally {
                page.close()
                document.close()
            }
        }
    }

    @Test
    fun testRenderPageToJpeg() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            try {
                // JPEG形式でのレンダリング
                val jpegBytes = page.render(dpi = DPI_HIGH, format = ImageFormat.JPEG, quality = 80)
                assertNotNull(jpegBytes)
                assertTrue(jpegBytes.isNotEmpty(), "Rendered JPEG bytes should not be empty")
            } finally {
                page.close()
                document.close()
            }
        }
    }

    @Test
    fun testRenderPageToWebp() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            try {
                if (isWebpSupported) {
                    val webpBytes = page.render(dpi = DPI_STANDARD, format = ImageFormat.WEBP)
                    assertNotNull(webpBytes)
                    assertTrue(webpBytes.isNotEmpty(), "Rendered WEBP bytes should not be empty")
                } else {
                    assertFailsWith<UnsupportedOperationException> {
                        page.render(dpi = DPI_STANDARD, format = ImageFormat.WEBP)
                    }
                }
            } finally {
                page.close()
                document.close()
            }
        }
    }

    @Test
    fun testRenderInvalidDpi() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            try {
                // DPIが0以下の場合
                assertFailsWith<IllegalArgumentException> {
                    page.render(dpi = 0)
                }
                assertFailsWith<IllegalArgumentException> {
                    page.render(dpi = -10)
                }
            } finally {
                page.close()
                document.close()
            }
        }
    }

    @Test
    fun testRenderInvalidQuality() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            try {
                // 品質(quality)が範囲外の場合
                assertFailsWith<IllegalArgumentException> {
                    page.render(quality = -1)
                }
                assertFailsWith<IllegalArgumentException> {
                    page.render(quality = 101)
                }
            } finally {
                page.close()
                document.close()
            }
        }
    }

    @Test
    fun testOpenLargeDocument() {
        if (!isResourceLoadingSupported) return
        val source = loadTestPdfSource("large_test.pdf")
        if (source == null) {
            println("large_test.pdf is not available, skipping testOpenLargeDocument")
            return
        }

        runBlocking {
            val document = PdfExtractor.openDocument(source)
            assertNotNull(document)

            try {
                assertTrue(document.pageCount > 0, "Large PDF document should have at least 1 page")
                for (i in 0 until document.pageCount) {
                    val page = document.getPage(i)
                    assertNotNull(page)
                    try {
                        assertTrue(page.width > 0)
                        assertTrue(page.height > 0)
                        // 全ページをレンダリング（書き出し）できるかテスト。
                        // テストの実行速度向上のため、DPIを低く（10）設定して処理を軽量化します。
                        val imageBytes = page.render(dpi = 10, format = ImageFormat.PNG)
                        assertNotNull(imageBytes)
                        assertTrue(
                            imageBytes.isNotEmpty(),
                            "Page $i image bytes should not be empty"
                        )
                    } finally {
                        page.close()
                    }
                }
            } finally {
                document.close()
                source.close()
            }
        }
    }


    @Test
    fun testOpenInvalidDocument() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            // 空のバイト配列
            assertFailsWith<IllegalArgumentException> {
                PdfExtractor.openDocument(ByteArray(0))
            }
            // ランダムで無効なバイナリデータ
            assertFailsWith<IllegalArgumentException> {
                PdfExtractor.openDocument(byteArrayOf(1, 2, 3, 4, 5))
            }
        }
    }

    @Test
    fun testInvalidPageIndices() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            assertNotNull(document)

            try {
                val pageCount = document.pageCount
                // 負のインデックス
                assertFailsWith<IndexOutOfBoundsException> {
                    document.getPage(-1)
                }
                // ページ数以上のインデックス
                assertFailsWith<IndexOutOfBoundsException> {
                    document.getPage(pageCount)
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    document.getPage(pageCount + 10)
                }
            } finally {
                document.close()
            }
        }
    }

    @Test
    fun testConcurrentPdfRenderingStability() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            assertNotNull(document)

            try {
                // 12の並行コルーチンから同時に0ページ目をレンダリングする
                val jobs = List(12) {
                    async {
                        repeat(10) {
                            document.getPage(0).use { page ->
                                val bytes = page.render(dpi = 10, format = ImageFormat.PNG)
                                assertNotNull(bytes)
                                assertTrue(bytes.isNotEmpty())
                            }
                        }
                    }
                }
                jobs.awaitAll()
            } finally {
                document.close()
            }
        }
    }

    @Test
    fun testClosedDocumentAndPageAccess() {
        if (!isResourceLoadingSupported) return
        runBlocking {
            val pdfBytes = getRequiredTestPdf("sample_test.pdf")
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            // まずクローズ前の状態を確認
            assertTrue(document.pageCount > 0)
            assertTrue(page.width > 0)

            // ページをクローズ
            page.close()

            // クローズ済みのページ操作で IllegalStateException が発生することを確認
            assertFailsWith<IllegalStateException> {
                page.width
            }
            assertFailsWith<IllegalStateException> {
                page.height
            }
            assertFailsWith<IllegalStateException> {
                page.render(dpi = 10, format = ImageFormat.PNG)
            }

            // ドキュメントをクローズ
            document.close()

            // クローズ済みのドキュメント操作で IllegalStateException が発生することを確認
            assertFailsWith<IllegalStateException> {
                document.pageCount
            }
            assertFailsWith<IllegalStateException> {
                document.getPage(0)
            }
        }
    }
}
