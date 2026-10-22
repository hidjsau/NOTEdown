package com.notes.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

private const val HOST = "appassets.androidplatform.net"
private const val ORIGIN = "https://$HOST"
private const val START_URL = "$ORIGIN/assets/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NotesScreen() {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val pending = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }

    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        pending[0]?.onReceiveValue(uris)
        pending[0] = null
    }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else (context as? Activity)?.finish()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .imePadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val loader = WebViewAssetLoader.Builder()
                    .setDomain(HOST)
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .build()
                val cdn = CdnCache(File(ctx.filesDir, "cdn"))

                if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = false

                    addJavascriptInterface(AppBridge(ctx), "NotesNative")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val u = request.url
                            loader.shouldInterceptRequest(u)?.let { return it }
                            if (request.method == "GET" && u.scheme == "https" && u.host in CdnCache.HOSTS) {
                                return cdn.get(u.toString())
                            }
                            return null
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val u = request.url
                            if (u.scheme == "https" && u.host == HOST) return false
                            if (u.scheme == "blob" || u.scheme == "data" || u.scheme == "about") return false
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: ActivityNotFoundException) {
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            if (url != null && url.startsWith(ORIGIN)) {
                                view.evaluateJavascript(AppBridge.INJECT_JS, null)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView,
                            filePathCallback: ValueCallback<Array<Uri>>,
                            fileChooserParams: FileChooserParams
                        ): Boolean {
                            pending[0]?.onReceiveValue(null)
                            pending[0] = filePathCallback
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(
                                    Intent.EXTRA_ALLOW_MULTIPLE,
                                    fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                                )
                            }
                            return try {
                                chooser.launch(intent)
                                true
                            } catch (e: Exception) {
                                pending[0] = null
                                filePathCallback.onReceiveValue(null)
                                false
                            }
                        }
                    }

                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(this, AppBridge.INJECT_JS, setOf(ORIGIN))
                    }

                    loadUrl(START_URL)
                }
            },
            update = { if (webView !== it) webView = it },
            onRelease = { it.destroy() }
        )
    }
}
