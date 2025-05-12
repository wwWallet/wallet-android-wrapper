package io.yubicolabs.wwwwallet.webkit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import io.yubicolabs.wwwwallet.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.wwwwallet.tagForLog
import java.io.ByteArrayInputStream

class WalletWebViewClient (
    private val activity: Activity
): WebViewClientCompat() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val response = super.shouldInterceptRequest(view, request)
        val requestHeader = request.requestHeaders.map { e ->
            "> ${e.key}: ${e.value}"
        }.joinToString(separator = "\n")

        val debugRequest = "${request.method.uppercase()} ${request.url}\n$requestHeader"
        Log.i(tagForLog, "intercepting http request: $debugRequest")

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
