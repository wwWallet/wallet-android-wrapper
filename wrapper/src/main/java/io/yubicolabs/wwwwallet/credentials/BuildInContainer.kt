package io.yubicolabs.wwwwallet.credentials

import android.util.Log
import io.yubicolabs.wwwwallet.json.b64
import io.yubicolabs.wwwwallet.json.toList
import io.yubicolabs.wwwwallet.tagForLog
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.NamedParameterSpec.X25519

class BuildInContainer : Container {
    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    ) = try {
        // Generate a credentialId
        val credentialId = ByteArray(32)
        SecureRandom().nextBytes(credentialId)
        val credentialId64 = credentialId.b64()

        val publickey = options.getJSONObject("publicKey")

        var spec = if (publickey.has("pubKeyCredParams")) {
            val credParams = publickey.getJSONArray("pubKeyCredParams").toList()

            credParams.firstNotNullOfOrNull {
                try {
                    val paramMap = it as Map<*, *>
                    val type = paramMap["type"]
                    val algorithm = paramMap["alg"] as Number
                    val name = algorithm.toAlgorithmName()
//                    val spec = algorithm.toSpec()

                    if (type == "public-key" && name?.startsWith("ES") == true) {
                        ECGenParameterSpec(name)
                    } else {
                        null
                    }
                } catch (th: Throwable) {
                    Log.e(tagForLog, "Error while creating parameter spec.", th)
                    null
                }
            }
        } else {
            null
        }

        if (spec == null) {
            // fallback
            spec = ECGenParameterSpec("secp256r1")
        }

        // Generate a credential key pair
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(spec.name.drop(2).toInt())

        val keyPair = keyPairGen.genKeyPair()

        // TODO: !!SAVE!!

        // TODO:SIGNATURE / PK???
        successCallback(
            JSONObject(
                mapOf(
                    "id" to credentialId64,
                    "rawId" to credentialId64,
                    "type" to "public-key",
                    "clientExtensionResults" to JSONObject(), // TODO ADD PRF! BLOBS! ALL THE THINGS
                    "response" to JSONObject(
                        mapOf(
                            "attestationObject" to "",
                            "clientData" to "",
                            "clientDataJSON" to "",
                            "transports" to "",
                        ).filter { entry -> entry.value != null },
                    ),
                ).filter { entry -> entry.value != null },
            )
        )
    } catch (th: Throwable) {
        failureCallback(th)
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    ) = try {
        successCallback(
            JSONObject(
                mapOf(
                    "id" to "SOME_ID_NOT_GOOD_SPEC",
                    "rawId" to "SOME_ID_NOT_GOOD_SPEC",
                    "type" to "public-key",
                    "clientExtensionResults" to JSONObject(), // TODO ADD PRF! BLOBS! ALL THE THINGS
                    "response" to JSONObject(
                        mapOf(
                            "attestationObject" to "",
                            "clientData" to "",
                            "clientDataJSON" to "",
                            "transports" to "",
                        ).filter { entry -> entry.value != null },
                    ),
                ).filter { entry -> entry.value != null },
            )
        )
    } catch (th: Throwable) {
        failureCallback(th)
    }
}

private fun Number.toAlgorithmName(): String? = when (this) {
    -65536 -> null
    -65535 -> "RS1"
    -65534 -> "A128CTR"
    -65533 -> "A192CTR"
    -65532 -> "A256CTR"
    -65531 -> "A128CBC"
    -65530 -> "A192CBC"
    -65529 -> "A256CBC"
    in -65528..-269 -> null
    -268 -> "ESB512"
    -267 -> "ESB384"
    -266 -> "ESB320"
    -265 -> "ESB256"
    -264 -> "KT256"
    -263 -> "KT128"
    -262 -> "TurboSHAKE256"
    -261 -> "TurboSHAKE128"
    -260 -> "WalnutDSA"
    -259 -> "RS512"
    -258 -> "RS384"
    -257 -> "RS256"
    in -256..-54 -> null
    -53 -> "Ed448"
    -52 -> "ESP512"
    -51 -> "ESP384"
    -50 -> "ML-DSA-87"
    -49 -> "ML-DSA-65"
    -48 -> "ML-DSA-44"
    -47 -> "ES256K"
    -46 -> "HSS-LMS"
    -45 -> "SHAKE256"
    -44 -> "SHA-512"
    -43 -> "SHA-384"
    -42 -> "RSAES-OAEP w/ SHA-512"
    -41 -> "RSAES-OAEP w/ SHA-256"
    -40 -> "RSAES-OAEP w/ RFC 8017 default parameters"
    -39 -> "PS512"
    -38 -> "PS384"
    -37 -> "PS256"
    -36 -> "ES512"
    -35 -> "ES384"
    -34 -> "ECDH-SS + A256KW"
    -33 -> "ECDH-SS + A192KW"
    -32 -> "ECDH-SS + A128KW"
    -31 -> "ECDH-ES + A256KW"
    -30 -> "ECDH-ES + A192KW"
    -29 -> "ECDH-ES + A128KW"
    -28 -> "ECDH-SS + HKDF-512"
    -27 -> "ECDH-SS + HKDF-256"
    -26 -> "ECDH-ES + HKDF-512"
    -25 -> "ECDH-ES + HKDF-256"
    in -24..-20 -> null
    -19 -> "Ed25519"
    -18 -> "SHAKE128"
    -17 -> "SHA-512/256"
    -16 -> "SHA-256"
    -15 -> "SHA-256/64"
    -14 -> "SHA-1"
    -13 -> "direct+HKDF-AES-256"
    -12 -> "direct+HKDF-AES-128"
    -11 -> "direct+HKDF-SHA-512"
    -10 -> "direct+HKDF-SHA-256"
    -9 -> "ESP256"
    -8 -> "EdDSA"
    -7 -> "ES256"
    -6 -> "direct"
    -5 -> "A256KW"
    -4 -> "A192KW"
    -3 -> "A128KW"
    in -2..-1 -> null
    0 -> null
    1 -> "A128GCM"
    2 -> "A192GCM"
    3 -> "A256GCM"
    4 -> "HMAC 256/64"
    5 -> "HMAC 256/256"
    6 -> "HMAC 384/384"
    7 -> "HMAC 512/512"
    in 8..9 -> null
    10 -> "AES-CCM-16-64-128"
    11 -> "AES-CCM-16-64-256"
    12 -> "AES-CCM-64-64-128"
    13 -> "AES-CCM-64-64-256"
    14 -> "AES-MAC 128/64"
    15 -> "AES-MAC 256/64"
    in 16..23 -> null
    24 -> "ChaCha20/Poly1305"
    25 -> "AES-MAC 128/128"
    26 -> "AES-MAC 256/128"
    in 27..29 -> null
    30 -> "AES-CCM-16-128-128"
    31 -> "AES-CCM-16-128-256"
    32 -> "AES-CCM-64-128-128"
    33 -> "AES-CCM-64-128-256"
    34 -> null
    else -> "Reserved for Private Use"
}

