package org.siros.wwwallet.bridging

import android.content.Context
import android.webkit.ValueCallback

typealias JSExecutor = (code: String, callback: ValueCallback<String>) -> Unit

class DebugMenuHandler(
    val context: Context,
    val browseTo: (String) -> Unit,
    val updateBaseUrl: () -> Unit,
    val copyToClipboard: (String) -> Unit,
) {
    fun onMenuOpened(jsExecutor: JSExecutor) {
        jsExecutor(
            """
            console.log("DEBUG MENU IGNORED IN RELEASE BUILD.");
            """.trimIndent(),
        ) {}
    }
}
