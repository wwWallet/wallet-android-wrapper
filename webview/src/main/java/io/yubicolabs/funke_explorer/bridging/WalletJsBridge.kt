package io.yubicolabs.funke_explorer.bridging

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.yubicolabs.funke_explorer.BuildConfig
import io.yubicolabs.funke_explorer.bluetooth.BleClientHandler
import io.yubicolabs.funke_explorer.bluetooth.BleServerHandler
import io.yubicolabs.funke_explorer.bluetooth.ServiceCharacteristic
import io.yubicolabs.funke_explorer.credentials.NavigatorCredentialsContainer
import io.yubicolabs.funke_explorer.json.setNested
import io.yubicolabs.funke_explorer.json.toList
import io.yubicolabs.funke_explorer.tagForLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext

class WalletJsBridge(
    private val webView: WebView,
    private val dispatcher: CoroutineDispatcher,
    private val securityKeyCredentialsContainer: NavigatorCredentialsContainer,
    private val clientDeviceCredentialsContainer: NavigatorCredentialsContainer,
    private val emulatedCredentialsContainer: NavigatorCredentialsContainer,
    private val bleClientHandler: BleClientHandler,
    private val bleServerHandler: BleServerHandler,
    private val debugMenuHandler: DebugMenuHandler
) {
    companion object {
        const val JAVASCRIPT_BRIDGE_NAME = "nativeWrapper"
    }

    private fun credentialsContainerByOption(mappedOptions: JSONObject): NavigatorCredentialsContainer =
        try {
            val publicKey = mappedOptions.getJSONObject("publicKey")
            // throws JSONException if not present
            val jsonHints = publicKey.getJSONArray("hints")
            val hints = jsonHints.toList().mapNotNull { it as? String }

            var selectedContainer: NavigatorCredentialsContainer? = null
            for (hint in hints) {
                selectedContainer = when (hint) {
                    "security-key" -> securityKeyCredentialsContainer
                    "client-device" -> clientDeviceCredentialsContainer
                    "hybrid" -> null // explicitly not supported
                    // not in spec: added for testing
                    "emulator" -> emulatedCredentialsContainer
                    else -> {
                        // error case: unknown hint.
                        Log.e(tagForLog, "Hint '$hint' not supported. Ignoring.")
                        null
                    }
                }

                if (selectedContainer != null) {
                    break
                }
            }

            selectedContainer ?: securityKeyCredentialsContainer
        } catch (jsonException: JSONException) {
            Log.i(
                tagForLog,
                "'hints' field in credential options not found, defaulting back to 'security-key'.",
                jsonException
            )
            securityKeyCredentialsContainer
        }


    /**
     * Call this to overwrite the `navigator.credentials.[get|create]` methods.
     */
    @JavascriptInterface
    @SuppressLint("unused")
    fun inject() {
        Log.i(
            tagForLog,
            "Adding `${javaClass.simpleName}` as `$JAVASCRIPT_BRIDGE_NAME` to JS."
        )

        dispatcher.dispatch(EmptyCoroutineContext) {
            val injectionSnippet = JSCodeSnippet.fromRawResource(
                context = webView.context, resource = "injectjs.js", replacements = listOf(
                    "JAVASCRIPT_BRIDGE" to JAVASCRIPT_BRIDGE_NAME,
                    "JAVASCRIPT_VISUALIZE_INJECTION" to "${BuildConfig.VISUALIZE_INJECTION}"
                )
            )

            webView.evaluateJavascript(injectionSnippet.code) {
                Log.i(it.tagForLog, it)
            }
        }
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun openDebugMenu() {
        Dispatchers.Main.dispatch(EmptyCoroutineContext) {
            debugMenuHandler.onMenuOpened { code, callback ->
                dispatcher.dispatch(EmptyCoroutineContext) {
                    webView.evaluateJavascript(
                        code,
                        callback,
                    )
                }
            }
        }
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun create(
        promiseUuid: String,
        options: String
    ) {
        val mappedOptions = JSONObject(options)
        mappedOptions.setNested("publicKey.attestation", "none")
        Log.i(tagForLog, "$JAVASCRIPT_BRIDGE_NAME.create($promiseUuid, ${mappedOptions.toString(2)}) called.")

        credentialsContainerByOption(mappedOptions)
            .create(
                options = mappedOptions,
                failureCallback = { th ->
                    Log.e(tagForLog, "Creation failed.", th)

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            console.log('credential creation failed', JSON.stringify("$th"))
                            alert('Credential creation failed: ' + JSON.stringify("${th.localizedMessage}"))
                            $JAVASCRIPT_BRIDGE_NAME.__reject__("$promiseUuid", JSON.stringify("$th"));
                        """.trimIndent()
                        ) {}
                    }
                },
                successCallback = { response ->
                    Log.i(tagForLog, "Creation succeeded with $response.")

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            var response = JSON.parse('$response')
                            console.log('credential created', response)
                            $JAVASCRIPT_BRIDGE_NAME.__resolve__("$promiseUuid", response);
                        """.trimIndent()
                        ) {}
                    }
                }
            )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun get(
        promiseUuid: String,
        options: String
    ) {
        Log.i(tagForLog, "$JAVASCRIPT_BRIDGE_NAME.get($promiseUuid, $options) called.")

        val mappedOptions = JSONObject(options)
        credentialsContainerByOption(mappedOptions)
            .get(
                options = mappedOptions,
                failureCallback = { th ->
                    Log.e(tagForLog, "Get failed.", th)

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            console.log('credential getting failed', JSON.stringify("$th"))
                            alert('Credential getting failed: ' + JSON.stringify("${th.localizedMessage}"))
                            $JAVASCRIPT_BRIDGE_NAME.__reject__("$promiseUuid", JSON.stringify("$th"));
                        """.trimIndent()
                        ) {}
                    }
                },
                successCallback = { response ->
                    Log.i(tagForLog, "Get succeeded with $response.")

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            var response = JSON.parse('$response')
                            console.log('credential getted', response)
                            $JAVASCRIPT_BRIDGE_NAME.__resolve__("$promiseUuid", response);
                        """.trimIndent()
                        ) {}
                    }
                }
            )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothStatusWrapped(
        promiseUuid: String,
        unusedParameter: String
    ) {
        resolvePromise(
            promiseUuid,
            // @formatter:off
            "Mode:   ${ServiceCharacteristic.mode.name}\\n\\n" +
            "Server: ${bleServerHandler.status()}\\n\\n" +
            "Client: ${bleClientHandler.status()}"
            // @formatter:on
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothTerminateWrapped(
        promiseUuid: String,
        unusedParameter: String
    ) {
        bleServerHandler.disconnect()
        bleClientHandler.disconnect()

        resolvePromise(promiseUuid, "true")
    }


    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothCreateServerWrapped(
        promiseUuid: String,
        serviceUuid: String
    ) {
        bleServerHandler.createServer(
            serviceUuid = serviceUuid,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") }
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothCreateClientWrapped(
        promiseUuid: String,
        serviceUuid: String,
    ) {
        bleClientHandler.createClient(
            serviceUuid = serviceUuid,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") }
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothSendToServerWrapped(
        promiseUuid: String,
        rawParameter: String
    ) {
        val parameter = JSONArray(rawParameter).toByteArray()

        bleClientHandler.sendToServer(
            parameter,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") }
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothSendToClientWrapped(
        promiseUuid: String,
        rawParameter: String
    ) {
        val parameter = JSONArray(rawParameter).toByteArray()

        bleServerHandler.sendToClient(
            parameter,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") }
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothReceiveFromClientWrapped(
        promiseUuid: String,
        unusedParameter: String
    ) {
        bleServerHandler.receiveFromClient(
            success = { resolvePromise(promiseUuid, JSONArray(it).toString()) },
            failure = { rejectPromise(promiseUuid, "null") }
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothReceiveFromServerWrapped(
        promiseUuid: String,
        unusedParameter: String
    ) {
        bleClientHandler.receiveFromServer(
            success = { resolvePromise(promiseUuid, JSONArray(it).toString()) },
            failure = { rejectPromise(promiseUuid, "false") }
        )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothSetMode(mode: String) {
        if (mode in ServiceCharacteristic.Mode.entries.map { it.name }) {
            ServiceCharacteristic.mode = ServiceCharacteristic.Mode.valueOf(mode)
        }
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun bluetoothGetMode(): String = ServiceCharacteristic.mode.name


    private fun resolvePromise(
        promiseUuid: String,
        result: String
    ) {
        dispatcher.dispatch(EmptyCoroutineContext) {
            val wrapped = JSONObject.wrap(result)
            webView.evaluateJavascript(
                "${JAVASCRIPT_BRIDGE_NAME}.__resolve__('$promiseUuid', '$wrapped')"
            ) {}
        }
    }

    private fun rejectPromise(
        promiseUuid: String,
        result: String
    ) {
        dispatcher.dispatch(EmptyCoroutineContext) {
            val wrapped = JSONObject.wrap(result)
            webView.evaluateJavascript(
                "${JAVASCRIPT_BRIDGE_NAME}.__reject__('$promiseUuid', '$wrapped')"
            ) {}
        }
    }
}

private fun JSONArray.toByteArray(): ByteArray = (0 until length()).mapNotNull { index ->
    val value = get(index)
    if (value is Int) {
        value.toByte()
    } else {
        null
    }
}.toByteArray()
