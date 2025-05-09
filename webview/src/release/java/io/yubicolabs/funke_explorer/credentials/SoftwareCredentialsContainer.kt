package io.yubicolabs.funke_explorer.credentials

import org.json.JSONObject

/**
 * Stub, implemented in debug build config only.
 */
class SoftwareCredentialsContainer : NavigatorCredentialsContainer {
    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    ) {
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    ) {
    }
}
