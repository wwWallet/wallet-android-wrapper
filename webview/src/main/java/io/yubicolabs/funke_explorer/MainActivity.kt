package io.yubicolabs.funke_explorer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings.LOAD_NO_CACHE
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewClientCompat
import ch.qos.logback.classic.android.BasicLogcatConfigurator
import io.yubicolabs.funke_explorer.bluetooth.BleClientHandler
import io.yubicolabs.funke_explorer.bluetooth.BleServerHandler
import io.yubicolabs.funke_explorer.bridging.DebugMenuHandler
import io.yubicolabs.funke_explorer.bridging.WalletJsBridge
import io.yubicolabs.funke_explorer.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.funke_explorer.credentials.NavigatorCredentialsContainerAndroid
import io.yubicolabs.funke_explorer.credentials.NavigatorCredentialsContainerYubico
import io.yubicolabs.funke_explorer.ui.theme.FunkeExplorerTheme
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream


class MainActivity : ComponentActivity() {
    init {
        BasicLogcatConfigurator.configureDefaultContext()
    }

    val vm: MainViewModel by viewModels<MainViewModel>()

    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionGranted?.invoke()
            } else {
                permissionDenied?.invoke()
            }

            permissionGranted = null
            permissionDenied = null
        }

    var permissionGranted: (() -> Unit)? = null
    var permissionDenied: (() -> Unit)? = null

    fun requestPermission(input: String, granted: () -> Unit, denied: () -> Unit) {
        permissionGranted = granted
        permissionDenied = denied

        permissionLauncher.launch(input)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        vm.activity = this // 👀 (NFC)

        onBackPressedDispatcher.addCallback {
            vm.onBackPressed()
        }

        super.onCreate(savedInstanceState)

        setContent {
            FunkeExplorerTheme {
                val url by vm.url.collectAsState()
                val navigation by vm.navigation.collectAsState()
                val urlRow by vm.showUrlRow.collectAsState()

                Scaffold(
                    topBar = {
                        if (urlRow) {
                            TopAppBar(
                                title = {
                                    Text(text = stringResource(id = R.string.app_name))
                                },
                                actions = {
                                    IconButton(onClick = { vm.reinjectAndroidBridge() }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.baseline_refresh_24),
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    },
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .padding(paddingValues)
                            .fillMaxHeight()
                    ) {
                        if (BuildConfig.SHOW_URL_ROW || urlRow) {
                            UrlRow(vm)
                        }

                        WebView(
                            activity = this@MainActivity,
                            url = url,
                            navigation = navigation,
                            showUrlRow = {
                                vm.showUrlRow(it)
                            },
                            requestPermission = {
                                val (input, granted, denied) = it
                                this@MainActivity.requestPermission(input, granted, denied)
                            }
                        )
                    }
                }
            }
        }

        if (intent.scheme == "https") {
            vm.parseIntent(intent)
        }
    }
}

@Composable
fun ColumnScope.UrlRow(vm: MainViewModel) {
    Row {
        var tempUrl by remember { mutableStateOf("https://funke.wwwallet.org") }
        val keyboardController = LocalSoftwareKeyboardController.current

        TextField(
            modifier = Modifier.weight(1.0f),
            singleLine = true,
            label = { Text(text = "Enter URL") },
            value = tempUrl,
            onValueChange = {
                tempUrl = it
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions {
                vm.setUrl(tempUrl)
                keyboardController?.hide()
            },
        )

        IconButton(onClick = { vm.setUrl(tempUrl) }) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_upload),
                contentDescription = null
            )
        }
    }
}

@Composable
fun ColumnScope.WebView(
    activity: Activity,
    url: String?,
    navigation: String?,
    showUrlRow: (Boolean) -> Unit,
    requestPermission: (RequestPermissionParameters) -> Unit,
) {
    AndroidView(
        modifier = Modifier.wrapContentHeight(
            align = Alignment.Top,
        ),
        factory = createWebViewFactory(
            activity = activity,
            showUrlRow = showUrlRow,
            requestPermission = requestPermission
        ),
        update = { webView: WebView ->
            updateWebView(
                webView = webView,
                navigation = navigation,
                url = url,
            )
        }
    )
}

