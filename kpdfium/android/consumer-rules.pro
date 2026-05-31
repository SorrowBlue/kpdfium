# JNIのC++側からリフレクションで呼び出すため、SeekableSourceインターフェースとそのメソッドを維持する
-keep interface com.sorrowblue.kpdfium.SeekableSource {
    int read(byte[], int, int);
    void seek(long);
}

# SeekableSourceを実装するすべてのクラスの該当メソッドも維持する
-keep class * implements com.sorrowblue.kpdfium.SeekableSource {
    int read(byte[], int, int);
    void seek(long);
}
