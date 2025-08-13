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
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import com.yubico.yubikit.fido.webauthn.PublicKeyCredential.PUBLIC_KEY_CREDENTIAL_TYPE
import io.yubicolabs.wwwwallet.R
import io.yubicolabs.wwwwallet.credentials.Container
import io.yubicolabs.wwwwallet.credentials.ContainerYubico
import io.yubicolabs.wwwwallet.credentials.SoftwareContainer
import io.yubicolabs.wwwwallet.json.getNested
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog
import org.json.JSONArray
import org.json.JSONObject

class PasskeyProviderActivity : ComponentActivity() {
    lateinit var yubicoContainer: Container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        yubicoContainer = ContainerYubico(activity = this)

        val requestId = intent.getIntExtra(EXTRA_KEY_REQUEST_ID, 0)
        when (requestId) {
            CREATE_SECURITY_KEY_REQUEST_ID, CREATE_CLIENT_DEVICE_REQUEST_ID -> createRequest()

            GET_CREDENTIAL_REQUEST_ID -> getRequest()

            0x411 -> {
                val json = intent.getStringExtra(EXTRA_KEY_CREDENTIALS_JSON) ?: "[\"nothing\"]"
                val credentials = JSONArray(json)
                val message = credentials.toString(4)

                YOLOLogger.i(tagForLog, message)
                Toast.makeText(
                    this,
                    "Found ${credentials.length()} credentials.",
                    Toast.LENGTH_LONG,
                ).show()

                PendingIntentHandler.setGetCredentialException(intent, NoCredentialException())
                setResult(RESULT_CANCELED)
                finish()
            }

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

    private fun getRequest() {
        YOLOLogger.d(tagForLog, "Get credential request found.")

        // get credential selected
        val credentialJson = intent.getStringExtra(EXTRA_KEY_CREDENTIALS_JSON)
        if (credentialJson != null) {
            val credential =
                PublicKeyCredential(
                    JSONObject(credentialJson)
                        .put("type", PUBLIC_KEY_CREDENTIAL_TYPE)
                        .put("clientExtensionResults", JSONObject())
                        .toString(),
                )

            YOLOLogger.i(tagForLog, "Found this credential in bundle extras: $credentialJson.")
            val response = GetCredentialResponse(credential)

            val intent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                intent,
                response,
            )

            setResult(RESULT_OK, intent)
        } else {
            val intent = Intent()
            PendingIntentHandler.setGetCredentialException(
                intent,
                NoCredentialException(),
            )

            setResult(RESULT_CANCELED, intent)
        }

        finish()
    }

    fun createRequest() {
        val request =
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)

        val accountId = intent.getStringExtra(EXTRA_KEY_USER_ID)

        if (request != null && request.callingRequest is CreatePublicKeyCredentialRequest) {
            val publicKeyRequest =
                request.callingRequest as CreatePublicKeyCredentialRequest

            val requestJson =
                JSONObject(publicKeyRequest.candidateQueryData.getString(BUNDLE_KEY_REQUEST, "{}"))
            val userId = requestJson.getNested("user.id")
            val userName = requestJson.getNested("user.name")
            val userDisplayName = requestJson.getNested("user.displayName")

//            val container = when (requestId) {
//                CREATE_SECURITY_KEY_REQUEST_ID -> yubicoContainer
//                CREATE_CLIENT_DEVICE_REQUEST_ID -> builtInContainer
//
//                else -> null
//            }
            val container = SoftwareContainer(applicationContext)

            if (container != null) {
                createPasskey(
                    container,
                    publicKeyRequest.requestJson,
                    request.callingAppInfo,
                    publicKeyRequest.clientDataHash,
                    accountId,
                )
            }
        }
    }

    private fun createPasskey(
        container: Container,
        requestJson: String,
        callingAppInfo: CallingAppInfo,
        clientDataHash: ByteArray?,
        accountId: String?,
    ) {
        container.create(
            options =
                JSONObject(
                    mapOf(
                        "publicKey" to JSONObject(requestJson),
                        "rp" to callingAppInfo.packageName,
                        "cliendData" to clientDataHash,
                        "accountId" to accountId,
                    ),
                ),
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

                setResult(RESULT_CANCELED, result)
                finish()
            },
        )
    }
}
