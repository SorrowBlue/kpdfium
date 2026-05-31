#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "fpdfview.h"

#ifdef __ANDROID__
#include <android/bitmap.h>
#endif

// Global pointer to the Java Virtual Machine for JNI callbacks on background threads
static JavaVM* g_JavaVM = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_JavaVM = vm;
    return JNI_VERSION_1_6;
}

// C++ context structure to hold JNI states and method references
struct JniFileAccessParam {
    jobject globalSourceRef;
    jclass sourceClass;
    jmethodID seekMethodId;
    jmethodID readMethodId;
};

// C++ wrapper to group PDFium document pointer with its file access JNI callbacks and structures
struct PdfDocumentWrapper {
    FPDF_DOCUMENT doc;
    FPDF_FILEACCESS* fileAccess;
    JniFileAccessParam* param;
};

// PDFium file block request callback implemented in C++ JNI
int JniGetBlockCallback(void* param, unsigned long pos, unsigned char* clientBuf, unsigned long size) {
    JniFileAccessParam* p = (JniFileAccessParam*)param;
    if (!p) return 0;
    
    JNIEnv* env = nullptr;
    bool attached = false;
    
    // Dynamically retrieve the current thread's JNIEnv, attaching the thread to the VM if needed
    int envStat = g_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (envStat == JNI_EDETACHED) {
#ifdef __ANDROID__
        g_JavaVM->AttachCurrentThread(&env, nullptr);
#else
        g_JavaVM->AttachCurrentThread((void**)&env, nullptr);
#endif
        attached = true;
    }
    
    // 1. Invoke stateful seek(pos) synchronously on Kotlin SeekableSource
    env->CallVoidMethod(p->globalSourceRef, p->seekMethodId, (jlong)pos);
    
    // 2. Allocate a local byte array and invoke read(buffer, 0, size)
    jbyteArray buffer = env->NewByteArray(size);
    jint bytesRead = env->CallIntMethod(p->globalSourceRef, p->readMethodId, buffer, 0, (jint)size);
    
    if (bytesRead > 0) {
        // Copy Java byte array contents back directly into PDFium C clientBuf pointer
        jbyte* body = env->GetByteArrayElements(buffer, nullptr);
        memcpy(clientBuf, body, bytesRead);
        env->ReleaseByteArrayElements(buffer, body, JNI_ABORT);
    }
    
    env->DeleteLocalRef(buffer);
    
    if (attached) {
        g_JavaVM->DetachCurrentThread();
    }
    
    return (bytesRead > 0) ? 1 : 0; // Return non-zero on success, zero on failure/EOF
}

