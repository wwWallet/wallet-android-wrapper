package org.siros.wwwallet.credentials

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.security.keystore.UserNotAuthenticatedException
import android.text.InputType
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import android.widget.EditText
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.core.util.Callback
import com.yubico.yubikit.fido.client.MultipleAssertionsAvailable
import com.yubico.yubikit.fido.client.WebAuthnClient
import com.yubico.yubikit.fido.client.clientdata.ClientDataProvider
import com.yubico.yubikit.fido.client.extensions.CredBlobExtension
import com.yubico.yubikit.fido.client.extensions.CredPropsExtension
import com.yubico.yubikit.fido.client.extensions.CredProtectExtension
import com.yubico.yubikit.fido.client.extensions.HmacSecretExtension
import com.yubico.yubikit.fido.client.extensions.LargeBlobExtension
import com.yubico.yubikit.fido.client.extensions.MinPinLengthExtension
import com.yubico.yubikit.fido.webauthn.PublicKeyCredential
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialCreationOptions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialDescriptor
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialRequestOptions
import com.yubico.yubikit.fido.webauthn.SerializationType
import org.siros.wwwallet.json.getOrNull
import org.siros.wwwallet.json.toMap
import org.siros.wwwallet.logging.YOLOLogger
import org.siros.wwwallet.tagForLog
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext

sealed class Operation(
    open val options: JSONObject,
    open val success: (JSONObject) -> Unit,
    open val failure: (Throwable) -> Unit,
) {
    data class CreateOperation(
        override val options: JSONObject,
        override val success: (JSONObject) -> Unit,
        override val failure: (Throwable) -> Unit,
    ) : Operation(options, success, failure)

    data class GetOperation(
        override val options: JSONObject,
        override val success: (JSONObject) -> Unit,
        override val failure: (Throwable) -> Unit,
    ) : Operation(options, success, failure)
}

