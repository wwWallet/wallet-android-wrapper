package io.yubicolabs.wwwwallet.credentials.provider

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.credentials.provider.RemoteEntry
import com.yubico.webauthn.data.ByteArray
import io.yubicolabs.wwwwallet.R
import io.yubicolabs.wwwwallet.credentials.SoftwareContainer
import io.yubicolabs.wwwwallet.json.getNested
import io.yubicolabs.wwwwallet.json.toList
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog
import org.json.JSONArray
import org.json.JSONObject
import androidx.credentials.provider.CredentialProviderService as AndroidCredentialProviderService

private const val BASE_ID: Int = 12341

private const val CREATE_BASE_REQUEST_ID: Int = BASE_ID + 0
const val CREATE_SECURITY_KEY_REQUEST_ID: Int = CREATE_BASE_REQUEST_ID + 0
const val CREATE_CLIENT_DEVICE_REQUEST_ID: Int = CREATE_BASE_REQUEST_ID + 1

const val GET_CREDENTIAL_REQUEST_ID: Int = BASE_ID + 10

const val BUNDLE_KEY_REQUEST: String = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
const val HINT_SECURITY_KEY: String = "security-key"
const val HINT_CLIENT_DEVICE: String = "client-device"

const val EXTRA_KEY_USER_ID = "KEY_ACCOUNT_ID"
const val EXTRA_KEY_REQUEST_ID = "KEY_REQUEST_ID"
const val EXTRA_KEY_CREDENTIALS_JSON = "KEY_CREDENTIAL_JSON"
const val EXTRA_KEY_CREDENTIAL_ID = "KEY_CREDENTIAL_ID"
const val EXTRA_KEY_REQUEST_OPTIONS = "KEY_REQUEST_OPTIONS"

