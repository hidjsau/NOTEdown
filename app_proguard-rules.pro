# WebView <-> JavaScript bridge must keep its annotated methods
-keepclassmembers class com.notes.app.AppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-dontwarn org.jetbrains.annotations.**
