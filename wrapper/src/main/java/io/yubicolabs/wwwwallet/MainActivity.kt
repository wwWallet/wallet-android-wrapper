package io.yubicolabs.wwwwallet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import ch.qos.logback.classic.android.BasicLogcatConfigurator
import io.yubicolabs.wwwwallet.bluetooth.BleClientHandler
import io.yubicolabs.wwwwallet.bluetooth.BleServerHandler
import io.yubicolabs.wwwwallet.bridging.DebugMenuHandler
import io.yubicolabs.wwwwallet.bridging.WalletJsBridge
import io.yubicolabs.wwwwallet.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.wwwwallet.credentials.NavigatorCredentialsContainerAndroid
import io.yubicolabs.wwwwallet.credentials.NavigatorCredentialsContainerYubico
import io.yubicolabs.wwwwallet.credentials.SoftwareCredentialsContainer
import io.yubicolabs.wwwwallet.webkit.WalletWebChromeClient
import io.yubicolabs.wwwwallet.webkit.WalletWebViewClient
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    init {
        BasicLogcatConfigurator.configureDefaultContext()
    }

    val vm: MainViewModel by viewModels<MainViewModel>()

    private val webViewClient: WebViewClient = WalletWebViewClient(this)

    private val webChromeClient: WebChromeClient = WalletWebChromeClient(this)

    private val javascriptInterfaceCreator: (WebView) -> WalletJsBridge = { webView ->
        WalletJsBridge(
            webView,
            Dispatchers.Main,
            NavigatorCredentialsContainerYubico(activity = this),
            NavigatorCredentialsContainerAndroid(activity = this),
            SoftwareCredentialsContainer(),
            BleClientHandler(activity = this),
            BleServerHandler(activity = this),
            if (BuildConfig.DEBUG) {
                DebugMenuHandler(
                    context = this,
                    showUrlRow = { vm.showUrlRow(it) },
                    browseTo = { vm.setUrl(it) },
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
            "https", "openid4vp", "haip" -> vm.parseIntent(intent)
            else -> Log.e(tagForLog, "Cannot handle ${intent.scheme}.")
        }

        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                enableEdgeToEdge()

                val urlRow by vm.showUrlRow.collectAsState()

                Scaffold(
                    topBar = {
                        if (urlRow) {
                            TopAppBar(
                                title = {
                                    Text(text = stringResource(id = R.string.app_name))
                                },
                                actions = {
                                    IconButton(onClick = {
                                        // force update
                                        vm.setUrl("")
                                        vm.setUrl(BuildConfig.BASE_URL)
                                    }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.baseline_refresh_24),
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                        }
                    },
                ) { paddingValues ->
                    Column(
                        modifier =
                            Modifier
                                .padding(paddingValues)
                                .fillMaxHeight(),
                    ) {
                        if (urlRow) {
                            UrlRow(vm)
                        }

                        WebView(
                            activity = this@MainActivity,
                            webViewClient = webViewClient,
                            webChromeClient = webChromeClient,
                            javascriptInterfaceCreator = javascriptInterfaceCreator,
                            javascriptInterfaceName = JAVASCRIPT_BRIDGE_NAME,
                            vm.url.collectAsState().value ?: "",
                            vm::setUrl,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.UrlRow(vm: MainViewModel) {
    Row {
        var tempUrl by remember { mutableStateOf(BuildConfig.BASE_URL) }
        val keyboardController = LocalSoftwareKeyboardController.current

        TextField(
            modifier = Modifier.weight(1.0f),
            singleLine = true,
            label = { Text(text = "Enter URL") },
            value = tempUrl,
            onValueChange = {
                tempUrl = it
            },
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Go,
                ),
            keyboardActions =
                KeyboardActions {
                    vm.setUrl(tempUrl)
                    keyboardController?.hide()
                },
        )

        IconButton(onClick = { vm.setUrl(tempUrl) }) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_upload),
                contentDescription = null,
            )
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

    webView.webViewClient = webViewClient

    webView.webChromeClient = webChromeClient

    ServiceWorkerController
        .getInstance().apply {
            serviceWorkerWebSettings.allowContentAccess = true
            setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? {
                        return super.shouldInterceptRequest(request)
                    }
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

                Log.i(webView.tagForLog, "Reached $newUrl after back.")
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
