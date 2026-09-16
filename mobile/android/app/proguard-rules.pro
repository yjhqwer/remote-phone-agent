# R8 / ProGuard Optimization Rules for Antigravity WebView Client

# Retain source lines and attributes for production stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes JavascriptInterface
-keepattributes InnerClasses,EnclosingMethod

# Keep JavaScript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# AndroidX WebKit listeners
-keepclassmembers class * implements androidx.webkit.WebViewCompat$WebMessageListener {
    public *;
}

# Prevent stripping of WebChromeClient and WebViewClient internal callbacks
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

# Suppress warnings from AndroidX WebKit optional APIs
-dontwarn androidx.webkit.**