class YubicoContainer(
    val activity: Activity,
) : Container {
    private val manager: YubiKitManager = YubiKitManager(activity)

    private val usbListener: Callback<UsbYubiKeyDevice> =
        Callback { device ->
            deviceConnected(device)
        }

    private val nfcListener: Callback<NfcYubiKeyDevice> =
        Callback { device ->
            deviceConnected(device)
        }

    private fun startDiscoveries() {
        manager.startUsbDiscovery(
            UsbConfiguration().handlePermissions(true),
            usbListener,
        )

        try {
            manager.startNfcDiscovery(
                NfcConfiguration().timeout(10_000),
                activity,
                nfcListener,
            )
        } catch (e: NfcNotAvailable) {
            YOLOLogger.i(tagForLog, "No NFC, ignoring.", e)
        }
    }

    private var lastOperation: Operation? = null

    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        YOLOLogger.i(tagForLog, "yubico create implementation called.")

        startDiscoveries()

        lastOperation =
            Operation.CreateOperation(
                options = options,
                success = successCallback,
                failure = failureCallback,
            )
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        YOLOLogger.i(tagForLog, "yubico get implementation called.")

        startDiscoveries()

        lastOperation =
            Operation.GetOperation(
                options = options,
                success = successCallback,
                failure = failureCallback,
            )
    }

    private fun askForPin(
        operation: Operation,
        device: YubiKeyDevice,
    ) {
        // ask user for pin
        requestPin { providedPin ->
            if (providedPin != null) {
                routeToCorrectMethodWithPin(operation, device, providedPin)
            } else {
                operation.failure(
                    UserNotAuthenticatedException(
                        "User did enter empty pin.",
                    ),
                )
            }
        }
    }

    private fun routeToCorrectMethodWithPin(
        operation: Operation,
        device: YubiKeyDevice,
        pin: String?,
    ) {
        try {
            when (operation) {
                is Operation.CreateOperation ->
                    createWithDevice(
                        device,
                        operation,
                        pin,
                    )

                is Operation.GetOperation ->
                    getWithDevice(
                        device,
                        operation,
                        pin,
                    )
            }
        } catch (e: Throwable) {
            YOLOLogger.e(tagForLog, "An unknown error appeared. Hide now.", e)
        }
    }

    private fun deviceConnected(device: YubiKeyDevice) {
        lastOperation?.let { operation ->
            askForPin(operation, device)
        }
    }

    private fun createWithDevice(
        device: YubiKeyDevice,
        operation: Operation.CreateOperation,
        pin: String?,
    ) {
        val client = createClient(device)

        val createOptions = operation.options
        val publicKey = createOptions.publicKey!!
        val kitOptions = PublicKeyCredentialCreationOptions.fromMap(publicKey.toMap())
        val domain = publicKey.rp?.id ?: ""
        val provider = ClientDataProvider.fromClientDataJson(
            getClientOptions(
                type = "webauthn.create",
                origin = domain,
                challenge =
                    encodeToString(
                        kitOptions.challenge,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
            )
        )
        val enterprise = null
        val state = null

        try {
            val result: PublicKeyCredential =
                client.makeCredential(
                    provider,
                    kitOptions,
                    domain,
                    pin?.toCharArray(),
                    enterprise,
                    state,
                )

            YOLOLogger.i(tagForLog, "Done, created $result.")
            operation.success(JSONObject(result.toMap()))
        } catch (ctap: CtapException) {
            YOLOLogger.e(tagForLog, "Protocol exception: '${ctap.ctapError.toHumanReadable()}'.", ctap)
            operation.failure(ctap)
        } catch (th: Throwable) {
            YOLOLogger.e(tagForLog, "Unexpected error: '${th.message}'.", th)
            operation.failure(th)
        } finally {
            lastOperation = null
            client.close()
        }
    }

    private fun createClient(device: YubiKeyDevice) =
        WebAuthnClient.create(
            device,
            listOf(
                CredPropsExtension(),
                CredBlobExtension(),
                CredProtectExtension(),
                HmacSecretExtension(),
                MinPinLengthExtension(),
                LargeBlobExtension(),
            )
        )

    private fun getWithDevice(
        device: YubiKeyDevice,
        operation: Operation.GetOperation,
        pin: String?,
    ) {
        val client = createClient(device)

        val getOptions = operation.options
        val publicKey = getOptions.publicKey!!
        val kitOptions = PublicKeyCredentialRequestOptions.fromMap(publicKey.toMap())
        val domain = publicKey.rpId ?: ""
        val provider = ClientDataProvider.fromClientDataJson(
            getClientOptions(
                type = "webauthn.get",
                origin = domain,
                challenge =
                    encodeToString(
                        kitOptions.challenge,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
            )
        )
        val enterprise = null
        try {
            val result =
                client.getAssertion(
                    provider,
                    kitOptions,
                    domain,
                    pin?.toCharArray(),
                    enterprise,
                )

            YOLOLogger.i(tagForLog, "Done, got $result.")
            operation.success(JSONObject(result.toMap()))
        } catch (ctap: CtapException) {
            YOLOLogger.e(tagForLog, "Protocol exception: '${ctap.ctapError.toHumanReadable()}'.", ctap)
            operation.failure(ctap)
        } catch (multiple: MultipleAssertionsAvailable) {
            YOLOLogger.i(tagForLog, "Found several assertions. User selection needed.")
            requestSelection(multiple, kitOptions, operation)
        } catch (th: Throwable) {
            YOLOLogger.e(tagForLog, "Unexpected error: '${th.message}'.", th)
            operation.failure(th)
        } finally {
            lastOperation = null
            client.close()
        }
    }

    private fun requestSelection(
        multiple: MultipleAssertionsAvailable,
        kitOptions: PublicKeyCredentialRequestOptions,
        operation: Operation.GetOperation,
    ) {
        askForCredentialSelection(
            multiple,
            success = { credential ->
                kitOptions.allowCredentials.clear()
                kitOptions.allowCredentials.add(
                    PublicKeyCredentialDescriptor(
                        "public-key",
                        credential.rawId,
                        null,
                    ),
                )

                get(
                    JSONObject(mapOf("publicKey" to kitOptions.toMap(SerializationType.JSON))),
                    operation.success,
                    operation.failure,
                )
            },
            failure = {
                operation.failure(Throwable())
            },
        )
    }

    private fun askForCredentialSelection(
        available: MultipleAssertionsAvailable,
        success: (PublicKeyCredential) -> Unit,
        failure: () -> Unit,
    ) {
        Dispatchers.Main.dispatch(EmptyCoroutineContext) {
            val items = available.users.map { it.displayName }.toTypedArray()
            val listener =
                DialogInterface.OnClickListener { dialog, which ->
                    val credential = available.select(which)
                    YOLOLogger.i(tagForLog, "credential selected: $credential")
                    dialog?.dismiss()

                    success(credential)
                }

            AlertDialog
                .Builder(activity)
                .setTitle("Select one")
                .setItems(items, listener)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    YOLOLogger.i(tagForLog, "No user selected.")
                    dialog.dismiss()

                    failure()
                }.show()
        }
    }

    private fun requestPin(callback: (String?) -> Unit) {
        Dispatchers.Main.dispatch(EmptyCoroutineContext) {
            val pinEdit =
                EditText(activity).apply {
                    hint = "PIN"
                    maxLines = 1
                    minLines = 1
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }

            val dialog =
                AlertDialog
                    .Builder(activity)
                    .setTitle("Pin Required")
                    .setView(pinEdit)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        YOLOLogger.i(tagForLog, "PIN entered.")
                        dialog.dismiss()
                        callback(pinEdit.text.toString())
                    }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        YOLOLogger.i(tagForLog, "PIN entry cancelled.")
                        dialog.dismiss()
                        callback(null)
                    }.show()

            pinEdit.setOnEditorActionListener { v, _, _ ->
                dialog.dismiss()
                callback(v.text.toString())
                true
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Byte.toHumanReadable(): String =
    when (this) {
        CtapException.ERR_SUCCESS -> "ERR_SUCCESS"
        CtapException.ERR_INVALID_COMMAND -> "ERR_INVALID_COMMAND"
        CtapException.ERR_INVALID_PARAMETER -> "ERR_INVALID_PARAMETER"
        CtapException.ERR_INVALID_LENGTH -> "ERR_INVALID_LENGTH"
        CtapException.ERR_INVALID_SEQ -> "ERR_INVALID_SEQ"
        CtapException.ERR_TIMEOUT -> "ERR_TIMEOUT"
        CtapException.ERR_CHANNEL_BUSY -> "ERR_CHANNEL_BUSY"
        CtapException.ERR_LOCK_REQUIRED -> "ERR_LOCK_REQUIRED"
        CtapException.ERR_INVALID_CHANNEL -> "ERR_INVALID_CHANNEL"
        CtapException.ERR_CBOR_UNEXPECTED_TYPE -> "ERR_CBOR_UNEXPECTED_TYPE"
        CtapException.ERR_INVALID_CBOR -> "ERR_INVALID_CBOR"
        CtapException.ERR_MISSING_PARAMETER -> "ERR_MISSING_PARAMETER"
        CtapException.ERR_LIMIT_EXCEEDED -> "ERR_LIMIT_EXCEEDED"
        CtapException.ERR_UNSUPPORTED_EXTENSION -> "ERR_UNSUPPORTED_EXTENSION"
        CtapException.ERR_FP_DATABASE_FULL -> "ERR_FP_DATABASE_FULL"
        CtapException.ERR_CREDENTIAL_EXCLUDED -> "ERR_CREDENTIAL_EXCLUDED"
        CtapException.ERR_PROCESSING -> "ERR_PROCESSING"
        CtapException.ERR_INVALID_CREDENTIAL -> "ERR_INVALID_CREDENTIAL"
        CtapException.ERR_USER_ACTION_PENDING -> "ERR_USER_ACTION_PENDING"
        CtapException.ERR_OPERATION_PENDING -> "ERR_OPERATION_PENDING"
        CtapException.ERR_NO_OPERATIONS -> "ERR_NO_OPERATIONS"
        CtapException.ERR_UNSUPPORTED_ALGORITHM -> "ERR_UNSUPPORTED_ALGORITHM"
        CtapException.ERR_OPERATION_DENIED -> "ERR_OPERATION_DENIED"
        CtapException.ERR_KEY_STORE_FULL -> "ERR_KEY_STORE_FULL"
        CtapException.ERR_NOT_BUSY -> "ERR_NOT_BUSY"
        CtapException.ERR_NO_OPERATION_PENDING -> "ERR_NO_OPERATION_PENDING"
        CtapException.ERR_UNSUPPORTED_OPTION -> "ERR_UNSUPPORTED_OPTION"
        CtapException.ERR_INVALID_OPTION -> "ERR_INVALID_OPTION"
        CtapException.ERR_KEEPALIVE_CANCEL -> "ERR_KEEPALIVE_CANCEL"
        CtapException.ERR_NO_CREDENTIALS -> "ERR_NO_CREDENTIALS"
        CtapException.ERR_USER_ACTION_TIMEOUT -> "ERR_USER_ACTION_TIMEOUT"
        CtapException.ERR_NOT_ALLOWED -> "ERR_NOT_ALLOWED"
        CtapException.ERR_PIN_INVALID -> "ERR_PIN_INVALID"
        CtapException.ERR_PIN_BLOCKED -> "ERR_PIN_BLOCKED"
        CtapException.ERR_PIN_AUTH_INVALID -> "ERR_PIN_AUTH_INVALID"
        CtapException.ERR_PIN_AUTH_BLOCKED -> "ERR_PIN_AUTH_BLOCKED"
        CtapException.ERR_PIN_NOT_SET -> "ERR_PIN_NOT_SET"
        CtapException.ERR_PIN_POLICY_VIOLATION -> "ERR_PIN_POLICY_VIOLATION"
        CtapException.ERR_PIN_TOKEN_EXPIRED -> "ERR_PIN_TOKEN_EXPIRED"
        CtapException.ERR_REQUEST_TOO_LARGE -> "ERR_REQUEST_TOO_LARGE"
        CtapException.ERR_ACTION_TIMEOUT -> "ERR_ACTION_TIMEOUT"
        CtapException.ERR_UP_REQUIRED -> "ERR_UP_REQUIRED"
        CtapException.ERR_UV_BLOCKED -> "ERR_UV_BLOCKED"
        CtapException.ERR_INTEGRITY_FAILURE -> "ERR_INTEGRITY_FAILURE"
        CtapException.ERR_INVALID_SUBCOMMAND -> "ERR_INVALID_SUBCOMMAND"
        CtapException.ERR_UV_INVALID -> "ERR_UV_INVALID"
        CtapException.ERR_UNAUTHORIZED_PERMISSION -> "ERR_UNAUTHORIZED_PERMISSION"
        CtapException.ERR_OTHER -> "ERR_OTHER"
        CtapException.ERR_SPEC_LAST -> "ERR_SPEC_LAST"
        CtapException.ERR_EXTENSION_FIRST -> "ERR_EXTENSION_FIRST"
        CtapException.ERR_EXTENSION_LAST -> "ERR_EXTENSION_LAST"
        CtapException.ERR_VENDOR_FIRST -> "ERR_VENDOR_FIRST"
        CtapException.ERR_VENDOR_LAST -> "ERR_VENDOR_LAST"
        else -> "Unknown CTAP ERROR"
    }

private val JSONObject.publicKey: JSONObject?
    get() = getOrNull("publicKey")

private val JSONObject.rpId: String?
    get() = getOrNull("rpId")

private val JSONObject.rp: JSONObject?
    get() = getOrNull("rp")

private val JSONObject.id: String?
    get() = getOrNull("id")
