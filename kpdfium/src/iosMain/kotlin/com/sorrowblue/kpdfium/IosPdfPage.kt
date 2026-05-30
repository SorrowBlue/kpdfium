@file:OptIn(ExperimentalForeignApi::class)
package com.sorrowblue.kpdfium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import kotlinx.io.Sink
import kotlinx.io.write
import com.sorrowblue.kpdfium.native.*
import platform.CoreGraphics.*
import platform.CoreFoundation.*
import platform.ImageIO.*
import platform.posix.memcpy

internal class IosPdfPage(
    private val docPtr: FPDF_DOCUMENT,
    override val pageIndex: Int
) : PdfPage {

    private val pagePtr: FPDF_PAGE = FPDF_LoadPage(docPtr, pageIndex)
        ?: throw IllegalArgumentException("Failed to load PDF page $pageIndex on iOS")

    override val width: Int 
        get() = FPDF_GetPageWidthF(pagePtr).toInt()

    override val height: Int 
        get() = FPDF_GetPageHeightF(pagePtr).toInt()

    override suspend fun renderToPng(scale: Float): ByteArray = withContext(Dispatchers.IO) {
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        // 1. Create PDFium bitmap
        val bitmap = FPDFBitmap_CreateEx(targetWidth, targetHeight, 4, null, targetWidth * 4)
            ?: throw IllegalStateException("Failed to create FPDF_BITMAP of size ${targetWidth}x${targetHeight}")

        try {
            // Fill background with white (0xFFFFFFFF)
            FPDFBitmap_FillRect(bitmap, 0, 0, targetWidth, targetHeight, 0xFFFFFFFFU)
            // Render page to bitmap
            FPDF_RenderPageBitmap(bitmap, pagePtr, 0, 0, targetWidth, targetHeight, 0, 0)

            val buffer = FPDFBitmap_GetBuffer(bitmap)
                ?: throw IllegalStateException("Failed to get buffer from FPDF_BITMAP")
            val bufferSize = (targetWidth * targetHeight * 4).toULong()

            // 2. Wrap buffer in CGDataProvider via CFData (Robust memory safety!)
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
            // kCGBitmapByteOrder32Little = 2 << 12 = 8192
            val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or 8192U

            val cgImage = CGImageCreate(
                width = targetWidth.toULong(),
                height = targetHeight.toULong(),
                bitsPerComponent = 8UL,
                bitsPerPixel = 32UL,
                bytesPerRow = (targetWidth * 4).toULong(),
                space = colorSpace,
                bitmapInfo = bitmapInfo,
                provider = dataProvider,
                decode = null,
                shouldInterpolate = true,
                intent = CGColorRenderingIntent.kCGRenderingIntentDefault
            ) ?: throw IllegalStateException("Failed to create CGImage")

            // 3. Compress CGImage into PNG CFData
            val mutableData = CFDataCreateMutable(null, 0)
                ?: throw IllegalStateException("Failed to create CFData")
            
            val pngType = CFStringCreateWithCString(null, "public.png", kCFStringEncodingUTF8)
                ?: throw IllegalStateException("Failed to create UTType PNG string")

            val destination = CGImageDestinationCreateWithData(mutableData, pngType, 1UL, null)
            if (destination == null) {
                CFRelease(pngType)
                CFRelease(mutableData)
                CGImageRelease(cgImage)
                CGColorSpaceRelease(colorSpace)
                CGDataProviderRelease(dataProvider)
                throw IllegalStateException("Failed to create CGImageDestination")
            }

            CGImageDestinationAddImage(destination, cgImage, null)
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

            // Cleanup resources
            CFRelease(pngType)
            CFRelease(mutableData)
            CFRelease(destination)
            CGImageRelease(cgImage)
            CGColorSpaceRelease(colorSpace)
            CGDataProviderRelease(dataProvider)

            result
        } finally {
            FPDFBitmap_Destroy(bitmap)
        }
    }

    override suspend fun renderToPng(scale: Float, sink: Sink) {
        val bytes = renderToPng(scale)
        sink.write(bytes)
    }

    override fun close() {
        FPDF_ClosePage(pagePtr)
    }
}
