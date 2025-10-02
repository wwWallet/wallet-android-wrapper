package io.yubicolabs.wwwwallet.credentials

fun getClientOptions(
    type: String,
    challenge: String,
    origin: String,
) = "{\"type\":\"$type\",\"challenge\":\"$challenge\",\"origin\":\"https://$origin\"}"
    .toByteArray()
