package org.siros.wwwallet.bridging

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.text.parseAsHtml
import androidx.credentials.CredentialManager
import org.json.JSONArray
import org.siros.wwwallet.BuildConfig
import org.siros.wwwallet.R
import org.siros.wwwallet.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import org.siros.wwwallet.json.toList
import org.siros.wwwallet.logging.YOLOLogger
import java.net.URLEncoder

private const val USE_DEMO_BASE_URL = "Use Demo Base URL (default)"
private const val USE_FUNKE_BASE_URL = "Use Funke Base URL"
private const val USE_QA_BASE_URL = "Use QA Base URL"
private const val CUSTOM_BASE_URL = "Custom Base URL"

private const val OPEN_CONFIG = "Open Passkey Preferences"

private const val SHOW_LOGS = "Show Application Logs"
private const val SEND_FEEDBACK_EMAIL = "Give Feedback via email"
private const val SEND_FEEDBACK_GITHUB = "Give Feedback via GitHub issues"

private const val LIST_SEPARATOR = "────"

typealias JSExecutor = (code: String, callback: ValueCallback<String>) -> Unit

class DebugMenuHandler(
    val context: Context,
    val browseTo: (String) -> Unit,
    val updateBaseUrl: () -> Unit,
    val copyToClipboard: (String) -> Unit,
) {
    private var maxSeparatorsCount = 1
    private val actions: Map<String, (JSExecutor) -> Unit> =
        mapOf(
            USE_DEMO_BASE_URL to { _ -> browseTo("https://demo.wwwallet.org/") },
            USE_FUNKE_BASE_URL to { _ -> browseTo("https://funke.wwwallet.org/") },
            USE_QA_BASE_URL to { _ -> browseTo("https://qa.wwwallet.org/") },
            CUSTOM_BASE_URL to { _ -> updateBaseUrl() },
            LIST_SEPARATOR * maxSeparatorsCount++ to {},
            OPEN_CONFIG to { _ -> openPasskeyProviderSettings() },
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
        val theme = R.style.Theme_Wwwallet_Dialog

        AlertDialog
            .Builder(context, theme)
            .setTitle("Debug Menu (v${BuildConfig.VERSION_NAME})")
            .setItems(
                items,
            ) { _, which ->
                val key = items[which]
                if (key in actions) {
                    jsExecutor("console.log(`Debug Menu $key pressed`)") {}
                    actions[key]!!(jsExecutor)
                } else {
                    jsExecutor("window.alert('Option $which (${items[which]}) is not implemented.')") {}
                }
            }.setPositiveButton(android.R.string.ok) { dialog, _ ->
                jsExecutor("console.log('OK')") {}
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                jsExecutor("console.log('Not OK')") {}
                dialog.dismiss()
            }.show()
    }

    fun showLogs(
        logs: List<String>,
        copyToClipboard: (String) -> Unit,
    ) {
        val theme = R.style.Theme_Wwwallet_Dialog

        AlertDialog
            .Builder(context, theme)
            .setTitle("Log")
            .setItems(
                logs
                    .map { log ->
                        log
                            .replace(
                                Regex("[0-9]+: (.*)"),
                                "<tt>$1</tt>",
                            ).parseAsHtml()
                    }.toTypedArray(),
            ) { dialog, which ->
                copyToClipboard(logs[which])
                dialog.dismiss()
            }.setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }.setNeutralButton("📋") { _, _ ->
                copyToClipboard(logs.joinToString("\n"))
            }.show()
    }

    private fun openPasskeyProviderSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val man = CredentialManager.create(context)
            val int = man.createSettingsPendingIntent()
            int.send()
        } else {
            Toast
                .makeText(
                    context,
                    "Not available on your OS version. You have ${Build.VERSION.SDK_INT} but it needs to be ${Build.VERSION_CODES.VANILLA_ICE_CREAM}.",
                    Toast.LENGTH_LONG,
                ).show()
        }
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
                type = "text/html"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("googledeveloper@siros.org"))
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
                .map { "$it" } + YOLOLogger.messages()

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

private fun String.urlSafe() = URLEncoder.encode(this, "utf-8")
