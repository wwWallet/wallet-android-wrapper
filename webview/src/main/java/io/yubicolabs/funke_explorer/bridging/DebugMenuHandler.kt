package io.yubicolabs.funke_explorer.bridging

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.webkit.ValueCallback
import io.yubicolabs.funke_explorer.BuildConfig
import io.yubicolabs.funke_explorer.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.funke_explorer.json.toList
import org.json.JSONArray
import kotlin.math.log10
import kotlin.math.nextUp

private const val SHOW_URL_ROW = "Show URL Row"
private const val HIDE_URL_ROW = "Hide URL Row"
private const val SEND_FEEDBACK = "Give Feedback (Version ${BuildConfig.VERSION_NAME})"

private const val OVERRIDE_HINT_WITH_SECURITY_KEY = "Set hints to ['security-key']"
private const val OVERRIDE_HINT_WITH_CLIENT_DEVICE = "Set hints to ['client-device']"
private const val OVERRIDE_HINT_WITH_EMULATOR = "Set hints to ['emulator']"
private const val DO_NOT_OVERRIDE_HINT = "Reset hints"

private const val LIST_SEPARATOR = "────"

typealias JSExecutor = (code: String, callback: ValueCallback<String>) -> Unit

class DebugMenuHandler(
    val context: Context,
    val showUrlRow: (Boolean) -> Unit,
) {
    private var maxSeparatorsCount = 1;
    private val actions: Map<String, (JSExecutor) -> Unit> = mapOf(
        SHOW_URL_ROW to { js -> showUrlRow(true) },
        HIDE_URL_ROW to { js -> showUrlRow(false) },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        OVERRIDE_HINT_WITH_SECURITY_KEY to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints(['security-key'])") {} },
        OVERRIDE_HINT_WITH_CLIENT_DEVICE to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints(['client-device'])") {} },
        DO_NOT_OVERRIDE_HINT to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints([])") {} },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        SEND_FEEDBACK to { js ->
            js("$JAVASCRIPT_BRIDGE_NAME.__captured_logs__") { logsJson ->
                val jsonArray = JSONArray(logsJson)
                val logs = jsonArray.toList().map { "$it" }
                val body = createIssueBody(logs, Int.MAX_VALUE)
                val title = "Wwwwallet wrapper issue"

//              TODO: Once Github is public, move over from email to github issue creation.
//              val uri =
//                  "https://github.com/wwWallet/wwwallet-android-wrapper/issues/new?title=${title}&body=${body.urlSafe()}".toUri()
//              context.startActivity(Intent(Intent.ACTION_VIEW, uri))

                val intent = Intent(Intent.ACTION_SEND).apply {
                    setType("text/html")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("mario.bodemann@yubico.com"))
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_HTML_TEXT, body)
                    putExtra(Intent.EXTRA_TEXT, body) // fallback
                }

                context.startActivity(intent)            }
        },
    )

    fun onMenuOpened(jsExecutor: JSExecutor) {
        jsExecutor("console.log('Developer encountered.')") {}
        val items = actions.keys.toTypedArray()
        val theme = io.yubicolabs.funke_explorer.R.style.Theme_Funkeexplorer_Dialog

        AlertDialog.Builder(context, theme)
            .setTitle("Debug Menu")
            .setItems(
                items
            ) { dialog, which ->
                val key = items[which]
                if (key in actions) {
                    jsExecutor("console.log(`Debug Menu $key pressed`)") {}
                    actions[key]!!(jsExecutor)
                } else {
                    jsExecutor("window.alert('Option $which (${items[which]}) is not implemented.')") {}
                }
            }
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                jsExecutor("console.log('OK')") {}
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, which ->
                jsExecutor("console.log('Not OK')") {}
                dialog.dismiss()
            }
            .show()
    }
}

private operator fun String.times(times: Int): String =
    (0 until times).joinToString(separator = "") { this }

private fun createIssueBody(logs: List<String>, maxLogLineCount: Int = 50): String {
    val digits = log10(logs.size.toFloat()).nextUp().toInt() + 1

    // truncate log to max lines (otherwise request to github becomes to big)
    val truncatedLogs = if (logs.size > maxLogLineCount) {
        logs.takeLast(maxLogLineCount)
    } else {
        logs
    }

    val truncated = truncatedLogs.size < logs.size
    val truncatedOffset = if (truncated) {
        logs.size - truncatedLogs.size
    } else {
        0
    }


    return """Hey wwwallet team,
                           
    I found the following issue on version ${BuildConfig.VERSION_NAME}:
    
    Description
    1. I opened the app
    2. ....
    
    Expectation
    1. ...
    
    Greetings,

    ----------------------

    PS: The following is the log of errors:
    <details><summary>Wwallet Log</summary>

    ```
    ${if (truncated) "… truncated …\n" else ""} ${
        truncatedLogs.mapIndexed { index, line ->
            "${"%0${digits}d".format(index + truncatedOffset + 1)}: $line"
        }.joinToString("\n")
    }
    ```
    </table>
    </details> 
""".lines().joinToString("\n") { it.trim() }
}

private fun String.urlSafe() = java.net.URLEncoder.encode(this, "utf-8")
