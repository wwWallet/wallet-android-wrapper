package org.siros.wwwallet.webkit

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import org.siros.wwwallet.MainActivity
import org.siros.wwwallet.bridging.WalletJsBridge
import java.net.URI

class WalletWebViewClient(
    private val activity: MainActivity,
    private val onErrorReceived: (description: String) -> Unit,
) : WebViewClientCompat() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val baseUrl = URI(activity.vm.url.value)

        // Open all foreign web pages and app schemes like "eid" for the AusweisApp
        // externally. Only wwWallet code is allowed inside the app.
        if (request.url.scheme != baseUrl.scheme || request.url.host != baseUrl.host) {
            // Restrict the schemes we are willing to hand to startActivity.
            // Without this guard a page rendered inside the wallet WebView can
            // fire arbitrary `intent://`, `content://`, `file://`, or
            // `javascript:` URLs at the host activity, a known Android-WebView
            // intent-smuggling / deep-link attack surface (CWE-939).
            val scheme = request.url.scheme?.lowercase()
            val allowed = scheme == "https" ||
                scheme == "http" ||
                scheme == "eid" ||
                scheme == "tel" ||
                scheme == "mailto"
            if (!allowed) {
                return true
            }
            activity.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            return true
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        super.onPageCommitVisible(view, url)

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
