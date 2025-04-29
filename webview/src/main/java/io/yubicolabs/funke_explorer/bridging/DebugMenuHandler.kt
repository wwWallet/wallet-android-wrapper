package io.yubicolabs.funke_explorer.bridging

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.ValueCallback
import io.yubicolabs.funke_explorer.BuildConfig
import io.yubicolabs.funke_explorer.bridging.WalletJsBridge.Companion.JAVASCRIPT_BRIDGE_NAME
import io.yubicolabs.funke_explorer.tagForLog

private const val SHOW_URL_ROW = "Show URL Row"
private const val HIDE_URL_ROW = "Hide URL Row"
private const val SEND_FEEDBACK = "Send Feedback"
private const val SHOW_VERSION = "Version ${BuildConfig.VERSION_NAME} @ ${BuildConfig.VERSION_CODE}"

private const val OVERRIDE_HINT_WITH_SECURITY_KEY = "Override credential option hint with 'security-key'"
private const val OVERRIDE_HINT_WITH_CLIENT_DEVICE = "Override credential option hint with 'client-device'"
private const val OVERRIDE_HINT_WITH_EMULATOR = "Override credential option hint with 'emulator'"
private const val DO_NOT_OVERRIDE_HINT = "Don't override credential option hints."

private const val BLE_SET_MODE_MDOC = "mDoc Mode"
private const val BLE_SET_MODE_READER = "mDoc Reader Mode (DEFAULT)"

private const val BLE_CREATE_SERVER = "SERVER: Create"
private const val BLE_SEND_TO_CLIENT = "SERVER: Send"
private const val BLE_RECEIVE_FROM_CLIENT = "SERVER: Receive"

private const val BLE_CREATE_CLIENT = "CLIENT: Create"
private const val BLE_SEND_TO_SERVER = "CLIENT: Send"
private const val BLE_RECEIVE_FROM_SERVER = "CLIENT: Receive"

private const val BLE_STATUS = "Show status"
private const val BLE_TERMINATE = "Terminate All"

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

        OVERRIDE_HINT_WITH_SECURITY_KEY to { it("$JAVASCRIPT_BRIDGE_NAME.__override_hints = ['security-key']"){} },
        OVERRIDE_HINT_WITH_CLIENT_DEVICE to { it("$JAVASCRIPT_BRIDGE_NAME.__override_hints = ['client-device']"){} },
        OVERRIDE_HINT_WITH_EMULATOR to { it("$JAVASCRIPT_BRIDGE_NAME.__override_hints = ['emulator']"){} },
        DO_NOT_OVERRIDE_HINT to { it("$JAVASCRIPT_BRIDGE_NAME.__override_hints = []"){} },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        BLE_SET_MODE_MDOC to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothSetMode(\"MDoc\")") {} },
        BLE_SET_MODE_READER to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothSetMode(\"MDocReader\")") {} },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        BLE_CREATE_SERVER to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothCreateServer(\"00179c7a-eec6-4f88-8646-045fda9ac4d8\").then(r=>console.log(r))") {} },
        BLE_SEND_TO_CLIENT to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothSendToClient([1,2,3,4,5,6]).then(r=>console.log(r))") {} },
        BLE_RECEIVE_FROM_CLIENT to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothReceiveFromClient().then(r=>alert(r))") {} },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        BLE_CREATE_CLIENT to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothCreateClient(\"00179c7a-eec6-4f88-8646-045fda9ac4d8\").then(r=>alert(r))") {} },
        BLE_SEND_TO_SERVER to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothSendToServer([6,5,4,3,2,1]).then(r=>alert(r))") {} },
        BLE_RECEIVE_FROM_SERVER to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothReceiveFromServer().then(r=>alert(r))") {} },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        BLE_STATUS to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothStatus().then(r=>alert(r))") {} },
        BLE_TERMINATE to { it("$JAVASCRIPT_BRIDGE_NAME.bluetoothTerminate().then(r=>alert(r))") {} },

        LIST_SEPARATOR * maxSeparatorsCount++ to {},

        SEND_FEEDBACK to { js ->
            js("$JAVASCRIPT_BRIDGE_NAME.__captured_logs__.map( (x,i)=> i + \": \" + x).join('\\n')") { it ->
                Log.d(tagForLog, it)

                val body =
                    "Hey funke wallet explorer team,\n\nI found the following issue on version ${BuildConfig.VERSION_NAME}:\n<DETAILED DESCRIPTION>\nGreetings,\n<NAME>\n\n=======\nLOGS\n${
                        it.replace(
                            "\\n",
                            "\n"
                        )
                    }"

                val intent = Intent(Intent.ACTION_SEND).apply {
                    setType("text/html")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("mario.bodemann@yubico.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback to funkeexplorer android wrapper")
                    putExtra(Intent.EXTRA_HTML_TEXT, body)
                    putExtra(Intent.EXTRA_TEXT, body) // fallback
                }

                context.startActivity(intent)
            }
        },

        SHOW_VERSION to { js ->
            // nothing
        }
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
