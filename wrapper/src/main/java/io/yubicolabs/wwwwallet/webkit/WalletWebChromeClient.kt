package io.yubicolabs.wwwwallet.webkit

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.yubicolabs.wwwwallet.tagForLog

private const val WEBKIT_VIDEO_PERMISSION = "android.webkit.resource.VIDEO_CAPTURE"

class WalletWebChromeClient(
    private val activity: ComponentActivity
) : WebChromeClient() {
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
                grantPermission(activity, request, resource)
            } else {
                Log.e(tagForLog, "Permission request denied: $resource")
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
                "android.permission.CAMERA",
                { request.grant(arrayOf(resource)) },
                { request.deny() }
            )
        }
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        super.onPermissionRequestCanceled(request)
    }

    private fun requestPermission(input: String, granted: () -> Unit, denied: () -> Unit) {
        permissionGranted = granted
        permissionDenied = denied

        permissionLauncher.launch(input)
    }

    val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
}
