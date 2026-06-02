package com.sorrowblue.kpdfium

import kotlinx.coroutines.runBlocking
import kotlinx.io.asSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfExtractorJvmTest {

    private fun loadTestPdf(): ByteArray {
        val pdfStream = PdfExtractorJvmTest::class.java.classLoader.getResourceAsStream("sample_test.pdf")
        assertNotNull(pdfStream, "sample_test.pdf resource was not found")
        return pdfStream.use { it.readBytes() }
    }

    @Test
    fun testOpenDocumentAndGetPageProperties() {
        runBlocking {
            val pdfBytes = loadTestPdf()
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
        runBlocking {
            val pdfBytes = loadTestPdf()
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
        runBlocking {
            val pdfBytes = loadTestPdf()
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
    fun testRenderPageToWebpUnsupported() {
        runBlocking {
            val pdfBytes = loadTestPdf()
            val document = PdfExtractor.openDocument(pdfBytes)
            val page = document.getPage(0)

            try {
                // JVMでは WEBP 形式は未サポートなので例外を投げるべき
                assertFailsWith<UnsupportedOperationException> {
                    page.render(dpi = DPI_STANDARD, format = ImageFormat.WEBP)
                }
            } finally {
                page.close()
                document.close()
            }
        }
    }

    @Test
    fun testRenderInvalidDpi() {
        runBlocking {
            val pdfBytes = loadTestPdf()
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
        runBlocking {
            val pdfBytes = loadTestPdf()
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
        val pdfStream = PdfExtractorJvmTest::class.java.classLoader.getResourceAsStream("large_test.pdf")
        if (pdfStream == null) {
            println("large_test.pdf is not available, skipping testOpenLargeDocument")
            return
        }

        runBlocking {
            // InputStreamSeekableSource を用いてメモリ効率良くドキュメントを開く
            val source = InputStreamSeekableSource.create(pdfStream)
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
                        assertTrue(imageBytes.isNotEmpty(), "Page $i image bytes should not be empty")
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
}
