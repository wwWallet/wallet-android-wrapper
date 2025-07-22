package io.yubicolabs.wwwwallet.credentials.provider

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import io.yubicolabs.wwwwallet.R
import io.yubicolabs.wwwwallet.credentials.Container
import io.yubicolabs.wwwwallet.credentials.BuildInContainer
import io.yubicolabs.wwwwallet.credentials.ContainerYubico
import io.yubicolabs.wwwwallet.credentials.SoftwareContainer
import io.yubicolabs.wwwwallet.tagForLog
import org.json.JSONObject

private const val EXTRA_KEY_IS_AUTO_SELECTED = "IS_AUTO_SELECTED"

class PasskeyProviderActivity : ComponentActivity() {

    lateinit var yubicoContainer: Container
    lateinit var builtInContainer: Container
    lateinit var softwareContainer: Container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        yubicoContainer = ContainerYubico(activity = this)
        builtInContainer = BuildInContainer()
        softwareContainer = SoftwareContainer()

        val requestId = intent.getIntExtra(EXTRA_KEY_REQUEST_ID, 0)
        when (requestId) {
            CREATE_SECURITY_KEY_REQUEST_ID, CREATE_CLIENT_DEVICE_REQUEST_ID -> createRequest(
                requestId
            )

            else -> {
                Toast.makeText(
                    applicationContext,
                    "Found unexpected request ID: 0x${String.format("%04X", requestId)}. ",
                    Toast.LENGTH_SHORT
                ).show()

                Log.e(tagForLog, "Could not identify request id $requestId. Ignored.")
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxSize(.5f),
                        )

                        Text(
                            modifier = Modifier.padding(32.dp),
                            text = stringResource(R.string.credential_provider_standby_message),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    fun createRequest(requestId: Int) {
        val request =
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)

        val accountId = intent.getStringExtra(EXTRA_KEY_ACCOUNT_ID)
        val isAutoSelected = intent.getBooleanExtra(EXTRA_KEY_IS_AUTO_SELECTED, false)

        if (request != null && request.callingRequest is CreatePublicKeyCredentialRequest) {
            val publicKeyRequest =
                request.callingRequest as CreatePublicKeyCredentialRequest

            val container = when (requestId) {
                CREATE_SECURITY_KEY_REQUEST_ID -> yubicoContainer
                CREATE_CLIENT_DEVICE_REQUEST_ID -> builtInContainer

                else -> null
            }

            if (container != null) {
                createPasskey(
                    container,
                    publicKeyRequest.requestJson,
                    request.callingAppInfo,
                    publicKeyRequest.clientDataHash,
                    accountId
                )
            }
        }
    }

    private fun createPasskey(
        container: Container,
        requestJson: String,
        callingAppInfo: CallingAppInfo,
        clientDataHash: ByteArray?,
        accountId: String?
    ) {
        container.create(
            options = JSONObject(mapOf("publicKey" to JSONObject(requestJson))),
            successCallback = {
                val registrationResponseJson = it.toString()
                val createPublicKeyCredResponse =
                    CreatePublicKeyCredentialResponse(registrationResponseJson)

                val result = Intent()

                PendingIntentHandler.setCreateCredentialResponse(
                    result,
                    createPublicKeyCredResponse
                )

                setResult(RESULT_OK, result)
                finish()
            },
            failureCallback = {
                val result = Intent()

                PendingIntentHandler.setCreateCredentialException(
                    result,
                    CreateCredentialCancellationException(
                        it.message
                    )
                )

                setResult(RESULT_CANCELED, result)
                finish()
            }
        )
    }
}
