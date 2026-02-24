package org.siros.wwwallet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings.LOAD_NO_CACHE
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import ch.qos.logback.classic.android.BasicLogcatConfigurator
import org.siros.wwwallet.bluetooth.BleClientHandler
import org.siros.wwwallet.bluetooth.BleServerHandler
import org.siros.wwwallet.bridging.DebugMenuHandler
import org.siros.wwwallet.bridging.WalletJsBridge
import org.siros.wwwallet.credentials.AndroidContainer
import org.siros.wwwallet.credentials.YubicoContainer
import org.siros.wwwallet.logging.YOLOLogger
import org.siros.wwwallet.webkit.WalletWebChromeClient
import org.siros.wwwallet.webkit.WalletWebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ui.EnterBaseUrlDialog

class MainActivity : ComponentActivity() {
    init {
        BasicLogcatConfigurator.configureDefaultContext()
    }

    val vm: MainViewModel by viewModels<MainViewModel>()

    private val webViewClient: WebViewClient =
        WalletWebViewClient(this) { description ->
            vm.errorReceived(
                description,
            )
        }

    private val webChromeClient: WebChromeClient = WalletWebChromeClient(this)

    private val javascriptInterfaceCreator: (WebView) -> WalletJsBridge = { webView ->
        WalletJsBridge(
            webView,
            Dispatchers.Main,
            YubicoContainer(activity = this),
            AndroidContainer(context = this),
            BleClientHandler(activity = this),
            BleServerHandler(activity = this),
            if (BuildConfig.DEBUG) {
                DebugMenuHandler(
                    context = this,
                    browseTo = {
                        lifecycleScope.launch {
                            vm.setBaseUrl(it)
                            vm.browseToUrl(it)
                        }
                    },
                    updateBaseUrl = { vm.updateBaseUrl() },
                    copyToClipboard = { vm.copyToClipboard(it) },
                )
            } else {
                null
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        vm.activity = this // 👀 (NFC)

        onBackPressedDispatcher.addCallback(
            owner = this,
        ) { vm.onBackPressed() }

        when (intent.scheme) {
            "https", "openid4vp", "haip", "wwwallet" -> vm.parseIntent(intent)
            null -> Unit
            else -> YOLOLogger.e(tagForLog, "Cannot handle ${intent.scheme}.")
        }

        vm.openedFromShortcut(intent.identifier)

        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                enableEdgeToEdge()

                val url by vm.url.collectAsState()
                val updateBaseUrl by vm.updateBaseUrl.collectAsState()

                Scaffold { paddingValues ->
                    Column(
                        modifier =
                            Modifier
                                .padding(paddingValues)
                                .fillMaxHeight(),
                    ) {
                        WebView(
                            activity = this@MainActivity,
                            webViewClient = webViewClient,
                            webChromeClient = webChromeClient,
                            javascriptInterfaceCreator = javascriptInterfaceCreator,
                            javascriptInterfaceName = WalletJsBridge.JAVASCRIPT_BRIDGE_NAME,
                            url,
                        ) { url ->
                            lifecycleScope.launch {
                                vm.browseToUrl(url)
                            }
                        }
                    }

                    updateBaseUrl?.let { reason ->
                        EnterBaseUrlDialog(
                            title = stringResource(R.string.shortcut_open_custom),
                            hint =
                                when (reason) {
                                    is MainViewModel.UpdateReason.WebpageError -> stringResource(R.string.shortcut_open_custom_by_error, reason.errorMessage)
                                    is MainViewModel.UpdateReason.DeeplinkRequest -> stringResource(R.string.shortcut_open_custom_from_deeplink)
                                    is MainViewModel.UpdateReason.UserRequest -> stringResource(R.string.shortcut_open_custom_by_user)
                                },
                            currentBaseUrl = runBlocking { vm.getBaseUrl() },
                            onCanceled = { vm.updateBaseUrlCanceled() },
                            onUrlEntered = {
                                lifecycleScope.launch {
                                    val url = vm.setBaseUrl(it)
                                    vm.browseToUrl(url)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.WebView(
    activity: Activity,
    webViewClient: WebViewClient,
    webChromeClient: WebChromeClient,
    javascriptInterfaceCreator: (WebView) -> Any,
    javascriptInterfaceName: String,
    url: String,
    setUrl: (String) -> Unit,
) {
    AndroidView(
        modifier =
            Modifier.wrapContentHeight(
                align = Alignment.Top,
            ),
        factory =
            createWebViewFactory(
                activity = activity,
                webViewClient = webViewClient,
                webChromeClient = webChromeClient,
                javascriptInterfaceCreator = javascriptInterfaceCreator,
                javascriptInterfaceName = javascriptInterfaceName,
            ),
        update = { webView: WebView ->
            updateWebView(
                webView = webView,
                url = url,
                newUrlCallback = setUrl,
            )
        },
    )
}

@Composable
@SuppressLint("SetJavaScriptEnabled", "RequiresFeature", "JavascriptInterface")
private fun createWebViewFactory(
    activity: Activity,
    webViewClient: WebViewClient,
    webChromeClient: WebChromeClient,
    javascriptInterfaceCreator: (WebView) -> Any,
    javascriptInterfaceName: String,
) = { context: Context ->
    val webView =
        WebView(activity).apply {
            setNetworkAvailable(true)
        }

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        textZoom = 100
        cacheMode = LOAD_NO_CACHE
        useWideViewPort = true
        loadWithOverviewMode = true
    }

    // This is needed in order to make WebView support navigator.credentials.get/create
    // on its own. This way, we only need to intercept the calls with the `security-key` hint, not
    // any others.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
        WebSettingsCompat.setWebAuthenticationSupport(
            webView.settings,
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP)

        YOLOLogger.i(webView.tagForLog,
            "Web authentication support enabled: ${WebSettingsCompat.getWebAuthenticationSupport(webView.settings)}")
    }
    else {
        YOLOLogger.e(webView.tagForLog, "WebView does not support passkeys.")
    }

    webView.webViewClient = webViewClient

    webView.webChromeClient = webChromeClient

    ServiceWorkerController
        .getInstance()
        .apply {
            serviceWorkerWebSettings.allowContentAccess = true
            setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? = super.shouldInterceptRequest(request)
                },
            )
        }

    webView.addJavascriptInterface(
        javascriptInterfaceCreator(webView),
        javascriptInterfaceName,
    )

    webView
}

private fun updateWebView(
    webView: WebView,
    url: String?,
    newUrlCallback: (String) -> Unit,
) {
    if (url?.isNotBlank() == true) {
        if (url == "webview://back") {
            webView.evaluateJavascript(
                """
                window.history.back()
                document.location.href
                """.trimIndent(),
            ) {
                val newUrl =
                    if (it.contains("\"")) {
                        it.split("\"")[1]
                    } else {
                        it
                    }

                YOLOLogger.i(webView.tagForLog, "Reached $newUrl after back.")
                newUrlCallback("")
            }
        } else {
            webView.loadUrl(url)
        }
        webView.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
    }
}
