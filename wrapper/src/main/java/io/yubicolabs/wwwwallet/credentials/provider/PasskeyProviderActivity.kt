@file:OptIn(ExperimentalUuidApi::class)

package io.yubicolabs.wwwwallet.credentials.provider

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.lifecycle.lifecycleScope
import io.yubicolabs.wwwwallet.R
import io.yubicolabs.wwwwallet.credentials.Container
import io.yubicolabs.wwwwallet.credentials.LocalContainer
import io.yubicolabs.wwwwallet.json.getNested
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.uuid.ExperimentalUuidApi

class PasskeyProviderActivity : ComponentActivity() {
    lateinit var localContainer: Container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        localContainer =
            LocalContainer(context = this).apply {
                YOLOLogger.i("KEY_STORE", "isStrongBoxed: $isStrongBoxed.")
            }

        setContent {
            MaterialTheme(
                if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                enableEdgeToEdge()

                Scaffold { paddingValues ->
                    Column(
                        modifier =
                            Modifier
                                .padding(paddingValues)
                                .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .padding(32.dp)
                                    .fillMaxSize(.5f),
                        )

                        Text(
                            modifier = Modifier.padding(32.dp),
                            text = stringResource(R.string.credential_provider_standby_message),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()

        handleActivityForResult()
    }

    private fun handleActivityForResult() {
        val requestCode = intent.getIntExtra(EXTRA_KEY_REQUEST_CODE, 0)
        val requestId = intent.getStringExtra(EXTRA_KEY_REQUEST_ID)

        when (requestCode) {
            CREATE_CLIENT_DEVICE_REQUEST_CODE -> createRequest(requestCode)
            GET_CLIENT_DEVICE_REQUEST_CODE -> getRequest(requestCode)

            else -> {
                Toast.makeText(
                    applicationContext,
                    "Found unexpected request ID: 0x${String.format("%04X", requestId)}. ",
                    Toast.LENGTH_SHORT,
                ).show()

                YOLOLogger.e(tagForLog, "Could not identify request id $requestId. Ignored.")
                finish()
            }
        }
    }

    private fun getRequest(requestCode: Int) {
        if (requestCode != GET_CLIENT_DEVICE_REQUEST_CODE) {
            return
        }

        YOLOLogger.d(tagForLog, "Get credential request found.")

        val publicKeyRequest = intent.getStringExtra(EXTRA_KEY_REQUEST_OPTIONS) ?: ""
        val requestOptions =
            JSONObject(
                mutableMapOf("publicKey" to JSONObject(publicKeyRequest)),
            )

        localContainer.get(
            options = requestOptions,
            successCallback = { credentialJson ->
                val result = Intent()
                PendingIntentHandler.setGetCredentialResponse(
                    result,
                    GetCredentialResponse(
                        androidx.credentials.PublicKeyCredential(
                            credentialJson.toString(),
                        ),
                    ),
                )
                setResult(RESULT_OK, result)
                finish()
            },
            failureCallback = {
                val result = Intent()

                PendingIntentHandler.setGetCredentialException(
                    result,
                    GetCredentialUnknownException(
                        it.message,
                    ),
                )

                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@PasskeyProviderActivity,
                        "Passkey getting failed: '${it.message}'.",
                        Toast.LENGTH_LONG,
                    ).show()

                    setResult(RESULT_OK, result)
                    finish()
                }
            },
        )
    }

    fun createRequest(requestCode: Int) {
        val request =
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)

        if (request != null && request.callingRequest is CreatePublicKeyCredentialRequest) {
            val publicKeyRequest =
                request.callingRequest as CreatePublicKeyCredentialRequest

            val publicKey = JSONObject(publicKeyRequest.candidateQueryData.getString(BUNDLE_KEY_REQUEST, "{}"))
            val requestJson =
                JSONObject(
                    mutableMapOf("publicKey" to publicKey),
                )

            localContainer.create(
                options = requestJson,
                successCallback = {
                    val registrationResponseJson = it.toString()
                    val createPublicKeyCredResponse =
                        CreatePublicKeyCredentialResponse(registrationResponseJson)

                    val result = Intent()

                    PendingIntentHandler.setCreateCredentialResponse(
                        result,
                        createPublicKeyCredResponse,
                    )

                    setResult(RESULT_OK, result)
                    finish()
                },
                failureCallback = {
                    val result = Intent()

                    PendingIntentHandler.setCreateCredentialException(
                        result,
                        CreateCredentialCancellationException(
                            it.message,
                        ),
                    )

                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@PasskeyProviderActivity,
                            "Passkey creation failed: '${it.message}'.",
                            Toast.LENGTH_LONG,
                        ).show()

                        setResult(RESULT_OK, result)
                        finish()
                    }
                },
            )
        }
    }
}
