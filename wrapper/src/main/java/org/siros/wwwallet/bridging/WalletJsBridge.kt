package org.siros.wwwallet.bridging

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.siros.wwwallet.bluetooth.BleClientHandler
import org.siros.wwwallet.bluetooth.BleServerHandler
import org.siros.wwwallet.bluetooth.ServiceCharacteristic
import org.siros.wwwallet.credentials.Container
import org.siros.wwwallet.json.toList
import org.siros.wwwallet.logging.YOLOLogger
import org.siros.wwwallet.tagForLog
import org.siros.wwwallet.BuildConfig
import kotlin.coroutines.EmptyCoroutineContext

class WalletJsBridge(
    private val webView: WebView,
    private val dispatcher: CoroutineDispatcher,
    private val securityKeyCredentialsContainer: Container,
    private val clientDeviceCredentialsContainer: Container,
    private val bleClientHandler: BleClientHandler,
    private val bleServerHandler: BleServerHandler,
    private val debugMenuHandler: DebugMenuHandler?,
) {
    companion object {
        const val JAVASCRIPT_BRIDGE_NAME = "nativeWrapper"
    }

    private fun credentialsContainerByOption(mappedOptions: JSONObject): Container =
        try {
            val publicKey = mappedOptions.getJSONObject("publicKey")
            // throws JSONException if not present
            val jsonHints = publicKey.getJSONArray("hints")
            val hints = jsonHints.toList().mapNotNull { it as? String }

            var selectedContainer: Container? = null
            for (hint in hints) {
                selectedContainer =
                    when (hint) {
                        "security-key" -> securityKeyCredentialsContainer
                        "client-device" -> clientDeviceCredentialsContainer
                        "hybrid" -> null // explicitly not supported
                        else -> {
                            // error case: unknown hint.
                            YOLOLogger.e(tagForLog, "Hint '$hint' not supported. Ignoring.")
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
                jsonException,
            )
            securityKeyCredentialsContainer
        }

    /**
     * Call this to overwrite the `navigator.credentials.[get|create]` methods.
     */
    @JavascriptInterface
    @Suppress("unused")
    fun inject() {
        YOLOLogger.i(
            tagForLog,
            "Adding `${javaClass.simpleName}` as `$JAVASCRIPT_BRIDGE_NAME` to JS.",
        )

        dispatcher.dispatch(EmptyCoroutineContext) {
            val injectionSnippet =
                JSCodeSnippet.fromRawResource(
                    context = webView.context,
                    resource = "injectjs.js",
                    replacements =
                        listOf(
                            "JAVASCRIPT_BRIDGE" to JAVASCRIPT_BRIDGE_NAME,
                            "JAVASCRIPT_VISUALIZE_INJECTION" to "${BuildConfig.VISUALIZE_INJECTION}",
                        ),
                )

            webView.evaluateJavascript(injectionSnippet.code) {
                YOLOLogger.i(it.tagForLog, it)
            }
        }
    }

    @JavascriptInterface
    @Suppress("unused")
    fun openDebugMenu() {
        Dispatchers.Main.dispatch(EmptyCoroutineContext) {
            debugMenuHandler?.onMenuOpened { code, callback ->
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
    @Suppress("unused")
    fun updateAllCredentials(list: String) {
        val credentials = JSONArray(list)
        val message = "Received ${credentials.length()} credentials."

        // TODO: Convert into credential information and pass through to digital credentials api.

        YOLOLogger.i(tagForLog, message)
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun create(
        promiseUuid: String,
        options: String,
    ) {
        val mappedOptions = JSONObject(options)
        YOLOLogger.i(tagForLog, "$JAVASCRIPT_BRIDGE_NAME.create($promiseUuid, ${mappedOptions.toString(2)}) called.")

        credentialsContainerByOption(mappedOptions)
            .create(
                options = mappedOptions,
                failureCallback = { th ->
                    YOLOLogger.e(tagForLog, "Creation failed.", th)

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            console.log('credential creation failed', JSON.stringify("$th"))
                            alert('Credential creation failed: ' + JSON.stringify("${th.localizedMessage}"))
                            $JAVASCRIPT_BRIDGE_NAME.__reject__("$promiseUuid", JSON.stringify("$th"));
                            """.trimIndent(),
                        ) {}
                    }
                },
                successCallback = { response ->
                    YOLOLogger.i(tagForLog, "Creation succeeded with $response.")

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            var response = JSON.parse('$response')
                            console.log('credential created', response)
                            $JAVASCRIPT_BRIDGE_NAME.__resolve__("$promiseUuid", response);
                            """.trimIndent(),
                        ) {}
                    }
                },
            )
    }

    @JavascriptInterface
    @SuppressLint("unused")
    fun get(
        promiseUuid: String,
        options: String,
    ) {
        YOLOLogger.i(tagForLog, "$JAVASCRIPT_BRIDGE_NAME.get($promiseUuid, $options) called.")

        val mappedOptions = JSONObject(options)
        val container = credentialsContainerByOption(mappedOptions)
        container
            .get(
                options = mappedOptions,
                failureCallback = { th ->
                    YOLOLogger.e(tagForLog, "Get failed.", th)

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            console.log('credential getting failed', JSON.stringify("$th"))
                            alert('Credential getting failed: ' + JSON.stringify("${th.localizedMessage}"))
                            $JAVASCRIPT_BRIDGE_NAME.__reject__("$promiseUuid", JSON.stringify("$th"));
                            """.trimIndent(),
                        ) {}
                    }
                },
                successCallback = { response ->
                    YOLOLogger.i(tagForLog, "Get succeeded with $response.")

                    dispatcher.dispatch(EmptyCoroutineContext) {
                        webView.evaluateJavascript(
                            """
                            var response = JSON.parse('$response')
                            console.log('credential getted', response)
                            $JAVASCRIPT_BRIDGE_NAME.__resolve__("$promiseUuid", response);
                            """.trimIndent(),
                        ) {}
                    }
                },
            )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothStatusWrapped(
        promiseUuid: String,
        unusedParameter: String,
    ) {
        resolvePromise(
            promiseUuid,
            // @formatter:off
            "Mode:   ${ServiceCharacteristic.mode.name}\\n\\n" +
                "Server: ${bleServerHandler.status()}\\n\\n" +
                "Client: ${bleClientHandler.status()}",
            // @formatter:on
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothTerminateWrapped(
        promiseUuid: String,
        unusedParameter: String,
    ) {
        bleServerHandler.disconnect()
        bleClientHandler.disconnect()

        resolvePromise(promiseUuid, "true")
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothCreateServerWrapped(
        promiseUuid: String,
        serviceUuid: String,
    ) {
        bleServerHandler.createServer(
            serviceUuid = serviceUuid,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") },
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothCreateClientWrapped(
        promiseUuid: String,
        serviceUuid: String,
    ) {
        bleClientHandler.createClient(
            serviceUuid = serviceUuid,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") },
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothSendToServerWrapped(
        promiseUuid: String,
        rawParameter: String,
    ) {
        val parameter = JSONArray(rawParameter).toByteArray()

        bleClientHandler.sendToServer(
            parameter,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") },
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothSendToClientWrapped(
        promiseUuid: String,
        rawParameter: String,
    ) {
        val parameter = JSONArray(rawParameter).toByteArray()

        bleServerHandler.sendToClient(
            parameter,
            success = { resolvePromise(promiseUuid, "true") },
            failure = { rejectPromise(promiseUuid, "false") },
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothReceiveFromClientWrapped(
        promiseUuid: String,
        unusedParameter: String,
    ) {
        bleServerHandler.receiveFromClient(
            success = { resolvePromise(promiseUuid, JSONArray(it).toString()) },
            failure = { rejectPromise(promiseUuid, "null") },
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothReceiveFromServerWrapped(
        promiseUuid: String,
        unusedParameter: String,
    ) {
        bleClientHandler.receiveFromServer(
            success = { resolvePromise(promiseUuid, JSONArray(it).toString()) },
            failure = { rejectPromise(promiseUuid, "false") },
        )
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothSetMode(mode: String) {
        if (mode in ServiceCharacteristic.Mode.entries.map { it.name }) {
            ServiceCharacteristic.mode = ServiceCharacteristic.Mode.valueOf(mode)
        }
    }

    @JavascriptInterface
    @Suppress("unused")
    fun bluetoothGetMode(): String = ServiceCharacteristic.mode.name

    private fun resolvePromise(
        promiseUuid: String,
        result: String,
    ) {
        dispatcher.dispatch(EmptyCoroutineContext) {
            val wrapped = JSONObject.wrap(result)
            webView.evaluateJavascript(
                "${JAVASCRIPT_BRIDGE_NAME}.__resolve__('$promiseUuid', '$wrapped')",
            ) {}
        }
    }

    private fun rejectPromise(
        promiseUuid: String,
        result: String,
    ) {
        dispatcher.dispatch(EmptyCoroutineContext) {
            val wrapped = JSONObject.wrap(result)
            webView.evaluateJavascript(
                "${JAVASCRIPT_BRIDGE_NAME}.__reject__('$promiseUuid', '$wrapped')",
            ) {}
        }
    }
}

private fun JSONArray.toByteArray(): ByteArray =
    (0 until length())
        .mapNotNull { index ->
            val value = get(index)
            if (value is Int) {
                value.toByte()
            } else {
                null
            }
        }.toByteArray()
