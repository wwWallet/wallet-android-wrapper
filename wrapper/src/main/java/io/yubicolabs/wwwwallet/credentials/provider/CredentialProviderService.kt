package io.yubicolabs.wwwwallet.credentials.provider

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.Action
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
import io.yubicolabs.wwwwallet.R
import io.yubicolabs.wwwwallet.credentials.SoftwareContainer
import io.yubicolabs.wwwwallet.json.getNested
import io.yubicolabs.wwwwallet.json.toList
import io.yubicolabs.wwwwallet.tagForLog
import org.json.JSONArray
import org.json.JSONObject
import androidx.credentials.provider.CredentialProviderService as AndroidCredentialProviderService

private const val BASE_ID: Int = 12341

private const val CREATE_BASE_REQUEST_ID: Int = BASE_ID + 0
const val CREATE_SECURITY_KEY_REQUEST_ID: Int = CREATE_BASE_REQUEST_ID + 0
const val CREATE_CLIENT_DEVICE_REQUEST_ID: Int = CREATE_BASE_REQUEST_ID + 1

const val BUNDLE_KEY_REQUEST: String = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
const val HINT_SECURITY_KEY: String = "security-key"
const val HINT_CLIENT_DEVICE: String = "client-device"

const val EXTRA_KEY_ACCOUNT_ID = "KEY_ACCOUNT_ID"
const val EXTRA_KEY_REQUEST_ID = "KEY_REQUEST_ID"

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class CredentialProviderService() : AndroidCredentialProviderService() {
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val queryJson =
            request.candidateQueryData.getString(BUNDLE_KEY_REQUEST)
                ?: ""
        val query = JSONObject(queryJson)
        val accountName = query.getNested("user.name") as? CharSequence ?: ""
        val accountId = query.getNested("user.id") as? String ?: ""

        callback.onResult(
            BeginCreateCredentialResponse(
                listOf(
                    createCreateEntry(
                        accountName = baseContext.getString(R.string.credential_provider_create_external_device_description),
                        accountId = accountId,
                        requestId = CREATE_SECURITY_KEY_REQUEST_ID,
                    ),
                    createCreateEntry(
                        accountName = baseContext.getString(R.string.credential_provider_create_android_description),
                        accountId = accountId,
                        requestId = CREATE_CLIENT_DEVICE_REQUEST_ID,
                    )
                ),
            )
        )
    }

    private fun createCreateEntry(
        accountName: String,
        requestId: Int,
        accountId: String,
    ): CreateEntry = CreateEntry(
        accountName = accountName,
        pendingIntent = PendingIntent.getActivity(
            baseContext,
            requestId,
            Intent(baseContext, PasskeyProviderActivity::class.java).also {
                it.setPackage(PACKAGE_NAME)
                it.putExtra(EXTRA_KEY_ACCOUNT_ID, accountId)
                it.putExtra(EXTRA_KEY_REQUEST_ID, requestId)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, androidx.credentials.exceptions.GetCredentialException>
    ) {
        try {
            // TODO: Use all options, not just the first.
            val optionsJson =
                request.beginGetCredentialOptions.first().candidateQueryData.getString(
                    BUNDLE_KEY_REQUEST
                ) ?: "null"

            val options = JSONObject(optionsJson)
            if (options != JSONObject.NULL)
                BuildInContainer().get(
                    options,
                    successCallback = { buildInResponses ->
//                        ContainerYubico(activity).get(
//                            options,
//                            successCallback = { yubicoResponses ->
                        answerRequest(
                            callback,
                            request,
                            JSONArray(
                                // todo convert into credentials here???
                                listOf(
                                    buildInResponses,
//                                            yubicoResponses
                                )
                            )
                        )
//                            },
//                            failureCallback = {
//                                Log.e(tagForLog, "Failure in requesting credentials.")
//
//                                callback.onError(
//                                    GetCredentialProviderConfigurationException("No credentials found for that configuration.")
//                                )
//                            }
//                        )
                    },
                    failureCallback = { th ->
                        Log.e(tagForLog, "Failure in requesting credentials.")

                        callback.onError(
                            GetCredentialProviderConfigurationException("No credentials found for that configuration.")
                        )
                    },
                )
        } catch (th: Throwable) {
            Log.e(tagForLog, "Couldn't get credentials.", th)

            callback.onError(
                GetCredentialUnknownException("Couldn't retrieve credentials.")
            )
        }
    }

    private fun answerRequest(
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
        request: BeginGetCredentialRequest, // TODO: USE THIS IN THE ACTIONS FOR NAMES AND THINGSSS
        credentials: JSONArray
    ) {
        callback.onResult(
            /*result = */ BeginGetCredentialResponse(
                credentialEntries = credentials.toList().mapNotNull {
                    /// TODO: MAP TO ACTUAL CREDENTIALS!!
                    (it as? Map<*, *>)?.toCredentialEntries()
                }.flatten(),
                actions = listOf(
                    Action(
                        title = "TITILE 1",
                        subtitle = "subtitle",
                        pendingIntent = createActionIntent()
                    )
                ),
                authenticationActions = listOf(
                    AuthenticationAction(
                        title = "AUTHACT!",
                        pendingIntent = createAuthActionIntent(),
                    )
                )
            )
        )
    }

    private fun createActionIntent(): PendingIntent = PendingIntent.getActivity(
        baseContext,
        0xC0FFE, // TODO MAKE SENSE OF THAT
        Intent(baseContext, PasskeyProviderActivity::class.java).also {
            it.setPackage(PACKAGE_NAME)
            it.putExtra("YOLO", "SOMETHING< FIX ME< DO STUFF")
            it.putExtra(EXTRA_KEY_REQUEST_ID, 0xC0FFE)
        },
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createAuthActionIntent(): PendingIntent = PendingIntent.getActivity(
        baseContext,
        0xBEEF, // TODO MAKE SENSE OF THAT
        Intent(baseContext, PasskeyProviderActivity::class.java).also {
            it.setPackage(PACKAGE_NAME)
            it.putExtra("YOLO-BUT_AUTH", "SOMETHING< FIX ME< DO STUFF")
            it.putExtra(EXTRA_KEY_REQUEST_ID, 0xBEEF)
        },
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createCredentialPendingIntent(): PendingIntent = PendingIntent.getActivity(
        baseContext,
        0xC0DE, // TODO MAKE SENSE OF THAT
        Intent(baseContext, PasskeyProviderActivity::class.java).also {
            it.setPackage(PACKAGE_NAME)
            it.putExtra("YOLO-BUT_CREDENTIAL", "SOMETHING< FIX ME< DO STUFF")
            it.putExtra(EXTRA_KEY_REQUEST_ID, 0xC0DE)
        },
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        Log.i("TODO", "Not yet implemented")
    }

    // TODO THINK ABOUT MUTIPLE SELECTION RETURNS
    private fun Map<*, *>.toCredentialEntries(): List<CredentialEntry> {
        // TODO convert all responses to entries
        val bundle = Bundle()
        val username = "username"
        val displayname = "displayname"
        val id = "1234"
        val clientDataHash = byteArrayOf()

        return listOf(
            PublicKeyCredentialEntry(
                username = username,
                context = applicationContext,
                pendingIntent = createCredentialPendingIntent(),
                beginGetPublicKeyCredentialOption = BeginGetPublicKeyCredentialOption(
                    candidateQueryData = bundle,
                    id = id,
                    requestJson = "",
                    clientDataHash = clientDataHash,
                ),
                displayName = displayname,
                isAutoSelectAllowed = false,
                icon = Icon.createWithResource(
                    applicationContext,
                    R.mipmap.ic_launcher
                ) // TODO: ADD FANCY ICON!!?
            )
        )
    }
}
