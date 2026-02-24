package org.siros.wwwallet.webkit

import android.app.Activity
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import org.siros.wwwallet.BuildConfig
import org.siros.wwwallet.bridging.WalletJsBridge

private val URL_IGNORE_LIST: Array<String> =
    arrayOf(
        "github",
        "demo-issuer",
        "demo-verifier",
        "qa-issuer",
        "qa-verifier",
    )

class WalletWebViewClient(
    private val activity: Activity,
    private val onErrorReceived: (description: String) -> Unit,
) : WebViewClientCompat() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (
            URL_IGNORE_LIST.any {
                request.url.host
                    ?.lowercase()
                    ?.startsWith(it) == true
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

        view.evaluateJavascript("${WalletJsBridge.JAVASCRIPT_BRIDGE_NAME}.inject()") {}
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceErrorCompat,
    ) {
        super.onReceivedError(view, request, error)

        val description =
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
                error.description.toString()
            } else {
                "Web Page Error"
            }

        onErrorReceived(description)
    }
}
