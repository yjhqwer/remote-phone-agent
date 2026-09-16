package top.yjhapp.antigravity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var statusBarBackdrop: View? = null
    private var uploadCallback: ValueCallback<Array<Uri>>? = null

    // Target Antigravity Google Web Console
    private val targetUrl = "https://antigravity.google.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(window)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }

        setupWebViewShell()

        if (savedInstanceState == null) {
            webView?.loadUrl(targetUrl)
        } else {
            webView?.restoreState(savedInstanceState)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleBack()
    }

    private fun handleBack() {
        val current = webView
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else {
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewShell() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val initialChromeColor = Color.BLACK
        applyStatusBarContrast(window, initialChromeColor)

        val backdrop = View(this).apply {
            setBackgroundColor(initialChromeColor)
        }
        statusBarBackdrop = backdrop

        val browser = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        webView = browser

        configureWebSettings(browser)
        configureCookieManager(browser)
        setupWebClients(browser)

        root.addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
        setContentView(root)

        // Safe Area Insets and Keyboard (IME) handling without external library
        root.setOnApplyWindowInsetsListener { _, insets ->
            val topSafe = resolveTopSafeInset(insets)
            val imeBottom = resolveImeInset(insets)

            browser.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = topSafe
                bottomMargin = imeBottom
            }

            backdrop.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                topSafe,
                Gravity.TOP
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) WindowInsets.CONSUMED
            else insets.consumeSystemWindowInsets()
        }
        root.requestApplyInsets()
    }

    private fun configureCookieManager(browser: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Critical for Google OAuth redirect flows across accounts.google.com and antigravity.google.com
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

            // Google OAuth Normalization: Strip '; wv' and 'Version/X.X' to bypass 403: disallowed_useragent
            val defaultUa = userAgentString
            val cleanUa = defaultUa
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
            userAgentString = cleanUa

            // Security Hardening
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false

            // Viewport & Popup support
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    private fun setupWebClients(browser: WebView) {
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Allow Google OAuth and Antigravity domains internally
                if (url.contains("google.com") || url.contains("google.cn") || url.contains("gstatic.com")) {
                    return false
                }
                // External links opened in system browser
                if (request.hasGesture() && (request.url.scheme == "https" || request.url.scheme == "http")) {
                    openExternalBrowser(request.url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Persist session cookies immediately
                CookieManager.getInstance().flush()
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel() // Strict TLS enforcement
            }
        }

        browser.webChromeClient = object : WebChromeClient() {
            // Camera / Image / File selector
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean = openFileChooser(filePathCallback, params)

            // Google OAuth Popups (target="_blank" or window.open)
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

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        popupDialog.dismiss()
                        window?.destroy()
                    }
                }

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView?, url: String?) {
                        super.onPageFinished(v, url)
                        CookieManager.getInstance().flush()
                    }
                }

                popupDialog.setOnDismissListener {
                    popupWebView.destroy()
                }

                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                popupDialog.show()
                return true
            }
        }
    }

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = callback

        val mimeTypes = params.acceptTypes
            ?.flatMap { it.split(',') }
            ?.map { it.trim().lowercase() }
            ?.filter { it.contains('/') }
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
            startActivityForResult(Intent.createChooser(intent, "Select Attachment"), REQUEST_FILE_CHOOSER)
            true
        } catch (_: ActivityNotFoundException) {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = null
            false
        }
    }

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

    @Suppress("DEPRECATION")
    private fun configureEdgeToEdge(window: Window) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    @Suppress("DEPRECATION")
    private fun applyStatusBarContrast(window: Window, color: Int) {
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        val darkIcons = red * 299 + green * 587 + blue * 114 >= 186_000

        window.decorView.systemUiVisibility = if (darkIcons) {
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveTopSafeInset(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val status = insets.getInsets(WindowInsets.Type.statusBars()).top
            val cutout = insets.getInsets(WindowInsets.Type.displayCutout()).top
            maxOf(status, cutout)
        } else {
            maxOf(insets.systemWindowInsetTop, insets.displayCutout?.safeInsetTop ?: 0)
        }

    @Suppress("DEPRECATION")
    private fun resolveImeInset(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            maxOf(0, ime - nav)
        } else {
            maxOf(0, insets.systemWindowInsetBottom - insets.stableInsetBottom)
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
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_FILE_CHOOSER = 4101
    }
}
