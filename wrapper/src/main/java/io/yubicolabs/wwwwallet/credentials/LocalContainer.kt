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
import io.yubicolabs.wwwwallet.json.toMap
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val DEFAULT_KEY_STORE = "AndroidKeyStore"

class LocalContainer(
    val context: Context,
    // TODO REPLACE ME WITH AN ACTUAL REGISTERED AAGUID
    val aaguid: Uuid = Uuid.random(),
) : Container {
    private val origin: String = BuildConfig.APPLICATION_ID

    private val secureStore: KeyStore = KeyStore.getInstance(DEFAULT_KEY_STORE).apply { load(null) }

    val isStrongBoxed: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private val sha256 =
        try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("SHA-256 is not available", e)
        }

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

        val rpId =
            options.getNested("publicKey.rp.id") as? String
                ?: throw IllegalStateException("'publicKey.rp.id' on credential create options not set.")

        val clientDataJson =
            getClientOptions(
                type = "webauthn.create",
                challenge = challenge,
                origin = rpId,
            )

        val clientDataJsonB64 =
            encodeToString(
                clientDataJson,
                NO_PADDING or NO_WRAP or URL_SAFE,
            )

        val (attestationObject, authenticatorData) =
            createAttestationObject(
                rpId = rpId,
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

        keyPair.writeMetaDataStorage(credentialId, options)

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
    ): KeyGenParameterSpec =
        KeyGenParameterSpec
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
            ).build()

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
                rpId = rpId,
                userPresence = true,
                userVerification = requireUserVerification,
                signatureCounter = signatureCount,
                attestedCredentialData = attestedCredentialData,
                extensions = null,
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
            -7 -> ECGenParameterSpec("secp256r1") to KEY_ALGORITHM_EC
            -35 -> ECGenParameterSpec("secp384r1") to KEY_ALGORITHM_EC
            -36 -> ECGenParameterSpec("secp521r1") to KEY_ALGORITHM_EC
            else -> throw IllegalArgumentException("Unsupported algorithm: $alg")
        }
    }

    fun getAll(
        options: JSONObject,
        successCallback: (JSONArray) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) = try {
        val allowedCredentials: List<Map<*, *>> =
            (options.getNested("publicKey.allowCredentials") as? List<Map<*, *>>) ?: listOf<Map<*, *>>()

        val challenge =
            (options.getNested("publicKey.challenge") as? String)?.decodeBase64()?.toByteArray()
                ?: byteArrayOf()

        val rpId = options.getNested("publicKey.rpId") as? String ?: ""

        // retrieve allowed or all credentials
        val selectedCredentials =
            if (allowedCredentials.isNotEmpty()) {
                allowedCredentials.mapNotNull { allowed ->
                    val type = allowed.getOrDefault("type", null) as? String ?: ""
                    if (type != "public-key") {
                        YOLOLogger.e(tagForLog, "Found non 'public-key' credential id in allow list.")
                    }

                    val allowedIdB64 = allowed.getOrDefault("id", null) as? String ?: ""
                    val allowedIdRaw = allowedIdB64.decodeBase64()
                    val allowedId = allowedIdRaw?.hex() ?: ""
                    val alias = "$origin+$allowedId"

                    if (secureStore.containsAlias(alias)) {
                        alias to secureStore.getEntry(alias, null)
                    } else {
                        null
                    }
                }.associate { it }
            } else {
                secureStore.aliases().toList().associate { key ->
                    key to secureStore.getEntry(key, null)
                }
            }

        val finalSelection =
            selectedCredentials.filter {
                it.value != null &&
                    it.key.startsWith(origin)
            }

        val credentials =
            finalSelection.mapNotNull { selectionEntry ->
                val (key, keyEntry) = selectionEntry
                (keyEntry as? KeyStore.PrivateKeyEntry)
                    ?.toResponse(key, challenge, rpId)
            }

        successCallback(
            JSONArray(credentials),
        )
    } catch (th: Throwable) {
        YOLOLogger.e(tagForLog, "Couldn't return all credentials.", th)
        failureCallback(th)
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) = try {
        getAll(
            options = options,
            successCallback = { jsonArray ->
                if (jsonArray.length() > 0) {
                    successCallback(jsonArray.getJSONObject(0))
                } else {
                    failureCallback(IllegalStateException("Not one credential found."))
                }
            },
            failureCallback = failureCallback,
        )
    } catch (th: Throwable) {
        YOLOLogger.e(tagForLog, "Couldn't return credential.", th)
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
                origin = rpId,
            )
        val clientDataJsonB64 =
            encodeToString(
                clientDataJson,
                NO_PADDING or NO_WRAP or URL_SAFE,
            )
        val clientDataJsonHash = sha256.digest(clientDataJson)

        // TODO SAVE signCount IN META
        val authenticatorData =
            createAuthenticatorData(
                rpId = rpId,
                userPresence = true,
                userVerification = true,
                signatureCounter = 0,
                attestedCredentialData = null,
                extensions = null,
            )

        val toSign = authenticatorData + clientDataJsonHash
        val signature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(toSign)
                sign()
            }

        val meta = privateKey.readMetaDataStorage(credentialId)

        val userName = meta.getNested("publicKey.user.name") as? String ?: ""
        val userDisplayName = meta.getNested("publicKey.user.displayName") as? String ?: ""
        val userId = meta.getNested("publicKey.user.id") as? String ?: ""

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
                    "userHandle" to
                        encodeToString(
                            userId.toByteArray(),
                            NO_PADDING or NO_WRAP or URL_SAFE,
                        ),
                    "userName" to userName,
                    "userDisplayName" to userDisplayName,
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

    private fun KeyPair.writeMetaDataStorage(
        credentialId: ByteArray,
        options: JSONObject,
    ) = try {
        val input =
            JSONObject(
                options.toMap().toMutableMap().run {
                    put("credentialId", credentialId)
                    this
                },
            ).toString()

        val key = private.deriveKeyFromKeyPair()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(createCipherIV()))

        val output = cipher.doFinal(input.toByteArray())

        val name = credentialId.credIdToFilename()
        context.openFileOutput(name, Context.MODE_PRIVATE).use {
            it.write(output)
        }
    } catch (th: Throwable) {
        YOLOLogger.e(tagForLog, "Cant encrypt meta information.", th)
    }

    private fun PrivateKey.readMetaDataStorage(credentialId: ByteArray): JSONObject =
        try {
            val key = deriveKeyFromKeyPair()

            val name = credentialId.credIdToFilename()
            if (name !in context.fileList()) {
                throw IllegalStateException("File Not Found.")
            } else {
                val bytes =
                    context.openFileInput(name).use {
                        it.readAllBytes()
                    }

                val decipher = Cipher.getInstance("AES/GCM/NoPadding")
                decipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(createCipherIV()))

                val decoded = decipher.doFinal(bytes)
                val json = JSONObject(String(decoded))

                json
            }
        } catch (th: Throwable) {
            YOLOLogger.e(tagForLog, "Cant encrypt meta information.", th)

            JSONObject()
        }

    private fun ByteArray.credIdToFilename() =
        sha256
            .digest(
                "AES+${toHexString()}"
                    .toByteArray(),
            )
            .toHexString()
}

private fun PrivateKey.deriveKeyFromKeyPair(): SecretKeySpec {
    val secret =
        HKDF(
            // algo =
            "HmacSHA256",
        ).digest(
            // ikm =
            encoded,
            // salt =
            createHkdfSalt(),
            // info =
            createHkdfInfo(),
            // length =
            32,
        )

    val key = SecretKeySpec(secret, "AES")
    return key
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

private fun createCipherIV(): ByteArray = ByteArray(32) { it.toByte() }

private fun createHkdfSalt(): ByteArray = ByteArray(32)

private fun createHkdfInfo(): ByteArray = "CTAP2 HMAC key".toByteArray(StandardCharsets.UTF_8)

private operator fun ByteArray.times(count: Int): ByteArray {
    var result = byteArrayOf()
    repeat(count) {
        result += this
    }

    return result
}

private fun String.fullyQualified(): String = if (startsWith("https://")) this else "https://$this"
