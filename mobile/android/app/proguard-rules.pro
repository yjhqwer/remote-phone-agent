# Minimal R8 rules for Antigravity WebView
-keepclassmembers class * implements androidx.webkit.WebViewCompat$WebMessageListener {
    public *;
}
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
