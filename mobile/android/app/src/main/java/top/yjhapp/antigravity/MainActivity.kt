package top.yjhapp.antigravity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var statusBarBackdrop: View? = null
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var activePopupDialog: Dialog? = null

    // Target Antigravity Google Web Console
    private val targetUrl = "https://antigravity.google.com"

    // Dynamic Predictive Back State tracking (Android 13/14/15)
    private var isBackCallbackRegistered = false
    private val backCallback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.window.OnBackInvokedCallback {
                val current = webView
                if (current != null && current.canGoBack()) {
                    current.goBack()
                }
            }
        } else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(window)
        unlockHighRefreshRate(window)
        setupWebViewShell()

        if (savedInstanceState == null) {
            webView?.loadUrl(targetUrl)
        } else {
            val restored = webView?.restoreState(savedInstanceState)
            if (restored == null) {
                webView?.loadUrl(targetUrl)
            }
        }
    }

    /**
     * Unlock 144Hz / maximum display panel refresh rate for realme GT Neo5 (ColorOS).
     */
    private fun unlockHighRefreshRate(window: Window) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = display ?: (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate >= 90f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate >= 90f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            }
        } catch (_: Exception) {
            // Graceful fallback if unsupported
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val popup = activePopupDialog
        if (popup != null && popup.isShowing) {
            popup.dismiss()
            return
        }
        val current = webView
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Dynamically registers OnBackInvokedCallback only when WebView can go back.
     * When at root, unregisters so native Android 14/15 Predictive Back to Home plays smoothly.
     */
    private fun updateBackInvokedCallbackState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val canGoBack = webView?.canGoBack() == true
            if (canGoBack && !isBackCallbackRegistered) {
                backCallback?.let {
                    onBackInvokedDispatcher.registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        it
                    )
                    isBackCallbackRegistered = true
                }
            } else if (!canGoBack && isBackCallbackRegistered) {
                backCallback?.let {
                    onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                    isBackCallbackRegistered = false
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewShell() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val initialChromeColor = Color.BLACK
        val backdrop = View(this).apply {
            setBackgroundColor(initialChromeColor)
        }
        statusBarBackdrop = backdrop

        val browser = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isLongClickable = true
            isHapticFeedbackEnabled = true
        }
        webView = browser

        configureWebSettings(browser)
        configureCookieManager(browser)
        setupWebClients(browser)

        root.addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
        setContentView(root)

        // Safe Area Insets and Keyboard (IME) handling:
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val topSafe = maxOf(statusInsets.top, cutoutInsets.top)
            val bottomInset = maxOf(navInsets.bottom, imeInsets.bottom)

            backdrop.updateLayoutParams<FrameLayout.LayoutParams> {
                height = topSafe
            }

            browser.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = topSafe
                bottomMargin = bottomInset
            }

            // Return insets unconsumed to allow Chromium's visual viewport to receive insets
            insets
        }

        // Frame-synchronized 144Hz keyboard animation to avoid jitter/flash
        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val hasImeAnim = runningAnimations.any {
                        (it.typeMask and WindowInsetsCompat.Type.ime()) != 0
                    }
                    if (hasImeAnim) {
                        val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                        val bottomInset = maxOf(navInsets.bottom, imeInsets.bottom)

                        browser.updateLayoutParams<FrameLayout.LayoutParams> {
                            bottomMargin = bottomInset
                        }
                    }
                    return insets
                }
            }
        )

        updateSystemBarContrast((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    }

    private fun configureCookieManager(browser: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, true)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebSettings(browser: WebView) {
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT

            // High Refresh Rate & Rendering Optimization
            offscreenPreRaster = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

            // Lock textZoom = 100 to prevent system font scale distortion from breaking code blocks/chat UI
            textZoom = 100

            // Dark Mode: Google Antigravity provides first-class native CSS dark mode.
            // Explicitly disallow algorithmic darkening to prevent color inversion glitches on code/badges.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
            }

            // Google OAuth Normalization: Strip '; wv' and 'Version/X.X' to bypass 403: disallowed_useragent
            val defaultUa = userAgentString
            val cleanUa = defaultUa
                .replace("; wv", "")
                .replace(Regex("Version/[0-9.]+\\s?"), "")
            userAgentString = cleanUa

            // Security Hardening
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false

            // Viewport & Window controls
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        // Native Clipboard Bridge for reliable code block copying
        browser.addJavascriptInterface(ClipboardBridge(this), "AntigravityNative")

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    private fun setupWebClients(browser: WebView) {
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isTrustedInternalOrAuthHost(uri)) {
                    return false
                }
                if (request.hasGesture() && (uri.scheme == "https" || uri.scheme == "http")) {
                    openExternalBrowser(uri)
                    return true
                }
                return false
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                updateBackInvokedCallbackState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateBackInvokedCallbackState()
                CookieManager.getInstance().flush()
                // Inject clipboard fallback polyfill
                view?.evaluateJavascript(CLIPBOARD_POLYFILL_JS, null)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel() // Strict TLS enforcement
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@MainActivity, "Connection failed. Check network.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean = openFileChooser(filePathCallback, params)

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false

                val popupWebView = WebView(this@MainActivity)
                configureWebSettings(popupWebView)
                CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)

                val popupDialog = Dialog(this@MainActivity, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
                    setContentView(popupWebView)
                    window?.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                }
                activePopupDialog = popupDialog

                popupDialog.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        if (popupWebView.canGoBack()) {
                            popupWebView.goBack()
                            true
                        } else {
                            popupDialog.dismiss()
                            true
                        }
                    } else false
                }

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        popupDialog.dismiss()
                    }

                    override fun onShowFileChooser(
                        vw: WebView,
                        cb: ValueCallback<Array<Uri>>,
                        fp: FileChooserParams
                    ): Boolean = openFileChooser(cb, fp)
                }

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                        val uri = req.url
                        val uriString = uri.toString()
                        // If OAuth redirects back to the main console, route back to parent WebView
                        if (uriString.startsWith(targetUrl)) {
                            popupDialog.dismiss()
                            this@MainActivity.webView?.loadUrl(uriString)
                            return true
                        }
                        if (isTrustedInternalOrAuthHost(uri)) {
                            return false
                        }
                        if (req.hasGesture()) {
                            openExternalBrowser(uri)
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(v: WebView?, url: String?) {
                        super.onPageFinished(v, url)
                        CookieManager.getInstance().flush()
                    }

                    override fun onReceivedSslError(v: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.cancel()
                    }
                }

                popupDialog.setOnDismissListener {
                    if (activePopupDialog === popupDialog) activePopupDialog = null
                    popupWebView.apply {
                        stopLoading()
                        webChromeClient = null
                        webViewClient = WebViewClient()
                        (parent as? ViewGroup)?.removeView(this)
                        destroy()
                    }
                }

                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                popupDialog.show()
                return true
            }
        }
    }

    private fun isTrustedInternalOrAuthHost(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false

        // Exact match for target console
        if (host == "antigravity.google.com") return true

        // Strict Google Identity & OAuth domains
        val isGoogleDomain = host == "google.com" || host.endsWith(".google.com") ||
            host == "google.cn" || host.endsWith(".google.cn") ||
            host == "gstatic.com" || host.endsWith(".gstatic.com") ||
            host == "googleapis.com" || host.endsWith(".googleapis.com") ||
            host == "googleusercontent.com" || host.endsWith(".googleusercontent.com") ||
            Regex("^accounts\\.google\\.[a-z.]+$").matches(host)

        if (isGoogleDomain) return true

        // Known Enterprise Workspace SSO Identity Providers
        val isCommonIdP = host.endsWith(".okta.com") ||
            host.endsWith(".onelogin.com") ||
            host == "login.microsoftonline.com" ||
            host.endsWith(".pingidentity.com") ||
            host.endsWith(".duosecurity.com") ||
            host.endsWith(".cloudflareaccess.com")

        return isCommonIdP
    }

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = callback

        val mimeTypeMap = MimeTypeMap.getSingleton()
        val mimeTypes = params.acceptTypes
            ?.flatMap { it.split(',') }
            ?.map { it.trim().lowercase() }
            ?.mapNotNull { token ->
                when {
                    token.contains('/') -> token
                    token.startsWith('.') -> mimeTypeMap.getMimeTypeFromExtension(token.removePrefix("."))
                    token.isNotEmpty() -> mimeTypeMap.getMimeTypeFromExtension(token)
                    else -> null
                }
            }
            ?.distinct()
            ?.ifEmpty { listOf("*/*") } ?: listOf("*/*")

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = if (mimeTypes.size == 1) mimeTypes.single() else "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        }

        return try {
            startActivityForResult(intent, REQUEST_FILE_CHOOSER)
            true
        } catch (_: ActivityNotFoundException) {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = null
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            val callback = uploadCallback ?: return
            uploadCallback = null
            if (resultCode != RESULT_OK || data == null) {
                callback.onReceiveValue(null)
                return
            }
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
            }
            data.data?.let { if (it !in uris) uris += it }
            callback.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        }
    }

    private fun openExternalBrowser(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureEdgeToEdge(window: Window) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isNightMode = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        updateSystemBarContrast(isNightMode)
    }

    private fun updateSystemBarContrast(isNightMode: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isNightMode
        insetsController.isAppearanceLightNavigationBars = !isNightMode
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        activePopupDialog?.let {
            if (it.isShowing) it.dismiss()
        }
        activePopupDialog = null
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            removeJavascriptInterface("AntigravityNative")
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    /**
     * Native bridge allowing web buttons to copy cleanly into Android ClipboardManager.
     */
    private class ClipboardBridge(private val context: Context) {
        @JavascriptInterface
        fun copyText(text: String): Boolean {
            return try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", text)
                clipboard.setPrimaryClip(clip)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        private const val REQUEST_FILE_CHOOSER = 4101

        private const val CLIPBOARD_POLYFILL_JS = """
(function() {
    if (window.__antigravityClipboardInjected) return;
    window.__antigravityClipboardInjected = true;
    
    const fallbackCopy = function(text) {
        if (window.AntigravityNative && window.AntigravityNative.copyText) {
            return window.AntigravityNative.copyText(text);
        }
        return false;
    };

    if (!navigator.clipboard) {
        navigator.clipboard = {};
    }
    
    const originalWriteText = navigator.clipboard.writeText ? navigator.clipboard.writeText.bind(navigator.clipboard) : null;

    navigator.clipboard.writeText = function(text) {
        if (originalWriteText) {
            return originalWriteText(text).catch(function() {
                if (fallbackCopy(text)) {
                    return Promise.resolve();
                }
                return Promise.reject(new Error("Failed to copy via native bridge"));
            });
        } else {
            if (fallbackCopy(text)) {
                return Promise.resolve();
            }
            return Promise.reject(new Error("Clipboard API not available"));
        }
    };
})();
"""
    }
}
