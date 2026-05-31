package com.sorrowblue.kpdfium.sample

import com.sorrowblue.kpdfium.SeekableSource
import io.github.vinceglb.filekit.PlatformFile

internal expect class RealSeekableSource(file: PlatformFile) : SeekableSource
