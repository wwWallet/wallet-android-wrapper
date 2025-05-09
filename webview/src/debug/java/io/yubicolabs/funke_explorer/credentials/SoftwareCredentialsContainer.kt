@file:OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)

package io.yubicolabs.funke_explorer.credentials

import android.util.Log
import com.yubico.webauthn.data.AuthenticatorAssertionResponse
import com.yubico.webauthn.data.AuthenticatorAttestationResponse
import com.yubico.webauthn.data.AuthenticatorTransport
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs
import com.yubico.webauthn.data.CollectedClientData
import com.yubico.webauthn.data.Extensions
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions
import com.yubico.webauthn.data.UserVerificationRequirement
import de.adesso.softauthn.Authenticators
import de.adesso.softauthn.CredentialsContainer
import com.yubico.webauthn.data.ByteArray as YubiBytes
import de.adesso.softauthn.Origin
import de.adesso.softauthn.authenticator.WebAuthnAuthenticator
import io.yubicolabs.funke_explorer.BuildConfig
import io.yubicolabs.funke_explorer.json.toList
import io.yubicolabs.funke_explorer.tagForLog
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.SortedSet
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.optionals.getOrNull


internal class SoftwareCredentialsContainer : NavigatorCredentialsContainer {
    private val authenticator: WebAuthnAuthenticator = Authenticators.yubikey5Nfc().build()
    private val origin = URL(BuildConfig.BASE_URL).toOrigin()
    private val credentials = CredentialsContainer(origin, listOf(authenticator));

    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    ) = try {
        val publicKey = options.getJSONObject("publicKey")
        if (publicKey.has("hints")) {
            Log.i(tagForLog, "Currently hints are not supported in software. Ignoring them.")
            publicKey.remove("hints")
        }
        val softOptions = publicKey.toPKCCO()
        val credential = credentials.create(softOptions)

        val response = credential.toAttestationJson()
        successCallback(response)
    } catch (th: Throwable) {
        failureCallback(th)
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    ) = try {
        val credential = credentials.get(options.getJSONObject("publicKey").toPKCRO())
        val response = credential.toAssertionJson()
        successCallback(response)
    } catch (th: Throwable) {
        failureCallback(th)
    }
}


private fun URL.toOrigin() = Origin("https", host, -1, null)

private fun JSONObject.toPKCCO(): PublicKeyCredentialCreationOptions =
    PublicKeyCredentialCreationOptions.fromJson(toString())

private fun JSONObject.toPKCRO(): PublicKeyCredentialRequestOptions {
    val challenge = YubiBytes.fromBase64Url(getString("challenge"))

    val builder = PublicKeyCredentialRequestOptions.builder()
        .challenge(challenge)
        .rpId(getString("rpId"))
        .allowCredentials(getJSONArray("allowCredentials").toPublicKeyCredentialDescriptors())

    if (has("userVerification")) {
        builder.userVerification(getString("userVerification").toUserVerificationRequirement())
    }

    return builder.build()
}

private fun String.toUserVerificationRequirement(): UserVerificationRequirement = when (this) {
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
                val id = YubiBytes.fromBase64Url(rawId)
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

private fun PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>?.toAssertionJson(): JSONObject =
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
                "response" to responseJson,
            ).filter { entry -> entry.value != null }
        )
    }

private fun AuthenticatorAssertionResponse.toJson(): JSONObject = JSONObject(
    mapOf(
        "authenticatorData" to authenticatorData.base64Url,
        "clientDataJSON" to clientDataJSON.base64Url,
        "signature" to signature.base64Url,
        "userHandle" to userHandle.getOrNull()?.base64Url,
        "clientData" to clientData.toJson(),
    ).filter { entry -> entry.value != null }
)

private fun ClientAssertionExtensionOutputs.toJson(): JSONObject = JSONObject(
    mapOf(
        "appid" to appid.getOrNull(),
        "largeBlob" to largeBlob.getOrNull()?.toJson()
    ).filter { entry -> entry.value != null }
)

private fun Extensions.LargeBlob.LargeBlobAuthenticationOutput.toJson(): JSONObject =
    JSONObject(
        mapOf(
            "blob" to blob.getOrNull()?.base64Url,
            "written" to written.getOrNull(),
        ).filter { entry -> entry.value != null }
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
            ).filter { entry -> entry.value != null }
        )
    }

private fun ClientRegistrationExtensionOutputs.toJson() = JSONObject(
    mapOf(
        "appidExclude" to appidExclude.getOrNull(),
        "credProps" to credProps.getOrNull(),
        "largeBlob" to largeBlob.getOrNull(),
    ).filter { entry -> entry.value != null }
)

private fun AuthenticatorAttestationResponse.toJson() = JSONObject(
    mapOf(
        "attestationObject" to attestationObject.base64Url,
        "clientData" to clientData.toJson(),
        "clientDataJSON" to clientDataJSON.base64Url,
        "transports" to transports.toJson(),
    ).filter { entry -> entry.value != null }
)

private fun CollectedClientData.toJson() = JSONObject(
    mapOf(
        "challenge" to challenge.base64Url,
        "origin" to origin,
        "type" to type,
    ).filter { entry -> entry.value != null }
)

private fun SortedSet<AuthenticatorTransport>.toJson() = JSONArray(
    mapNotNull {
        it.id
    }
)
