@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("WildcardImport")

package com.sorrowblue.kpdfium

import com.sorrowblue.kpdfium.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import platform.ImageIO.*
import platform.posix.memcpy

private const val POINTS_PER_INCH = 72.0f
private const val BYTES_PER_PIXEL = 4
private const val BITS_PER_COMPONENT = 8UL
private const val BITS_PER_PIXEL = 32UL
private const val CG_BITMAP_BYTE_ORDER_32_LITTLE = 8192U
private const val QUALITY_PERCENT = 100.0f
private const val IMAGE_COUNT_ONE = 1UL
private const val COLOR_WHITE = 0xFFFFFFFFU

internal class IosPdfPage(
    private val document: IosPdfDocument,
    private val docPtr: FPDF_DOCUMENT,
    override val pageIndex: Int
) : PdfPage {

    private val pagePtr: FPDF_PAGE = runWithPdfiumLock {
        FPDF_LoadPage(docPtr, pageIndex)
    } ?: throw IllegalArgumentException("Failed to load PDF page $pageIndex on iOS")

    private var isClosed = false

    private fun checkClosed() {
        check(!isClosed) { "PdfPage is already closed" }
    }

    override val width: Int
        get() {
            checkClosed()
            return runWithPdfiumLock { FPDF_GetPageWidthF(pagePtr).toInt() }
        }

    override val height: Int
        get() {
            checkClosed()
            return runWithPdfiumLock { FPDF_GetPageHeightF(pagePtr).toInt() }
        }

    override suspend fun render(dpi: Int, format: ImageFormat, quality: Int): ByteArray =
        document.mutex.withLock {
            checkClosed()
            require(dpi > 0) { "DPI must be greater than 0" }
            require(quality in 0..QUALITY_MAX) { "Quality must be between 0 and 100" }
            withContext(Dispatchers.IO) {
                val scale = dpi / POINTS_PER_INCH
                val targetWidth = (width * scale).toInt()
                val targetHeight = (height * scale).toInt()

                val bitmap = runWithPdfiumLock {
                    renderToBitmap(targetWidth, targetHeight)
                }
                try {
                    val cgImage = bitmap.toCGImage(targetWidth, targetHeight)
                    try {
                        cgImage.compressToByteArray(format, quality)
                    } finally {
                        CGImageRelease(cgImage)
                    }
                } finally {
                    runWithPdfiumLock {
                        FPDFBitmap_Destroy(bitmap)
                    }
                }
            }
        }

    private fun renderToBitmap(targetWidth: Int, targetHeight: Int): FPDF_BITMAP {
        val bitmap = FPDFBitmap_CreateEx(
            targetWidth,
            targetHeight,
            BYTES_PER_PIXEL,
            null,
            targetWidth * BYTES_PER_PIXEL
        )
            ?: throw IllegalStateException(
                "Failed to create FPDF_BITMAP of size ${targetWidth}x$targetHeight"
            )
        // Fill background with white
        FPDFBitmap_FillRect(bitmap, 0, 0, targetWidth, targetHeight, COLOR_WHITE.toULong())
        // Render page to bitmap
        FPDF_RenderPageBitmap(bitmap, pagePtr, 0, 0, targetWidth, targetHeight, 0, 0)
        return bitmap
    }

    @Suppress("ThrowsCount")
    private fun FPDF_BITMAP.toCGImage(targetWidth: Int, targetHeight: Int): CGImageRef {
        val buffer = FPDFBitmap_GetBuffer(this)
            ?: throw IllegalStateException("Failed to get buffer from FPDF_BITMAP")
        val bufferSize = (targetWidth * targetHeight * BYTES_PER_PIXEL).toULong()

        // Wrap buffer in CGDataProvider via CFData (Robust memory safety!)
        val cfData = CFDataCreate(null, buffer.reinterpret(), bufferSize.toLong())
            ?: throw IllegalStateException("Failed to create CFData from buffer")
        val dataProvider = CGDataProviderCreateWithCFData(cfData)
            ?: run {
                CFRelease(cfData)
                throw IllegalStateException("Failed to create CGDataProvider")
            }
        CFRelease(cfData) // CGDataProviderCreateWithCFData retains cfData, so we must release our own reference.

        val colorSpace = CGColorSpaceCreateDeviceRGB()
        // BGRA 32-bit (Premultiplied First, Little Endian)
        val bitmapInfo =
            CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or CG_BITMAP_BYTE_ORDER_32_LITTLE

        val cgImage = CGImageCreate(
            width = targetWidth.toULong(),
            height = targetHeight.toULong(),
            bitsPerComponent = BITS_PER_COMPONENT,
            bitsPerPixel = BITS_PER_PIXEL,
            bytesPerRow = (targetWidth * BYTES_PER_PIXEL).toULong(),
            space = colorSpace,
            bitmapInfo = bitmapInfo,
            provider = dataProvider,
            decode = null,
            shouldInterpolate = true,
            intent = CGColorRenderingIntent.kCGRenderingIntentDefault
        )
        CGColorSpaceRelease(colorSpace)
        CGDataProviderRelease(dataProvider)
        return cgImage ?: throw IllegalStateException("Failed to create CGImage")
    }

    private fun createUtiType(typeString: String): CFStringRef =
        CFStringCreateWithCString(null, typeString, kCFStringEncodingUTF8)
            ?: throw IllegalStateException("Failed to create UTType string")

    private fun createJpegOptions(quality: Int): CFMutableDictionaryRef? {
        val options = CFDictionaryCreateMutable(
            null,
            1,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        memScoped {
            val qualityVar = alloc<FloatVar>()
            qualityVar.value = quality / QUALITY_PERCENT
            val qualityNum = CFNumberCreate(
                null,
                kCFNumberFloatType,
                qualityVar.ptr
            )
            if (qualityNum != null) {
                CFDictionarySetValue(
                    options,
                    kCGImageDestinationLossyCompressionQuality,
                    qualityNum
                )
                CFRelease(qualityNum)
            }
        }
        return options
    }

    @Suppress("ThrowsCount")
    private fun CGImageRef.compressToByteArray(format: ImageFormat, quality: Int): ByteArray {
        val mutableData = CFDataCreateMutable(null, 0)
            ?: throw IllegalStateException("Failed to create CFData")

        val utiType: CFStringRef
        val options: CFMutableDictionaryRef?

        when (format) {
            ImageFormat.PNG -> {
                utiType = createUtiType("public.png")
                options = null
            }

            ImageFormat.JPEG -> {
                utiType = createUtiType("public.jpeg")
                options = createJpegOptions(quality)
            }

            ImageFormat.WEBP -> {
                CFRelease(mutableData)
                throw UnsupportedOperationException(
                    "WEBP format is not supported on iOS platform"
                )
            }
        }

        val destination = CGImageDestinationCreateWithData(
            mutableData,
            utiType,
            IMAGE_COUNT_ONE,
            null
        )
        if (destination == null) {
            CFRelease(utiType)
            if (options != null) CFRelease(options)
            CFRelease(mutableData)
            throw IllegalStateException("Failed to create CGImageDestination")
        }

        try {
            CGImageDestinationAddImage(destination, this, options)
            val finalizeSuccess = CGImageDestinationFinalize(destination)

            val result: ByteArray
            if (finalizeSuccess) {
                val length = CFDataGetLength(mutableData)
                val bytePtr = CFDataGetBytePtr(mutableData)
                result = ByteArray(length.toInt())
                if (length > 0) {
                    result.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), bytePtr, length.toULong())
                    }
                }
            } else {
                result = ByteArray(0)
            }
            return result
        } finally {
            CFRelease(utiType)
            if (options != null) CFRelease(options)
            CFRelease(mutableData)
            CFRelease(destination)
        }
    }

    override suspend fun render(dpi: Int, format: ImageFormat, quality: Int, sink: Sink) {
        checkClosed()
        val bytes = render(dpi, format, quality)
        sink.write(bytes)
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        runWithPdfiumLock {
            FPDF_ClosePage(pagePtr)
        }
    }
}
