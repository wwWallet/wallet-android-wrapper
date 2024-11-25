package io.yubicolabs.funke_explorer.credentials

import org.json.JSONObject

interface NavigatorCredentialsContainer {
    fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    )

    fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit
    )
}
