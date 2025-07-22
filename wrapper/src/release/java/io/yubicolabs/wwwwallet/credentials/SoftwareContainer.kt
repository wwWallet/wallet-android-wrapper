package io.yubicolabs.wwwwallet.credentials

import org.json.JSONObject
import java.lang.IllegalStateException

/**
 * Stub, implemented in debug build config only.
 */
class SoftwareContainer : Container {
    override fun create(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        failureCallback(IllegalStateException("Not Implemented."))
    }

    override fun get(
        options: JSONObject,
        successCallback: (JSONObject) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        failureCallback(IllegalStateException("Not Implemented."))
    }
}
