package io.yubicolabs.funke_explorer.json

import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import android.util.Log
import io.yubicolabs.funke_explorer.tagForLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


fun ByteArray.b64(): String = encodeToString(this, NO_WRAP or NO_PADDING or URL_SAFE)

inline fun <reified T> JSONObject.getOrNull(name: String): T? = try {
    val value = get(name)
    if (value is T) {
        value
    } else {
        Log.w(
            tagForLog,
            "Name $name of $this is not of type ${T::class.java.simpleName} but of ${value.javaClass.simpleName}."
        )
        null
    }
} catch (e: JSONException) {
    Log.e(tagForLog, "Name $name not found in $this.", e)
    null
}


fun JSONObject.toMap(): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()

    for (key in keys()) {
        when (val value = get(key)) {
            is JSONObject -> {
                val names = value.names()
                if (value.names() == null || names?.length() == 0) {
                    result[key] = ""
                } else {

                    val bytesOrObject: Map<String, Any?> = value.toMap()
                    val keysAreIndexes =
                        bytesOrObject.keys.mapNotNull { it.toIntOrNull() }.isNotEmpty()
                    if (keysAreIndexes) {
                        // make string
                        val string = ByteArray(
                            bytesOrObject.size
                        ) { index ->
                            val maybeByte = bytesOrObject["$index"]
                            if (maybeByte is Int) {
                                maybeByte.toByte()
                            } else {
                                Log.w(
                                    tagForLog,
                                    "Index $index of name $key is not a byte but $maybeByte instead."
                                )
                                0
                            }
                        }.b64()

                        result[key] = string
                    } else {
                        // unexciting map found
                        result[key] = bytesOrObject
                    }
                }
            }

            is JSONArray -> result[key] = value.toList()

            JSONObject.NULL -> result[key] = null

            else -> result[key] = value
        }
    }

    return result
}

fun JSONArray.toList(): List<Any?> = MutableList(length()) { index ->
    when (val value = get(index)) {
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        else -> value
    }
}

fun JSONObject.getNested(path: String): Any? = if ("." in path) {
    val all = path.split(".")
    val first = all.first()

    if (has(first)) {
        val rest = all.drop(1).joinToString(separator = ".")
        getJSONObject(first).getNested(rest)
    } else {
        null
    }
} else {
    try {
        get(path)
    } catch (ex: JSONException) {
        null
    }
}

fun JSONObject.setNested(path: String, value: Any?) {
    if ("." in path) {
        val all = path.split(".")
        val first = all.first()

        if (has(first)) {
            val rest = all.drop(1).joinToString(separator = ".")
            getJSONObject(first).setNested(rest, value)
        } else {
            // ignore non present path
        }
    } else {
        put(path, value)
    }
}