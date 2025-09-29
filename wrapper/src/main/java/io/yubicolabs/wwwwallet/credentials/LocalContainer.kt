@file:OptIn(ExperimentalStdlibApi::class, ExperimentalUuidApi::class)

package io.yubicolabs.wwwwallet.credentials

import COSE.KeyKeys
import COSE.OneKey
import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.DIGEST_SHA256
import android.security.keystore.KeyProperties.DIGEST_SHA384
import android.security.keystore.KeyProperties.DIGEST_SHA512
import android.security.keystore.KeyProperties.KEY_ALGORITHM_EC
import android.security.keystore.KeyProperties.PURPOSE_SIGN
import android.security.keystore.KeyProperties.PURPOSE_VERIFY
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import com.upokecenter.cbor.CBORObject
import io.yubicolabs.wwwwallet.BuildConfig
import io.yubicolabs.wwwwallet.json.getNested
import io.yubicolabs.wwwwallet.json.toList
import io.yubicolabs.wwwwallet.json.toMap
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val DEFAULT_KEY_STORE = "AndroidKeyStore"

class LocalContainer(
    val context: Context,
    // TODO REPLACE ME WITH AN ACTUAL REGISTERED AAGUID
    val aaguid: Uuid = Uuid.random(),
) : Container {
    private val origin: String = BuildConfig.APPLICATION_ID
    private val storage: KeyStore = KeyStore.getInstance(DEFAULT_KEY_STORE).apply { load(null) }
    val isStrongBoxed: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) = try {
        val credential = createCredential(options)
        successCallback(credential)
    } catch (th: Throwable) {
        YOLOLogger.e(tagForLog, "Cannot create credential.", th)
        failureCallback(th)
    }

    private fun createCredential(options: JSONObject): JSONObject {
        val selectedAlgorithm = selectAlgorithm(options)
        val (algorithmSpec, keyAlgorithm) = getAlgorithmParams(selectedAlgorithm)

        val credentialId = ByteArray(32)
        SecureRandom().nextBytes(credentialId)

        val challenge =
            (options.getNested("publicKey.challenge") as? String)?.decodeBase64()?.toByteArray()
                ?: byteArrayOf()

        val spec = buildKeyGenParameterSpec(credentialId, challenge, algorithmSpec)
        val keyPair = generateKeyPair(keyAlgorithm, spec)

        val clientDataJson =
            getClientOptions(
                type = "webauthn.create",
                challenge = challenge,
                origin = origin,
            )

        val clientDataJsonB64 =
            encodeToString(
                clientDataJson,
                NO_PADDING or NO_WRAP or URL_SAFE,
            )

        val (attestationObject, authenticatorData) =
            createAttestationObject(
                rpId = options.getNested("publicKey.rp.id") as? String ?: "",
                credentialId = credentialId,
                publicKey = keyPair.toCoseBytes(selectedAlgorithm),
                // TODO: CHECK WITH OPTIONS
                requireUserVerification = true,
                // TODO: STORE THIS?
                signatureCount = 0,
            )

        val credential =
            createPublicKeyCredential(
                credentialId,
                clientDataJsonB64,
                attestationObject,
                authenticatorData,
                selectedAlgorithm,
                keyPair.public.encoded,
            )

        YOLOLogger.i(tagForLog, "Created credential: $credential")

        return credential
    }

    private fun selectAlgorithm(options: JSONObject): Int {
        val pubKeyCredParams: List<*> =
            options.getNested("publicKey.pubKeyCredParams") as? List<*> ?: listOf<Any>()

        val selectedAlgorithm =
            pubKeyCredParams.firstNotNullOfOrNull {
                val alg = (it as? Map<*, *>)?.get("alg") as? Int
                if (alg != null && isAlgorithmSupported(alg)) {
                    alg
                } else {
                    null
                }
            }

        return selectedAlgorithm
            ?: throw IllegalArgumentException("No supported algorithm found in pubKeyCredParams")
    }

    private fun buildKeyGenParameterSpec(
        credentialId: ByteArray,
        challenge: ByteArray,
        algorithmSpec: AlgorithmParameterSpec,
    ): KeyGenParameterSpec {
        return KeyGenParameterSpec
            .Builder(
                "$origin+${credentialId.toHexString()}",
                PURPOSE_SIGN or PURPOSE_VERIFY,
            )
            .setAlgorithmParameterSpec(algorithmSpec)
            .setIsStrongBoxBacked(
                isStrongBoxed,
            ).setDigests(
                DIGEST_SHA256,
                DIGEST_SHA384,
                DIGEST_SHA512,
            ).setAttestationChallenge(
                challenge,
            ).setUserPresenceRequired(true)
            .build()
    }

    private fun generateKeyPair(
        keyAlgorithm: String,
        spec: KeyGenParameterSpec,
    ): KeyPair {
        val keyPairGen =
            KeyPairGenerator.getInstance(
                keyAlgorithm,
                DEFAULT_KEY_STORE,
            )
        keyPairGen.initialize(spec)
        return keyPairGen.genKeyPair()
    }

    private fun createPublicKeyCredential(
        credentialId: ByteArray,
        clientDataJsonB64: String,
        attestationObject: String,
        authenticatorData: ByteArray,
        publicKeyAlgorithm: Int,
        publicKey: ByteArray,
    ): JSONObject {
        val response =
            JSONObject(
                mapOf(
                    "clientDataJSON" to clientDataJsonB64,
                    "attestationObject" to attestationObject,
                    "authenticatorData" to
                        encodeToString(
                            authenticatorData,
                            NO_PADDING or NO_WRAP or URL_SAFE,
                        ),
                    "publicKeyAlgorithm" to publicKeyAlgorithm,
                    "publicKey" to
                        encodeToString(
                            publicKey,
                            NO_PADDING or NO_WRAP or URL_SAFE,
                        ),
                    "transports" to JSONArray(arrayOf("internal", "hybrid")),
                ),
            )

        return JSONObject(
            mapOf(
                "rawId" to
                    encodeToString(
                        credentialId,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
                "id" to
                    encodeToString(
                        credentialId,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
                "type" to "public-key",
                "authenticatorAttachment" to "platform",
                "response" to response,
                "clientExtensionResults" to JSONObject(),
            ),
        )
    }

    private fun getClientOptions(
        type: String,
        challenge: ByteArray,
        origin: String,
    ): ByteArray {
        return JSONObject(
            mapOf(
                "type" to type,
                "challenge" to encodeToString(challenge, NO_PADDING or NO_WRAP or URL_SAFE),
                "origin" to origin,
                "crossOrigin" to false,
            ),
        ).toString().toByteArray()
    }

    private fun createAttestationObject(
        rpId: String,
        credentialId: ByteArray,
        publicKey: ByteArray,
        requireUserVerification: Boolean,
        signatureCount: Int,
    ): Pair<String, ByteArray> {
        val attestedCredentialData = createAttestedCredentialData(credentialId, publicKey)

        val authenticatorData =
            createAuthenticatorData(
                rpId,
                true,
                requireUserVerification,
                signatureCount,
                attestedCredentialData,
                null,
            )

        val attestationObject =
            encodeToString(
                CBORObject.NewMap()
                    .Add("fmt", "none")
                    .Add("attStmt", CBORObject.NewMap())
                    .Add("authData", authenticatorData)
                    .EncodeToBytes(),
                NO_PADDING or NO_WRAP or URL_SAFE,
            )
        return attestationObject to authenticatorData
    }

    private fun createAttestedCredentialData(
        credentialId: ByteArray,
        cosePublicKey: ByteArray,
    ): ByteArray {
        val attestedCredentialDataLength = 16 + 2 + credentialId.size + cosePublicKey.size
        return ByteBuffer.allocate(attestedCredentialDataLength)
            .order(ByteOrder.BIG_ENDIAN)
            .put(aaguid.toByteArray(), 0, 16)
            .putShort(credentialId.size.toShort())
            .put(credentialId)
            .put(cosePublicKey)
            .array()
    }

    private fun createAuthenticatorData(
        rpId: String,
        userPresence: Boolean,
        userVerification: Boolean,
        signatureCounter: Int,
        attestedCredentialData: ByteArray?,
        extensions: ByteArray?,
    ): ByteArray {
        val sha256 =
            try {
                MessageDigest.getInstance("SHA-256")
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("SHA-256 is not available", e)
            }

        val rpIdHash = sha256.digest(rpId.toByteArray())
        val ed = extensions != null
        val at = attestedCredentialData != null

        val flags = generateAuthenticatorDataFlags(ed, at, userVerification, userPresence)
        val authenticatorDataLength =
            32 + 1 + 4 + (
                attestedCredentialData?.size
                    ?: 0
            ) + (extensions?.size ?: 0)
        val authenticatorData =
            ByteBuffer.allocate(authenticatorDataLength)
                .order(ByteOrder.BIG_ENDIAN)
                .put(rpIdHash, 0, 32)
                .put(flags)
                .putInt(signatureCounter)
        if (at) {
            authenticatorData.put(attestedCredentialData)
        }
        if (ed) {
            authenticatorData.put(extensions)
        }
        return authenticatorData.array()
    }

    private fun generateAuthenticatorDataFlags(
        ed: Boolean,
        at: Boolean,
        uv: Boolean,
        up: Boolean,
    ): Byte {
        var flags = 0
        if (up) {
            flags = flags or 0x01
        }
        if (uv) {
            flags = flags or 0x04
        }
        if (at) {
            flags = flags or 0x40
        }
        if (ed) {
            flags = flags or 0x80
        }
        return flags.toByte()
    }

    private fun isAlgorithmSupported(alg: Int): Boolean {
        return alg in listOf(-7, -257, -35, -36)
    }

    private fun getAlgorithmParams(alg: Int): Pair<AlgorithmParameterSpec, String> {
        return when (alg) {
            -7 -> Pair(ECGenParameterSpec("secp256r1"), KEY_ALGORITHM_EC)
            -35 -> Pair(ECGenParameterSpec("secp384r1"), KEY_ALGORITHM_EC)
            -36 -> Pair(ECGenParameterSpec("secp521r1"), KEY_ALGORITHM_EC)
            else -> throw IllegalArgumentException("Unsupported algorithm: $alg")
        }
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) = try {
        val allowedCredentials: List<Map<String, Any?>> =
            (
                (options.getNested("publicKey.allowCredentials") as? JSONArray)?.toList()
                    ?: listOf()
            ).mapNotNull {
                (it as? JSONObject)?.toMap()
            }

        val challenge =
            (options.getNested("publicKey.challenge") as? String)?.decodeBase64()?.toByteArray()
                ?: byteArrayOf()

        val rpId = options.getNested("publicKey.rpId") as? String ?: origin

        // retrieve all credentials
        val selectedCredentials =
            storage.aliases().toList().associate { alias ->
                alias to storage.getEntry(alias, null)
            }.toMutableMap()

        for (allowed in allowedCredentials) {
            val allowedId = allowed.getOrDefault("id", null) as? String ?: ""
            val alias = "$origin+$allowedId"

            if (origin !in alias || !storage.containsAlias(alias)) {
                selectedCredentials[alias] = null
            }
        }

        val finalSelection = selectedCredentials.filter { it.value != null }
        if (finalSelection.isNotEmpty()) {
            // TODO MULTIPLE MATCHES??

            val firstKey = finalSelection.keys.first()
            val first = finalSelection[firstKey]!! as KeyStore.PrivateKeyEntry
            val credentialResponse = first.toResponse(firstKey, challenge, rpId)

            successCallback(
                credentialResponse,
            )
        } else {
            val ids =
                allowedCredentials
                    .joinToString(separator = ",") {
                        (it as? JSONObject)?.get("id") as? String ?: "null"
                    }

            failureCallback(
                NoSuchElementException("No credential with id in [$ids] found in credential storage."),
            )
        }
    } catch (th: Throwable) {
        failureCallback(th)
    }

    private fun KeyStore.PrivateKeyEntry.toResponse(
        id: String,
        challenge: ByteArray,
        rpId: String,
    ): JSONObject {
        val credentialId =
            id.replace("$origin+", "").hexToByteArray()

        val clientDataJson =
            getClientOptions(
                type = "webauthn.get",
                challenge = challenge,
                origin = origin,
            )
        val clientDataJsonB64 =
            encodeToString(
                clientDataJson,
                NO_PADDING or NO_WRAP or URL_SAFE,
            )
        val clientDataJsonHash = MessageDigest.getInstance("SHA-256").digest(clientDataJson)

        val authenticatorData =
            createAuthenticatorData(
                rpId = rpId,
                userPresence = true,
                userVerification = true,
                signatureCounter = 0,
                attestedCredentialData = null,
                extensions = null,
            )

        val signature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(authenticatorData)
                update(clientDataJsonHash)
                sign()
            }

        val response =
            JSONObject(
                mapOf(
                    "clientDataJSON" to clientDataJsonB64,
                    "authenticatorData" to
                        encodeToString(
                            authenticatorData,
                            NO_PADDING or NO_WRAP or URL_SAFE,
                        ),
                    "signature" to
                        encodeToString(
                            signature,
                            NO_PADDING or NO_WRAP or URL_SAFE,
                        ),
                    "userHandle" to null,
                ),
            )

        return JSONObject(
            mapOf(
                "rawId" to
                    encodeToString(
                        credentialId,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
                "id" to
                    encodeToString(
                        credentialId,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
                "type" to "public-key",
                "authenticatorAttachment" to "platform",
                "response" to response,
                "clientExtensionResults" to JSONObject(),
            ),
        )
    }
}

private fun KeyPair.toCoseBytes(algorithm: Int): ByteArray =
    try {
        OneKey(public, null).run {
            add(KeyKeys.Algorithm, CBORObject.FromObject(algorithm))
            EncodeToBytes()
        }
    } catch (th: Throwable) {
        YOLOLogger.e("COSE", "Error while building the cose bytes.", th)
        byteArrayOf()
    }

private fun Map<Int, Any>.toCbor(): CBORObject {
    val result = CBORObject.NewMap()

    forEach {
        val (key, value) = it
        val typedValue =
            when (value) {
                is Int -> CBORObject.FromObject(value)
                is ByteArray -> CBORObject.FromObject(value)
                else -> throw RuntimeException("Type '${value.javaClass.simpleName}' did not map to any registered cbor types.")
            }

        result.set(key, typedValue)
    }

    return result
}