const val BUNDLE_KEY_REQUEST_JSON = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
const val BUNDLE_KEY_CLIENT_DATA_HASH = "androidx.credentials.BUNDLE_KEY_CLIENT_DATA_HASH"

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class CredentialProviderService() : AndroidCredentialProviderService() {
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        val queryJson =
            request.candidateQueryData.getString(BUNDLE_KEY_REQUEST)
                ?: ""
        val query = JSONObject(queryJson)
        val userId = query.getNested("user.id") as? String ?: ""

        callback.onResult(
            BeginCreateCredentialResponse(
                createEntries =
                    listOf(
                        createCreateEntry(
                            accountName = baseContext.getString(R.string.credential_provider_create_android_description),
                            userId = userId,
                            requestId = CREATE_CLIENT_DEVICE_REQUEST_ID,
                        ),
                    ),
                remoteEntry =
                    RemoteEntry(
                        pendingIntent =
                            createSecurityKeyCreationIntent(
                                queryJson,
                            ),
                    ),
            ),
        )
    }

    private fun createCreateEntry(
        accountName: String,
        requestId: Int,
        userId: String,
    ): CreateEntry =
        CreateEntry(
            accountName = accountName,
            pendingIntent =
                PendingIntent.getActivity(
                    baseContext,
                    requestId,
                    Intent(baseContext, PasskeyProviderActivity::class.java).also {
                        it.setPackage(PACKAGE_NAME)
                        it.putExtra(EXTRA_KEY_USER_ID, userId)
                        it.putExtra(EXTRA_KEY_REQUEST_ID, requestId)
                    },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
        )

    private fun createSecurityKeyCreationIntent(optionsJson: String): PendingIntent =
        PendingIntent.getActivity(
            baseContext,
            CREATE_SECURITY_KEY_REQUEST_ID,
            Intent(baseContext, PasskeyProviderActivity::class.java).also {
                it.setPackage(PACKAGE_NAME)
                it.putExtra(EXTRA_KEY_REQUEST_OPTIONS, optionsJson)
            },
            PendingIntent.FLAG_MUTABLE,
        )

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        try {
            // TODO: Use all options, not just the first.
            val optionsJson =
                request.beginGetCredentialOptions.first().candidateQueryData.getString(
                    BUNDLE_KEY_REQUEST,
                ) ?: "null"

            val options = JSONObject(optionsJson)
            if (options != JSONObject.NULL) {
                SoftwareContainer(applicationContext).get(
                    JSONObject(mapOf("publicKey" to options)),
                    successCallback = { buildInResponse ->
                        val response =
                            if (buildInResponse.has("credentials")) {
                                buildInResponse.getJSONArray("credentials")
                            } else {
                                JSONArray(listOf(buildInResponse))
                            }

                        answerRequest(
                            callback,
                            request,
                            response,
                        )
                    },
                    failureCallback = { th ->
                        YOLOLogger.e(tagForLog, "Failure in requesting credentials.", th)

                        callback.onError(
                            GetCredentialProviderConfigurationException("No credentials found for that configuration."),
                        )
                    },
                )
            }
        } catch (th: Throwable) {
            YOLOLogger.e(tagForLog, "Couldn't get credentials.", th)

            callback.onError(
                GetCredentialUnknownException("Couldn't retrieve credentials."),
            )
        }
    }

    private fun answerRequest(
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
        request: BeginGetCredentialRequest,
        credentials: JSONArray,
    ) {
        val requestJson =
            request
                .beginGetCredentialOptions
                .first()
                .candidateQueryData
                .getString(BUNDLE_KEY_REQUEST)

        val requestId = request.beginGetCredentialOptions.first().id
        val credentialEntries =
            credentials.toList().mapNotNull {
                (it as? Map<*, *>)?.toCredentialEntry(requestJson ?: "", requestId)
            }

        YOLOLogger.i(
            tagForLog,
            "Available user named credentials:\n  ${
                credentialEntries.joinToString("\n  ") { cred ->
                    (cred as? PublicKeyCredentialEntry)?.username ?: "<no public key credential: ${cred.javaClass.simpleName}."
                }
            }",
        )

        callback.onResult(
            // result =
            BeginGetCredentialResponse(
                credentialEntries = credentialEntries,
                authenticationActions =
                    listOf(
                        AuthenticationAction(
                            title = "Get From Security Key",
                            pendingIntent = createPendingIntentForSecurityCreation(credentials.toString()),
                        ),
                    ),
            ),
        )
    }

    private fun createPendingIntentForSecurityCreation(credentialsJson: String): PendingIntent =
        PendingIntent.getActivity(
            baseContext,
            0x411,
            Intent(baseContext, PasskeyProviderActivity::class.java).also {
                it.setPackage(PACKAGE_NAME)
                it.putExtra(EXTRA_KEY_REQUEST_ID, 0x411)
                it.putExtra(EXTRA_KEY_CREDENTIALS_JSON, credentialsJson)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createGetCredentialsPendingIntent(
        id: String,
        credentialJson: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            baseContext,
            GET_CREDENTIAL_REQUEST_ID,
            Intent(baseContext, PasskeyProviderActivity::class.java).also {
                it.setPackage(PACKAGE_NAME)
                it.putExtra(EXTRA_KEY_REQUEST_ID, GET_CREDENTIAL_REQUEST_ID)
                it.putExtra(EXTRA_KEY_CREDENTIALS_JSON, credentialJson)
                it.putExtra(EXTRA_KEY_CREDENTIAL_ID, id)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        YOLOLogger.i("TODO", "Not yet implemented")
    }

    private fun Map<*, *>.toCredentialEntry(
        requestJson: String,
        requestId: String,
    ): CredentialEntry {
        val id = get("id") as String
        val username = get("userName") as String
        val displayname = get("userDisplayName") as String

        val clientDataHash =
            ByteArray.fromBase64Url(getNested("response.clientDataJSON") as String)
                .bytes

        val bundle = Bundle()
        bundle.putString(BUNDLE_KEY_REQUEST_JSON, requestJson)
        bundle.putByteArray(BUNDLE_KEY_CLIENT_DATA_HASH, clientDataHash)

        return PublicKeyCredentialEntry(
            username = username,
            context = applicationContext,
            pendingIntent = createGetCredentialsPendingIntent(id, JSONObject(this).toString()),
            beginGetPublicKeyCredentialOption =
                BeginGetPublicKeyCredentialOption(
                    candidateQueryData = bundle,
                    id = requestId,
                    requestJson = requestJson,
                    clientDataHash = clientDataHash,
                ),
            displayName = displayname,
            isAutoSelectAllowed = false,
            icon =
                Icon.createWithResource(
                    applicationContext,
                    R.mipmap.ic_launcher,
                ),
        )
    }
}
