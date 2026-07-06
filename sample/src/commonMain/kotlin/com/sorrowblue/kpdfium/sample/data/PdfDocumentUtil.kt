package com.sorrowblue.kpdfium.sample.data

import kotlinx.coroutines.sync.Mutex

/**
 * PDFium はマルチスレッドに対してスレッドセーフではないため、
 * アプリ内でのすべての PDFium 関連処理（オープン、ページのロード、レンダリング、クローズ等）を
 * このグローバルミューテックスで保護し、スレッド競合によるクラッシュを防止します。
 */
val pdfiumMutex = Mutex()

internal fun releasePdfDocument() {
    // キャッシュ機構は廃止されたため何もしません
}