extern "C" {

JNIEXPORT void JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1InitLibrary(JNIEnv* env, jobject thiz) {
    FPDF_InitLibrary();
}

JNIEXPORT void JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1DestroyLibrary(JNIEnv* env, jobject thiz) {
    FPDF_DestroyLibrary();
}

JNIEXPORT jlong JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1LoadCustomDocument(
    JNIEnv* env, jobject thiz, jobject source, jlong length, jstring password
) {
    // Initialize JNI method caching context
    JniFileAccessParam* param = new JniFileAccessParam();
    param->globalSourceRef = env->NewGlobalRef(source); // Pin Kotlin object to prevent GC
    
    jclass sourceClass = env->GetObjectClass(source);
    param->sourceClass = (jclass)env->NewGlobalRef(sourceClass);
    param->seekMethodId = env->GetMethodID(sourceClass, "seek", "(J)V");
    param->readMethodId = env->GetMethodID(sourceClass, "read", "([BII)I");
    
    if (!param->seekMethodId || !param->readMethodId) {
        env->DeleteGlobalRef(param->globalSourceRef);
        env->DeleteGlobalRef(param->sourceClass);
        delete param;
        return 0;
    }
    
    // Initialize PDFium FPDF_FILEACCESS
    FPDF_FILEACCESS* fileAccess = (FPDF_FILEACCESS*)malloc(sizeof(FPDF_FILEACCESS));
    fileAccess->m_FileLen = (unsigned long)length;
    fileAccess->m_GetBlock = JniGetBlockCallback;
    fileAccess->m_Param = param;
    
    const char* pwd = password ? env->GetStringUTFChars(password, nullptr) : nullptr;
    FPDF_DOCUMENT doc = FPDF_LoadCustomDocument(fileAccess, pwd);
    if (pwd) env->ReleaseStringUTFChars(password, pwd);
    
    if (!doc) {
        // Clean up immediately in case of loading failure
        env->DeleteGlobalRef(param->globalSourceRef);
        env->DeleteGlobalRef(param->sourceClass);
        delete param;
        free(fileAccess);
        return 0;
    }
    
    // Wrap native resources together so they are safely disposed during CloseDocument
    PdfDocumentWrapper* wrapper = new PdfDocumentWrapper();
    wrapper->doc = doc;
    wrapper->fileAccess = fileAccess;
    wrapper->param = param;
    
    return (jlong)wrapper;
}

JNIEXPORT void JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1CloseDocument(JNIEnv* env, jobject thiz, jlong docPtr) {
    PdfDocumentWrapper* wrapper = (PdfDocumentWrapper*)docPtr;
    if (wrapper) {
        FPDF_CloseDocument(wrapper->doc);
        
        // CRITICAL: Clean up JNI global references to avoid memory leaks
        env->DeleteGlobalRef(wrapper->param->globalSourceRef);
        env->DeleteGlobalRef(wrapper->param->sourceClass);
        
        delete wrapper->param;
        free(wrapper->fileAccess);
        delete wrapper;
    }
}

JNIEXPORT jint JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1GetPageCount(JNIEnv* env, jobject thiz, jlong docPtr) {
    PdfDocumentWrapper* wrapper = (PdfDocumentWrapper*)docPtr;
    return wrapper ? FPDF_GetPageCount(wrapper->doc) : 0;
}

JNIEXPORT jlong JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1LoadPage(JNIEnv* env, jobject thiz, jlong docPtr, jint pageIndex) {
    PdfDocumentWrapper* wrapper = (PdfDocumentWrapper*)docPtr;
    return wrapper ? (jlong)FPDF_LoadPage(wrapper->doc, pageIndex) : 0;
}

JNIEXPORT void JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1ClosePage(JNIEnv* env, jobject thiz, jlong pagePtr) {
    FPDF_ClosePage((FPDF_PAGE)pagePtr);
}

JNIEXPORT jfloat JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1GetPageWidthF(JNIEnv* env, jobject thiz, jlong pagePtr) {
    return FPDF_GetPageWidthF((FPDF_PAGE)pagePtr);
}

JNIEXPORT jfloat JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1GetPageHeightF(JNIEnv* env, jobject thiz, jlong pagePtr) {
    return FPDF_GetPageHeightF((FPDF_PAGE)pagePtr);
}

#ifdef __ANDROID__
JNIEXPORT void JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1RenderPageBitmap(
    JNIEnv* env, jobject thiz, jlong pagePtr, jobject bitmap, jint startX, jint startY, jint sizeX, jint sizeY, jint rotate, jint flags
) {
    AndroidBitmapInfo info;
    void* pixels;
    
    // Obtain information about the Bitmap and lock its native pixels memory block
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    
    // Wrap locked raw bitmap memory into FPDF_BITMAP
    FPDF_BITMAP fpdfBitmap = FPDFBitmap_CreateEx(
        info.width, info.height,
        FPDFBitmap_BGRA, // ARGB_8888 maps natively to little-endian BGRA format in C++ memory
        pixels,
        info.stride
    );
    
    // Blazing-fast native rendering directly into locked bitmap memory (Zero-copy!)
    FPDF_RenderPageBitmap(fpdfBitmap, (FPDF_PAGE)pagePtr, startX, startY, sizeX, sizeY, rotate, flags);
    
    FPDFBitmap_Destroy(fpdfBitmap);
    AndroidBitmap_unlockPixels(env, bitmap); // Release native pixel lock
}
#else
// Desktop JVM rendering into Direct ByteBuffer (Zero-copy!)
JNIEXPORT void JNICALL Java_com_sorrowblue_kpdfium_PdfiumJni_FPDF_1RenderPageBitmapJvm(
    JNIEnv* env, jobject thiz, jlong pagePtr, jobject byteBuffer, jint targetWidth, jint targetHeight, jint rotate, jint flags
) {
    void* pixels = env->GetDirectBufferAddress(byteBuffer);
    if (!pixels) return;
    
    // Wrap direct byte buffer memory into FPDF_BITMAP
    FPDF_BITMAP fpdfBitmap = FPDFBitmap_CreateEx(
        targetWidth, targetHeight,
        FPDFBitmap_BGRA,
        pixels,
        targetWidth * 4 // Stride (width * 4 bytes per pixel)
    );
    
    FPDFBitmap_FillRect(fpdfBitmap, 0, 0, targetWidth, targetHeight, 0xFFFFFFFF);
    FPDF_RenderPageBitmap(fpdfBitmap, (FPDF_PAGE)pagePtr, 0, 0, targetWidth, targetHeight, rotate, flags);
    
    FPDFBitmap_Destroy(fpdfBitmap);
}
#endif

}
