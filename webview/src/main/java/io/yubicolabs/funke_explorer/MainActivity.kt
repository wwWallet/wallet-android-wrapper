package io.yubicolabs.funke_explorer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import io.yubicolabs.funke_explorer.credentials.SoftwareCredentialsContainer
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

        onBackPressedDispatcher.addCallback(
            owner = this
        ) { vm.onBackPressed() }

        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                val systemUiController = rememberSystemUiController()
                systemUiController.setSystemBarsColor(MaterialTheme.colorScheme.onPrimary)

                val urlRow by vm.showUrlRow.collectAsState()

                Scaffold(
                    topBar = {
                        if (urlRow) {
                            TopAppBar(
                                title = {
                                    Text(text = stringResource(id = R.string.app_name))
                                },
                                actions = {
                                    IconButton(onClick = { vm.setUrl(BuildConfig.BASE_URL) }) {
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
                        if (urlRow) {
                            UrlRow(vm)
                        }

                        WebView(
                            activity = this@MainActivity,
                            vm = vm,
                            requestPermission = {
                                val (input, granted, denied) = it
                                this@MainActivity.requestPermission(input, granted, denied)
                            }
                        )
                    }
                }
            }
        }

        when (intent.scheme) {
            "https", "openid4vp" -> vm.parseIntent(intent)
            else -> Log.e(tagForLog, "Cannot handle ${intent.scheme}.")
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
    vm: MainViewModel,
    requestPermission: (RequestPermissionParameters) -> Unit,
) {
    val url by vm.url.collectAsState()

    AndroidView(
        modifier = Modifier.wrapContentHeight(
            align = Alignment.Top,
        ),
        factory = createWebViewFactory(
            activity = activity,
            showUrlRow = { vm.showUrlRow(it) },
            requestPermission = requestPermission
        ),
        update = { webView: WebView ->
            updateWebView(
                webView = webView,
                url = url,
                newUrlCallback = { vm.setUrl(it) },
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
                    val url = if (request.url.toString().startsWith("view://")) {
                        Uri.parse(request.url.toString().replace("view://", "https://"))
                    } else {
                        request.url
                    }

                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (e: ActivityNotFoundException) {
                        Log.e(tagForLog, "Could not find activity for ${url}.", e)
                    }

                    return if (url.scheme == "openid4vp") {
                        response
                    } else {
                        // assume external handling and immediately return to sender.
                        WebResourceResponse(
                            "text/html",
                            "utf-8",
                            ByteArrayInputStream(
                                """
                            <script language="JavaScript" type="text/javascript">
                                setTimeout("window.history.back()", 1000);
                            </script>
                            """.trim().toByteArray()
                            )
                        )
                    }
                }
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            view.evaluateJavascript("$JAVASCRIPT_BRIDGE_NAME.inject()") {
                // remove unwanted elements
                for (unwanted in listOf(
                    "menu-area", // pid issuer useless menu in wrapped mode
                    "ReactModalPortal"
                )) {
                    view.evaluateJavascript(
                        """
                            while(document.getElementsByClassName('$unwanted').length > 0) {
                                document.getElementsByClassName('$unwanted')[0].remove()
                            }
                        """.trimIndent()
                    ) {
                        Log.i(tagForLog, "Hardening: Deleted $unwanted class from html.")
                    }
                }

                // remove links to foreign pages
                for (unwanted in listOf(
                    "github",
                    "gunet",
                )) {
                    view.evaluateJavascript(
                        """
                        for (let elem of document.getElementsByTagName("a")) {
                            if(elem.href.indexOf("$unwanted") > -1 && elem.href.indexOf("view://") == -1) {
                                let old = elem.href
                                elem.setAttribute('href', old.replace('https://','view://'))
                                console.log("Hardening: Redirected from", old, "to", elem.href)
                            }
                        }
                    """.trimIndent()
                    ) {}
                }
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
            NavigatorCredentialsContainerYubico(
                activity = activity
            ),
            NavigatorCredentialsContainerAndroid(
                activity = activity
            ),
            SoftwareCredentialsContainer(),
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
    url: String?,
    newUrlCallback: (String) -> Unit
) {
    if (url?.isNotBlank() == true) {
        if (url == "webview://back") {
            webView.evaluateJavascript(
                """
                window.history.back()
                document.location.href
            """.trimIndent()
            ) {
                val newUrl = if (it.contains("\"")) {
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
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
