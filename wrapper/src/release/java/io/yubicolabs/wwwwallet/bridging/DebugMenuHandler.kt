package io.yubicolabs.wwwwallet.bridging

import android.content.Context
import android.webkit.ValueCallback

typealias JSExecutor = (code: String, callback: ValueCallback<String>) -> Unit

class DebugMenuHandler(
    val context: Context,
    val showUrlRow: (Boolean) -> Unit,
    val browseTo: (Boolean) -> Unit,
) {
    fun onMenuOpened(jsExecutor: JSExecutor) {
        jsExecutor(
            """
            console.log("DEBUG MENU IGNORED IN RELEASE BUILD.");
            """.trimIndent(),
        ) {}
    }
}