/**
private fun Number.toSpec(): AlgorithmParameterSpec? = when (this) {
-65536 -> null
-65535 -> "RS1"
-65534 -> "A128CTR"
-65533 -> "A192CTR"
-65532 -> "A256CTR"
-65531 -> "A128CBC"
-65530 -> "A192CBC"
-65529 -> "A256CBC"
in -65528..-269 -> null
-268 -> "ESB512"
-267 -> "ESB384"
-266 -> "ESB320"
-265 -> "ESB256"
-264 -> "KT256"
-263 -> "KT128"
-262 -> "TurboSHAKE256"
-261 -> "TurboSHAKE128"
-260 -> "WalnutDSA"
-259 -> "RS512"
-258 -> "RS384"
-257 -> "RS256"
in -256..-54 -> null
-53 -> "Ed448"
-52 -> "ESP512"
-51 -> "ESP384"
-50 -> "ML-DSA-87"
-49 -> "ML-DSA-65"
-48 -> "ML-DSA-44"
-47 -> "ES256K"
-46 -> "HSS-LMS"
-45 -> "SHAKE256"
-44 -> "SHA-512"
-43 -> "SHA-384"
-42 -> "RSAES-OAEP w/ SHA-512"
-41 -> "RSAES-OAEP w/ SHA-256"
-40 -> "RSAES-OAEP w/ RFC 8017 default parameters"
-39 -> "PS512"
-38 -> "PS384"
-37 -> "PS256"
-36 -> "ES512"
-35 -> "ES384"
-34 -> "ECDH-SS + A256KW"
-33 -> "ECDH-SS + A192KW"
-32 -> "ECDH-SS + A128KW"
-31 -> "ECDH-ES + A256KW"
-30 -> "ECDH-ES + A192KW"
-29 -> "ECDH-ES + A128KW"
-28 -> "ECDH-SS + HKDF-512"
-27 -> "ECDH-SS + HKDF-256"
-26 -> "ECDH-ES + HKDF-512"
-25 -> "ECDH-ES + HKDF-256"
in -24..-20 -> null
-19 -> "Ed25519"
-18 -> "SHAKE128"
-17 -> "SHA-512/256"
-16 -> "SHA-256"
-15 -> "SHA-256/64"
-14 -> "SHA-1"
-13 -> "direct+HKDF-AES-256"
-12 -> "direct+HKDF-AES-128"
-11 -> "direct+HKDF-SHA-512"
-10 -> "direct+HKDF-SHA-256"
-9 -> "ESP256"
-8 -> "EdDSA"
-7 -> "ES256"
-6 -> "direct"
-5 -> "A256KW"
-4 -> "A192KW"
-3 -> "A128KW"
in -2..-1 -> null
0 -> null
1 -> "A128GCM"
2 -> "A192GCM"
3 -> "A256GCM"
4 -> "HMAC 256/64"
5 -> "HMAC 256/256"
6 -> "HMAC 384/384"
7 -> "HMAC 512/512"
in 8..9 -> null
10 -> "AES-CCM-16-64-128"
11 -> "AES-CCM-16-64-256"
12 -> "AES-CCM-64-64-128"
13 -> "AES-CCM-64-64-256"
14 -> "AES-MAC 128/64"
15 -> "AES-MAC 256/64"
in 16..23 -> null
24 -> "ChaCha20/Poly1305"
25 -> "AES-MAC 128/128"
26 -> "AES-MAC 256/128"
in 27..29 -> null
30 -> "AES-CCM-16-128-128"
31 -> "AES-CCM-16-128-256"
32 -> "AES-CCM-64-128-128"
33 -> "AES-CCM-64-128-256"
34 -> null
else -> "Reserved for Private Use"
}
 **/
