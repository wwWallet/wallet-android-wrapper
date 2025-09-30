package io.yubicolabs.wwwwallet.credentials.provider

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import io.yubicolabs.wwwwallet.R
import io.yubicolabs.wwwwallet.json.getNested
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import org.json.JSONObject
import androidx.credentials.provider.CredentialProviderService as AndroidCredentialProviderService

const val CREATE_CLIENT_DEVICE_REQUEST_CODE: Int = 12341
const val GET_CLIENT_DEVICE_REQUEST_CODE: Int = 12345

const val BUNDLE_KEY_REQUEST: String = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"

const val EXTRA_KEY_USER_ID = "KEY_ACCOUNT_ID"
const val EXTRA_KEY_REQUEST_CODE = "KEY_REQUEST_CODE"
const val EXTRA_KEY_REQUEST_ID = "KEY_REQUEST_ID"
const val EXTRA_KEY_REQUEST_OPTIONS = "KEY_REQUEST_OPTIONS"

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class CredentialProviderService() : AndroidCredentialProviderService() {
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (isNotFromUs(request.callingAppInfo?.packageName ?: "")) {
            val queryJson =
                request.candidateQueryData.getString(BUNDLE_KEY_REQUEST)
                    ?: ""
            val query = JSONObject(queryJson)
            val userId = query.getNested("user.id") as? String ?: ""
            val requestId = query.getNested("requestId") as? String ?: ""

            callback.onResult(
                BeginCreateCredentialResponse(
                    createEntries =
                        listOf(
                            createCreateEntry(
                                accountName = baseContext.getString(R.string.credential_provider_create_android_description),
                                userId = userId,
                                requestCode = CREATE_CLIENT_DEVICE_REQUEST_CODE,
                                requestId = requestId,
                            ),
                        ),
                ),
            )
        } else {
            // tried to call the wwwallet provider from the wwwwallet
            cancellationSignal.cancel()
        }
    }

    private fun createCreateEntry(
        accountName: String,
        requestCode: Int,
        requestId: String,
        userId: String,
    ): CreateEntry =
        CreateEntry(
            accountName = accountName,
            pendingIntent =
                PendingIntent.getActivity(
                    baseContext,
                    requestCode,
                    Intent(baseContext, PasskeyProviderActivity::class.java).also {
                        it.setPackage(PACKAGE_NAME)
                        it.putExtra(EXTRA_KEY_REQUEST_CODE, requestCode)
                        it.putExtra(EXTRA_KEY_USER_ID, userId)
                        it.putExtra(EXTRA_KEY_REQUEST_ID, requestId)
                    },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
        )

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        if (isNotFromUs(request.callingAppInfo?.packageName ?: "")) {
            callback.onResult(
                BeginGetCredentialResponse(
                    authenticationActions = createGetEntries(request),
                ),
            )
        } else {
            // tried to call the wwwallet provider from the wwwwallet
            cancellationSignal.cancel()
        }
    }

    private fun isNotFromUs(packageName: String) = !packageName.contains("wwwallet")

    private fun createGetEntries(request: BeginGetCredentialRequest): List<AuthenticationAction> {
        val requestJson =
            request
                .beginGetCredentialOptions
                .first()
                .candidateQueryData
                .getString(BUNDLE_KEY_REQUEST)
                ?: ""

        val requestId = request.beginGetCredentialOptions.first().id

        return listOf(
            AuthenticationAction(
                title = applicationContext.getString(R.string.credential_provider_create_android_description),
                pendingIntent =
                    createPendingIntentForGetRequest(
                        GET_CLIENT_DEVICE_REQUEST_CODE,
                        requestId,
                        requestJson,
                    ),
            ),
        )
    }

    private fun createPendingIntentForGetRequest(
        requestCode: Int,
        requestId: String,
        credentialsJson: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            baseContext,
            requestCode,
            Intent(baseContext, PasskeyProviderActivity::class.java).also {
                it.setPackage(PACKAGE_NAME)
                it.putExtra(EXTRA_KEY_REQUEST_CODE, requestCode)
                it.putExtra(EXTRA_KEY_REQUEST_ID, requestId)
                it.putExtra(EXTRA_KEY_REQUEST_OPTIONS, credentialsJson)
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
}
