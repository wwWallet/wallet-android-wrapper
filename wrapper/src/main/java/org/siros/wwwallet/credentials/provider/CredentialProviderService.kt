@file:OptIn(ExperimentalUuidApi::class)

package org.siros.wwwallet.credentials.provider

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.siros.wwwallet.R
import org.siros.wwwallet.credentials.LocalContainer
import org.siros.wwwallet.json.getNested
import org.siros.wwwallet.json.toList
import org.siros.wwwallet.logging.YOLOLogger
import java.util.concurrent.CountDownLatch
import kotlin.uuid.ExperimentalUuidApi
import androidx.credentials.provider.CredentialProviderService as AndroidCredentialProviderService

const val CREATE_CLIENT_DEVICE_REQUEST_CODE: Int = 12341
const val GET_CLIENT_DEVICE_REQUEST_CODE: Int = 12345

const val BUNDLE_KEY_REQUEST: String = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"

const val EXTRA_KEY_REQUEST_CODE = "KEY_REQUEST_CODE"
const val EXTRA_KEY_REQUEST_ID = "KEY_REQUEST_ID"
const val EXTRA_KEY_CREDENTIAL_ID = "KEY_CREDENTIAL_ID"

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class CredentialProviderService : AndroidCredentialProviderService() {
    companion object {
        private var runningNumberPendingIntentNumber: Int = 1
    }

    private lateinit var localContainer: LocalContainer

    override fun onCreate() {
        super.onCreate()
        localContainer = LocalContainer(applicationContext)
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (!request.isFromWwwallet()) {
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
                                requestCode = CREATE_CLIENT_DEVICE_REQUEST_CODE,
                            ),
                        ),
                ),
            )
        } else {
            // tried to call the wwwallet provider from the wwwallet
            cancellationSignal.cancel()
        }
    }

    private fun createCreateEntry(
        accountName: String,
        requestCode: Int,
    ): CreateEntry =
        CreateEntry(
            accountName = accountName,
            pendingIntent =
                PendingIntent.getActivity(
                    applicationContext,
                    runningNumberPendingIntentNumber++,
                    Intent(applicationContext, PasskeyProviderActivity::class.java).also {
                        it.setPackage(PACKAGE_NAME)
                        it.putExtra(EXTRA_KEY_REQUEST_CODE, requestCode)
                    },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
        )

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        if (!request.isFromWwwallet()) {
            callback.onResult(
                BeginGetCredentialResponse(
                    credentialEntries =
                        createGetEntries(
                            request.beginGetCredentialOptions.mapNotNull {
                                it as? BeginGetPublicKeyCredentialOption
                            },
                        ),
                ),
            )
        } else {
            // tried to call the wwwallet provider from the wwwallet
            cancellationSignal.cancel()
        }
    }

    private fun Any.isFromWwwallet() =
        when (this) {
            is BeginGetCredentialRequest -> (callingAppInfo?.packageName ?: "").contains("wwwallet")
            is BeginCreateCredentialRequest -> (callingAppInfo?.packageName ?: "").contains("wwwallet")
            else -> true
        }

    private fun createGetEntries(requestOptions: List<BeginGetPublicKeyCredentialOption>): List<PublicKeyCredentialEntry> {
        val responsesOrOption = getAllResponsesToOptions(requestOptions)

        val result = mutableListOf<PublicKeyCredentialEntry>()
        var lastOption: BeginGetPublicKeyCredentialOption? = null

        responsesOrOption.forEach { item ->
            when (item) {
                is BeginGetPublicKeyCredentialOption -> lastOption = item

                is Map<*, *> ->
                    lastOption?.let { option ->
                        result += getEntryFromResponses(option, item)
                    }
            }
        }

        return result
    }

    private fun getEntryFromResponses(
        option: BeginGetPublicKeyCredentialOption,
        credential: Map<*, *>,
    ): PublicKeyCredentialEntry {
        val credentialId = credential.getNested("id") as? String ?: ""
        val username = credential.getNested("response.userName") as? String ?: ""
        val displayName = credential.getNested("response.userDisplayName") as? String ?: ""

        val intent = Intent(applicationContext, PasskeyProviderActivity::class.java)
        intent.putExtra(EXTRA_KEY_REQUEST_CODE, GET_CLIENT_DEVICE_REQUEST_CODE)
        intent.putExtra(EXTRA_KEY_CREDENTIAL_ID, credentialId)

        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                (runningNumberPendingIntentNumber++),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val icon = Icon.createWithResource(applicationContext, R.mipmap.ic_launcher)

        return PublicKeyCredentialEntry
            .Builder(
                context = applicationContext,
                username = username,
                pendingIntent = pendingIntent,
                beginGetPublicKeyCredentialOption = option,
            ).setIcon(
                icon,
            ).setDisplayName(
                displayName,
            ).build()
    }

    private fun getAllResponsesToOptions(requestOptions: List<BeginGetPublicKeyCredentialOption>): MutableList<Any?> {
        val responsesOrOption = mutableListOf<Any?>()
        val latch = CountDownLatch(1)

        for (option in requestOptions) {
            var response = JSONArray()

            CoroutineScope(Dispatchers.IO).launch {
                val publicKeyOptions = JSONObject(mapOf("publicKey" to JSONObject(option.requestJson)))
                localContainer.getAll(
                    options = publicKeyOptions,
                    successCallback = {
                        response = it
                        latch.countDown()
                    },
                    failureCallback = {
                        response = JSONArray()
                        latch.countDown()
                    },
                )
            }
            latch.await()

            if (response.length() > 0) {
                responsesOrOption.add(option)
                responsesOrOption.addAll(response.toList())
            }
        }
        return responsesOrOption
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        YOLOLogger.i("TODO", "Not yet implemented")
    }
}
