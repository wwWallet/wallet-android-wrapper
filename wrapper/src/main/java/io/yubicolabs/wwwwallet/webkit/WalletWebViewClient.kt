package io.yubicolabs.wwwwallet.webkit

import android.app.Activity
import android.content.Intent
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebViewClientCompat
import io.yubicolabs.wwwwallet.BuildConfig
import io.yubicolabs.wwwwallet.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME

private val URL_IGNORE_LIST: Array<String> = arrayOf(
    "github",
    "demo-issuer",
    "demo-verifier",
    "qa-issuer",
    "qa-verifier",
)

class WalletWebViewClient(
    private val activity: Activity,
) : WebViewClientCompat() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        if (
            URL_IGNORE_LIST.any {
                request.url.host?.lowercase()?.startsWith(it) == true
            }
        ) {
            if (BuildConfig.DEBUG) {
                Toast.makeText(view.context, "Opening '${request.url}' app chooser.", Toast.LENGTH_SHORT).show()
            }

            activity.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            return true
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        super.onPageFinished(view, url)

        view.evaluateJavascript("$JAVASCRIPT_BRIDGE_NAME.inject()") {}
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        view.evaluateJavascript("console.log('SSL Error: \"$error\"');") {}
        handler.proceed()
    }
}
