package com.sorrowblue.kpdfium

/**
 * テストを実行しているプラットフォームでリソースのロード（およびテスト実行）がサポートされているか。
 * ホスト上の AndroidUnitTest など、ネイティブライブラリが動作しない環境では false となります。
 */
public expect val isResourceLoadingSupported: Boolean

/**
 * テストを実行しているプラットフォームで WEBP フォーマットがサポートされているか。
 */
public expect val isWebpSupported: Boolean

/**
 * 指定されたテスト用PDFリソースをロードして ByteArray として返します。
 * ファイルが存在しない、もしくはロードに失敗した場合は null を返します。
 */
public expect fun loadTestPdf(fileName: String): ByteArray?

/**
 * 指定されたテスト用PDFリソースをメモリ効率の良い SeekableSource としてロードします。
 * ファイルが存在しない、もしくはロードに失敗した場合は null を返します。
 */
public expect fun loadTestPdfSource(fileName: String): SeekableSource?
