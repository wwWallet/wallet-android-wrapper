package io.yubicolabs.wwwwallet.bridging

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.webkit.ValueCallback
import androidx.core.net.toUri
import androidx.core.text.parseAsHtml
import io.yubicolabs.wwwwallet.BuildConfig
import io.yubicolabs.wwwwallet.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.wwwwallet.json.toList
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import org.json.JSONArray

private const val SHOW_URL_ROW = "Show URL Row"
private const val HIDE_URL_ROW = "Hide URL Row"
private const val USE_DEMO_BASE_URL = "Use Demo Base URL (default)"
private const val USE_FUNKE_BASE_URL = "Use Funke Base URL"
private const val USE_QA_BASE_URL = "Use QA Base URL"

private const val SHOW_LOGS = "Show Application Logs"
private const val SEND_FEEDBACK_EMAIL = "Give Feedback via email"
private const val SEND_FEEDBACK_GITHUB = "Give Feedback via GitHub issues"

private const val OVERRIDE_HINT_WITH_SECURITY_KEY = "Set hints to ['security-key']"
private const val OVERRIDE_HINT_WITH_CLIENT_DEVICE = "Set hints to ['client-device']"
private const val OVERRIDE_HINT_WITH_EMULATOR = "Set hints to ['emulator']"
private const val DO_NOT_OVERRIDE_HINT = "Reset hints"

private const val LIST_SEPARATOR = "────"

typealias JSExecutor = (code: String, callback: ValueCallback<String>) -> Unit

class DebugMenuHandler(
    val context: Context,
    val showUrlRow: (Boolean) -> Unit,
    val browseTo: (String) -> Unit,
    val copyToClipboard: (String) -> Unit,
) {
    private var maxSeparatorsCount = 1
    private val actions: Map<String, (JSExecutor) -> Unit> =
        mapOf(
            SHOW_URL_ROW to { js -> showUrlRow(true) },
            HIDE_URL_ROW to { js -> showUrlRow(false) },
            LIST_SEPARATOR * maxSeparatorsCount++ to {},
            USE_DEMO_BASE_URL to { js -> browseTo("https://demo.wwwallet.org/") },
            USE_FUNKE_BASE_URL to { js -> browseTo("https://funke.wwwallet.org/") },
            USE_QA_BASE_URL to { js -> browseTo("https://qa.wwwallet.org/") },
            LIST_SEPARATOR * maxSeparatorsCount++ to {},
            OVERRIDE_HINT_WITH_SECURITY_KEY to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints(['security-key'])") {} },
            OVERRIDE_HINT_WITH_CLIENT_DEVICE to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints(['client-device'])") {} },
            OVERRIDE_HINT_WITH_EMULATOR to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints(['emulator'])") {} },
            DO_NOT_OVERRIDE_HINT to { it("$JAVASCRIPT_BRIDGE_NAME.overrideHints([])") {} },
            LIST_SEPARATOR * maxSeparatorsCount++ to {},
            SHOW_LOGS to { js ->
                js("$JAVASCRIPT_BRIDGE_NAME.__captured_logs__") { logsJson ->
                    showLogs(
                        collectLogs(logsJson),
                        copyToClipboard,
                    )
                }
            },
            SEND_FEEDBACK_EMAIL to { js ->
                js("$JAVASCRIPT_BRIDGE_NAME.__captured_logs__") { logsJson ->
                    emailFeedback(
                        createIssueBody(
                            collectLogs(logsJson),
                            Int.MAX_VALUE,
                        ),
                    )
                }
            },
            SEND_FEEDBACK_GITHUB to { js ->
                js("$JAVASCRIPT_BRIDGE_NAME.__captured_logs__") { logsJson ->
                    githubFeedback(
                        createIssueBody(
                            collectLogs(logsJson),
                            Int.MAX_VALUE,
                        ),
                    )
                }
            },
        )

    fun onMenuOpened(jsExecutor: JSExecutor) {
        jsExecutor("console.log('Developer encountered.')") {}
        val items = actions.keys.toTypedArray()
        val theme = io.yubicolabs.wwwwallet.R.style.Theme_Wwwallet_Dialog

        AlertDialog.Builder(context, theme)
            .setTitle("Debug Menu (v${BuildConfig.VERSION_NAME})")
            .setItems(
                items,
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

    fun showLogs(
        logs: List<String>,
        copyToClipboard: (String) -> Unit,
    ) {
        val theme = io.yubicolabs.wwwwallet.R.style.Theme_Wwwallet_Dialog

        AlertDialog.Builder(context, theme)
            .setTitle("Log")
            .setItems(
                logs.map { log ->
                    log.replace(
                        Regex("[0-9]+: (.*)"),
                        "<tt>$1</tt>",
                    ).parseAsHtml()
                }
                    .toTypedArray(),
            ) { dialog, which ->
                copyToClipboard(logs[which])
                dialog.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                dialog.dismiss()
            }.setNeutralButton("📋") { dialog, which ->
                copyToClipboard(logs.joinToString("\n"))
            }
            .show()
    }

    private fun githubFeedback(
        body: String,
        title: String = "wwWallet Android Wrapper Issue",
    ) {
        val uri =
            "https://github.com/wwWallet/wallet-android-wrapper/issues/new?title=${
                title
            }&body=${
                body.urlSafe()
            }".toUri()

        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun emailFeedback(
        body: String,
        title: String = "wwWallet Android Wrapper Issue",
    ) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                setType("text/html")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("mario.bodemann@yubico.com"))
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_HTML_TEXT, body)
                putExtra(Intent.EXTRA_TEXT, body) // fallback
            }

        context.startActivity(intent)
    }

    private fun collectLogs(logsJson: String): List<String> {
        val combinedLogs =
            JSONArray(logsJson)
                .toList()
                .map { "$it" } +
                YOLOLogger.messages()

        return combinedLogs.sorted()
    }
}

private operator fun String.times(times: Int): String = (0 until times).joinToString(separator = "") { this }

private fun createIssueBody(
    logs: List<String>,
    maxLogLineCount: Int = 50,
): String {
    // truncate log to max lines (otherwise request to github becomes to big)
    val truncatedLogs =
        if (logs.size > maxLogLineCount) {
            logs.takeLast(maxLogLineCount)
        } else {
            logs
        }

    val truncated = truncatedLogs.size < logs.size

    return """Hey wwWallet Android Wrapper team,
                           
    I found the following issue in version ${BuildConfig.VERSION_NAME} of the Android app:
    
    Description

    1. I opened the app
    2. …
    
    Expectation

    1. …
    
    Thanks for taking a look into it.
    
    Greetings,

    ----------------------

    PS: The following is the log of the app:

    <details><summary>wwWallet Frontend Log</summary>

    ```
    ${if (truncated) "… truncated …\n" else ""} ${
        truncatedLogs.joinToString("\n")
    }
    ```
    </details> 
""".lines().joinToString("\n") { it.trim() }
}

private fun String.urlSafe() = java.net.URLEncoder.encode(this, "utf-8")
