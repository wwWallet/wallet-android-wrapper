package org.siros.wwwallet.credentials

import android.content.Context
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext

private const val REGISTRATION_RESPONSE_BUNDLE_KEY =
    "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"

class AndroidContainer(
    val context: Context,
) : Container {
    val manager = CredentialManager.create(context)

    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        CoroutineScope(EmptyCoroutineContext).launch {
            try {
                val result =
                    manager.createCredential(
                        context = context,
                        request = options.toCreateRequestOption(),
                    )

                val rawResult = result.data.getString(REGISTRATION_RESPONSE_BUNDLE_KEY) ?: ""

                successCallback(JSONObject(rawResult))
            } catch (th: Throwable) {
                failureCallback(th)
            }
        }
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        CoroutineScope(EmptyCoroutineContext).launch {
            try {
                val result =
                    manager.getCredential(
                        context = context,
                        request = options.toGetRequestOption(),
                    )

                val rawResult =
                    if (result.credential is PublicKeyCredential) {
                        (result.credential as PublicKeyCredential).authenticationResponseJson
                    } else {
                        """ {"error":"no public key credential returned", "actual":"$result"} """.trim()
                    }

                successCallback(JSONObject(rawResult))
            } catch (th: Throwable) {
                failureCallback(th)
            }
        }
    }
}

private fun JSONObject.toCreateRequestOption(): CreateCredentialRequest =
    CreatePublicKeyCredentialRequest(
        getJSONObject("publicKey").toString(),
    )

private fun JSONObject.toGetRequestOption(): GetCredentialRequest =
    GetCredentialRequest(
        credentialOptions =
            listOf(
                GetPublicKeyCredentialOption(
                    requestJson = getString("publicKey").toString(),
                ),
            ),
    )
