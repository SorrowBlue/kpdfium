@file:OptIn(ExperimentalForeignApi::class)
package com.sorrowblue.kpdfium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import com.sorrowblue.kpdfium.native.*
import platform.posix.memcpy

public actual object PdfExtractor {
    init {
        FPDF_InitLibrary()
    }

    public actual suspend fun openDocument(source: SeekableSource): PdfDocument =
        withContext(Dispatchers.IO) {
            val fileAccess = nativeHeap.alloc<FPDF_FILEACCESS>()
            val stableRef = StableRef.create(source)
            
            fileAccess.m_FileLen = source.length().toULong()
            fileAccess.m_Param = stableRef.asCPointer()
            fileAccess.m_GetBlock = staticCFunction { param, position, pBuf, size ->
                if (param == null || pBuf == null) return@staticCFunction 0
                try {
                    val ref = param.asStableRef<SeekableSource>()
                    val src = ref.get()
                    src.seek(position.toLong())
                    
                    val buffer = ByteArray(size.toInt())
                    val readBytes = src.read(buffer, 0, size.toInt())
                    if (readBytes > 0) {
                        buffer.usePinned { pinned ->
                            memcpy(pBuf, pinned.addressOf(0), readBytes.toULong())
                        }
                        1
                    } else {
                        0
                    }
                } catch (e: Exception) {
                    0
                }
            }
            
            val docPtr = FPDF_LoadCustomDocument(fileAccess.ptr, null)
            if (docPtr == null) {
                stableRef.dispose()
                nativeHeap.free(fileAccess)
                throw IllegalArgumentException("Failed to parse PDF document via native C-Interop FPDF_FILEACCESS iOS")
            }
            IosPdfDocument(docPtr, source, stableRef, fileAccess.ptr)
        }
}
