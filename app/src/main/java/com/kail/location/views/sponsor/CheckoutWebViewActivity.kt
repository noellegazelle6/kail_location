package com.kail.location.views.sponsor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.theme.locationTheme
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
class CheckoutWebViewActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "checkout_url"
        private const val TAG = "CheckoutWebView"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val checkoutUrl = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        setContent {
            locationTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(getString(R.string.checkout_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getString(R.string.checkout_back_desc))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
                        )
                    }
                ) { paddingValues ->
                    AndroidView(
                        modifier = Modifier
                            .padding(paddingValues)
                            .fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                WebView.setWebContentsDebuggingEnabled(true)

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(message: String, lineNumber: Int, sourceID: String) {
                                        Log.d(TAG, "$sourceID:$lineNumber: $message")
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                        if (request.isForMainFrame) return null
                                        val urlStr = request.url.toString()
                                        if (!urlStr.contains("buy.paddle.com") && !urlStr.contains("paddlejs")) return null
                                        Log.d(TAG, "intercept: ${urlStr.take(120)}...")
                                        return try {
                                            val conn = URL(urlStr).openConnection() as HttpURLConnection
                                            conn.instanceFollowRedirects = true
                                            conn.connectTimeout = 15000
                                            conn.readTimeout = 15000
                                            conn.setRequestProperty("User-Agent", desktopUA)
                                            for ((key, value) in request.requestHeaders) conn.setRequestProperty(key, value)
                                            conn.connect()

                                            val contentType = conn.contentType ?: "text/plain"
                                            if (!contentType.contains("html")) {
                                                Log.d(TAG, "skip non-html: $contentType")
                                                return null
                                            }

                                            val html = conn.inputStream.bufferedReader().readText()
                                            if (!html.contains("<head>")) {
                                                Log.d(TAG, "skip no head tag")
                                                return null
                                            }

                                            val injected = """
                                                <script>
                                                (function(){
                                                    try{Object.defineProperty(Navigator.prototype,'maxTouchPoints',{get:function(){return 0}})}catch(e){}
                                                    try{Object.defineProperty(Navigator.prototype,'platform',{get:function(){return 'Win32'}})}catch(e){}
                                                })();
                                                </script>
                                            """.trimIndent()
                                            val modified = html.replace("<head>", "<head>$injected")
                                            Log.d(TAG, "injected OK: ${html.length} -> ${modified.length}")
                                            WebResourceResponse(contentType, "utf-8", ByteArrayInputStream(modified.toByteArray(Charsets.UTF_8)))
                                        } catch (e: Exception) {
                                            Log.w(TAG, "intercept error: ${e.message}")
                                            null
                                        }
                                    }

                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        Log.d(TAG, "onPageStarted: ${url.take(100)}")
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        Log.d(TAG, "onPageFinished: ${url.take(100)}")
                                    }
                                }

                                loadUrl(checkoutUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}
