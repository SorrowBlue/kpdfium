package com.sorrowblue.kpdfium.sample.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.openDocument
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfiumStabilityTest {

    @Test
    fun testConcurrentPdfRenderingStability() {
        runBlocking {
            // 1. Get test instrumentation context and read test PDF from Assets
            val context = InstrumentationRegistry.getInstrumentation().context
            val assetManager = context.assets
            val pdfBytes = assetManager.open("sample_test.pdf").use { it.readBytes() }
            assertNotNull("Failed to read sample_test.pdf from assets", pdfBytes)

            // 2. Open document using PdfExtractor
            val document = PdfExtractor.openDocument(pdfBytes)
            assertNotNull("Failed to open PDF document", document)
            assertEquals("Sample PDF should have 1 page", 1, document.pageCount)

            try {
                // 3. Stress Test: Launch 12 concurrent coroutines to render the same page simultaneously
                // PDFium is non-threadsafe, but our Mutex will safely serialize them preventing SIGSEGV crashes.
                val jobs = List(12) { threadId ->
                    async {
                        repeat(10) { repeatIndex ->
                            // Concurrent call to getPage and renderToPng
                            document.getPage(0).use { page ->
                                val bytes = page.render()
                                assertNotNull("Rendered page bytes should not be null", bytes)
                            }
                        }
                    }
                }

                // Wait for all concurrent render tasks to complete
                jobs.awaitAll()
            } finally {
                document.close()
            }
        }
    }
}
