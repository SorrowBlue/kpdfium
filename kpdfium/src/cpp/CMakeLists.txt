cmake_minimum_required(VERSION 3.12)
project(pdfium-jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. Shared headers directory path
set(PDFIUM_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/include")

# 2. Configure JDK JNI Headers for JVM/Desktop targets
if(NOT ANDROID)
    find_package(JNI REQUIRED)
    include_directories(${JNI_INCLUDE_DIRS})
endif()

include_directories(${PDFIUM_INCLUDE_DIR})

# 3. Define precompiled PDFium library based on target platform
add_library(pdfium SHARED IMPORTED)

if(ANDROID)
    # Android uses .so from standard jniLibs
    set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/../../android/src/main/jniLibs/${ANDROID_ABI}/libpdfium.so"
    )
elseif(WIN32)
    # Windows Desktop uses pdfium.dll.lib for linking, and actual dll is pdfium.dll
    set_target_properties(pdfium PROPERTIES 
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/pdfium/win-x64/pdfium.dll"
        IMPORTED_IMPLIB "${CMAKE_CURRENT_SOURCE_DIR}/pdfium/win-x64/pdfium.dll.lib"
    )
elseif(APPLE)
    # macOS uses libpdfium.dylib (detect Apple Silicon arm64 vs Intel x64)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64" OR CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
        set(PLATFORM_DIR "mac-arm64")
    else()
        set(PLATFORM_DIR "mac-x64")
    endif()
    set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/pdfium/${PLATFORM_DIR}/libpdfium.dylib"
    )
else()
    # Linux uses libpdfium.so (detect arm64 vs x64)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64" OR CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
        set(PLATFORM_DIR "linux-arm64")
    else()
        set(PLATFORM_DIR "linux-x64")
    endif()
    set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/pdfium/${PLATFORM_DIR}/libpdfium.so"
    )
endif()

# 4. Define the shared JNI bridge library target
add_library(pdfium-jni SHARED
    "${CMAKE_CURRENT_SOURCE_DIR}/pdfium-jni.cpp"
)

# 5. Link libraries and platform-specific dependencies
if(ANDROID)
    target_link_libraries(pdfium-jni PRIVATE
        pdfium
        jnigraphics # REQUIRED for AndroidBitmap_lockPixels
        log         # REQUIRED for Android native logging
    )
else()
    target_link_libraries(pdfium-jni PRIVATE
        pdfium
    )
    
    # On Windows JVM, ensure output DLL does not have 'lib' prefix
    if(WIN32)
        set_target_properties(pdfium-jni PROPERTIES
            PREFIX ""
            SUFFIX ".dll"
        )
    endif()
endif()
