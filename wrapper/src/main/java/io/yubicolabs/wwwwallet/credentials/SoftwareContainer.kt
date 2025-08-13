package io.yubicolabs.wwwwallet.credentials

import android.content.Context
import com.yubico.webauthn.data.AuthenticatorAssertionResponse
import com.yubico.webauthn.data.AuthenticatorAttestationResponse
import com.yubico.webauthn.data.AuthenticatorTransport
import com.yubico.webauthn.data.ByteArray.fromBase64Url
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs
import com.yubico.webauthn.data.CollectedClientData
import com.yubico.webauthn.data.Extensions
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import de.adesso.softauthn.authenticator.functional.exception.MutiplePublicKeysFoundException
import io.yubicolabs.wwwwallet.json.toList
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.storage.CredentialStorage
import io.yubicolabs.wwwwallet.tagForLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.SortedSet
import kotlin.jvm.optionals.getOrNull

class SoftwareContainer(context: Context) : Container {
    private val storage = CredentialStorage(context.applicationContext)

    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val publicKey = options.getJSONObject("publicKey")
                if (publicKey.has("hints")) {
                    YOLOLogger.i(
                        tagForLog,
                        "Currently hints are not supported in software. Ignoring them.",
                    )
                    publicKey.remove("hints")
                }
                val credentialContainer = storage.restore()
                val softOptions = publicKey.toPKCCO()

                val credential = credentialContainer.create(softOptions)
                if (credential != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        storage.store(credentialContainer)
                    }
                }

                val response = credential.toAttestationJson()

                successCallback(response)
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
        CoroutineScope(Dispatchers.IO).launch {
            val credentialContainer = storage.restore()

            try {
                val credential =
                    credentialContainer.get(options.getJSONObject("publicKey").toPKCRO())

                if (credential != null) {
                    val user = credentialContainer.getUser(credential.response.userHandle.get())
                    val response = credential.toAssertionJson(user)

                    successCallback(response)
                } else {
                    failureCallback(NoSuchElementException())
                }
            } catch (e: MutiplePublicKeysFoundException) {
                YOLOLogger.e(tagForLog, "Multiple Credentials Found", e)

                val creds =
                    e.publicKeys.map { key ->
                        val userHandle = key.response.userHandle.get()
                        val user = credentialContainer.getUser(userHandle)

                        key.toAssertionJson(user)
                    }

                successCallback(JSONObject(mapOf("credentials" to creds)))
            } catch (th: Throwable) {
                failureCallback(th)
            }
        }
    }

    private fun JSONObject.toPKCCO(): PublicKeyCredentialCreationOptions = PublicKeyCredentialCreationOptions.fromJson(toString())

    private fun JSONObject.toPKCRO(): PublicKeyCredentialRequestOptions {
        val challenge = fromBase64Url(getString("challenge"))

        val builder =
            PublicKeyCredentialRequestOptions.builder()
                .challenge(challenge)
                .rpId(getString("rpId"))
                .allowCredentials(getJSONArray("allowCredentials").toPublicKeyCredentialDescriptors())

        if (has("userVerification")) {
            builder.userVerification(getString("userVerification").toUserVerificationRequirement())
        }

        return builder.build()
    }

    private fun String.toUserVerificationRequirement(): UserVerificationRequirement =
        when (this) {
            "discouraged" -> UserVerificationRequirement.DISCOURAGED
            "preferred" -> UserVerificationRequirement.PREFERRED
            "required" -> UserVerificationRequirement.REQUIRED
            else -> UserVerificationRequirement.REQUIRED
        }

    private fun JSONArray.toPublicKeyCredentialDescriptors(): List<PublicKeyCredentialDescriptor> =
        toList().mapNotNull {
            if (it is Map<*, *>) {
                val rawId = it["id"]
                if (rawId is String) {
                    val id = fromBase64Url(rawId)
                    PublicKeyCredentialDescriptor.builder()
                        .id(id)
                        .build()
                } else {
                    null
                }
            } else {
                null
            }
        }

    private fun PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>?.toAssertionJson(user: UserIdentity): JSONObject =
        if (this == null) {
            JSONObject()
        } else {
            val extensionResultsJson = clientExtensionResults.toJson()
            val responseJson = response.toJson()

            JSONObject(
                mapOf(
                    "id" to id.base64Url,
                    "rawId" to id.base64Url,
                    "type" to type.name,
                    "clientExtensionResults" to extensionResultsJson,
                    "userName" to user.name,
                    "userDisplayName" to user.displayName,
                    "response" to responseJson,
                ).filter { entry -> entry.value != null },
            )
        }

    private fun AuthenticatorAssertionResponse.toJson(): JSONObject =
        JSONObject(
            mapOf(
                "authenticatorData" to authenticatorData.base64Url,
                "clientDataJSON" to clientDataJSON.base64Url,
                "signature" to signature.base64Url,
                "userHandle" to userHandle.getOrNull()?.base64Url,
                "clientData" to clientData.toJson(),
            ).filter { entry -> entry.value != null },
        )

    private fun ClientAssertionExtensionOutputs.toJson(): JSONObject =
        JSONObject(
            mapOf(
                "appid" to appid.getOrNull(),
                "largeBlob" to largeBlob.getOrNull()?.toJson(),
            ).filter { entry -> entry.value != null },
        )

    private fun Extensions.LargeBlob.LargeBlobAuthenticationOutput.toJson(): JSONObject =
        JSONObject(
            mapOf(
                "blob" to blob.getOrNull()?.base64Url,
                "written" to written.getOrNull(),
            ).filter { entry -> entry.value != null },
        )

    private fun PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs>?.toAttestationJson(): JSONObject =
        if (this == null) {
            JSONObject()
        } else {
            val extensionResults = clientExtensionResults.toJson()
            val response = response.toJson()

            JSONObject(
                mapOf(
                    "id" to id.base64Url,
                    "rawId" to id.base64Url,
                    "type" to type.name,
                    "clientExtensionResults" to extensionResults,
                    "response" to response,
                ).filter { entry -> entry.value != null },
            )
        }

    private fun ClientRegistrationExtensionOutputs.toJson() =
        JSONObject(
            mapOf(
                "appidExclude" to appidExclude.getOrNull(),
                "credProps" to credProps.getOrNull(),
                "largeBlob" to largeBlob.getOrNull(),
            ).filter { entry -> entry.value != null },
        )

    private fun AuthenticatorAttestationResponse.toJson() =
        JSONObject(
            mapOf(
                "attestationObject" to attestationObject.base64Url,
                "clientData" to clientData.toJson(),
                "clientDataJSON" to clientDataJSON.base64Url,
                "transports" to transports.toJson(),
            ).filter { entry -> entry.value != null },
        )

    private fun CollectedClientData.toJson() =
        JSONObject(
            mapOf(
                "challenge" to challenge.base64Url,
                "origin" to origin,
                "type" to type,
            ).filter { entry -> entry.value != null },
        )

    private fun SortedSet<AuthenticatorTransport>.toJson() =
        JSONArray(
            mapNotNull {
                it.id
            },
        )
}