private const val WEBKIT_VIDEO_PERMISSION = "android.webkit.resource.VIDEO_CAPTURE"

data class RequestPermissionParameters(
    val resource: String,
    val grant: () -> Unit,
    val deny: () -> Unit
)

@Composable
@SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
private fun createWebViewFactory(
    activity: Activity,
    showUrlRow: (Boolean) -> Unit,
    requestPermission: (RequestPermissionParameters) -> Unit,
) = { context: Context ->
    val webView = WebView(activity).apply {
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

    webView.webViewClient = object : WebViewClientCompat() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val response = super.shouldInterceptRequest(view, request)
            val responseLog = if (response != null) {
                val data = if (response.data != null) {
                    " " + String(response.data.readBytes())
                } else {
                    ""
                }

                "\n${response.statusCode}$data"
            } else {
                ""
            }

            Log.i(
                tagForLog,
                "${request.method.uppercase()}: ${request.url}$responseLog"
            )

            return when (request.url.scheme) {
                "http", "https" -> response

                else -> {
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (e: ActivityNotFoundException) {
                        Log.e(tagForLog, "Could not find activity for ${request.url}.", e)
                    }

                    // assume external handling and immediately return to sender.
                    return WebResourceResponse(
                        "text/html",
                        "utf-8",
                        ByteArrayInputStream(
                            """
                            <script language="JavaScript" type="text/javascript">
                                setTimeout("window.history.go(-1)", 1000);
                            </script>
                            """.trim().toByteArray()
                        )
                    )
                }

            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            view.evaluateJavascript("$JAVASCRIPT_BRIDGE_NAME.inject()") {
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError
        ) {
            view.evaluateJavascript("console.log('SSL Error: \"$error\"');") {}
            handler.proceed()
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            Log.e("WEBVIEW", message ?: "<>")

            return super.onJsAlert(view, url, message, result)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            for (resource in request.resources) {
                if (resource == WEBKIT_VIDEO_PERMISSION) {
                    grantPermission(context, request, resource)
                } else {
                    webView.evaluateJavascript("console.log('Permission request denied: $resource')") {}
                }
            }
        }

        private fun grantPermission(
            context: Context,
            request: PermissionRequest,
            resource: String,
        ) {
            when (ContextCompat.checkSelfPermission(context, "android.permission.CAMERA")) {
                PackageManager.PERMISSION_GRANTED -> request.grant(arrayOf(resource))
                PackageManager.PERMISSION_DENIED -> requestPermission(
                    RequestPermissionParameters(
                        "android.permission.CAMERA",
                        { request.grant(arrayOf(resource)) },
                        { request.deny() })
                )
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            super.onPermissionRequestCanceled(request)
        }
    }

    ServiceWorkerController
        .getInstance().apply {
            serviceWorkerWebSettings.allowContentAccess = true
            setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? {
                        return super.shouldInterceptRequest(request)
                    }
                }
            )
        }

    webView.addJavascriptInterface(
        WalletJsBridge(
            webView,
            Dispatchers.Main,
            if (BuildConfig.USE_YUBIKIT == true) {
                NavigatorCredentialsContainerYubico(
                    activity = activity
                )
            } else {
                NavigatorCredentialsContainerAndroid(
                    activity = activity
                )
            },
            BleClientHandler(
                activity = activity,
            ),
            BleServerHandler(
                activity = activity,
            ),
            DebugMenuHandler(
                context = activity,
                showUrlRow = showUrlRow
            )
        ),
        JAVASCRIPT_BRIDGE_NAME
    )

    webView
}

fun updateWebView(
    webView: WebView,
    navigation: String?,
    url: String?,
) {
    if (url?.isNotBlank() == true) {
        webView.loadUrl(url)
    }

    if (navigation == "back") {
        webView.evaluateJavascript("history.back()") {}
    }

    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
}
