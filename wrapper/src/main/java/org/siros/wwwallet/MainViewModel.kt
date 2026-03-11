package org.siros.wwwallet

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.siros.wwwallet.logging.YOLOLogger
import org.siros.wwwallet.storage.ProfileStorage
import java.net.URI
import java.net.URISyntaxException

@SuppressLint("StaticFieldLeak")
class MainViewModel : ViewModel() {
    lateinit var profileStorage: ProfileStorage

    var activity: MainActivity? = null
        set(value) {
            if (value != null) {
                profileStorage = ProfileStorage(value)

                viewModelScope.launch {
                    val baseurl = profileStorage.restore().baseUrl

                    _url.update {
                        baseurl
                    }
                }
            }

            field = value
        }

    private val _url: MutableStateFlow<String> = MutableStateFlow("")
    var url: StateFlow<String> = _url.asStateFlow()

    sealed class UpdateReason {
        object UserRequest : UpdateReason()

        object DeeplinkRequest : UpdateReason()

        data class WebpageError(
            val errorMessage: String,
        ) : UpdateReason()
    }

    private val _updateBaseUrl: MutableStateFlow<UpdateReason?> = MutableStateFlow(null)
    var updateBaseUrl: StateFlow<UpdateReason?> = _updateBaseUrl.asStateFlow()

    suspend fun browseToUrl(url: String) {
        _url.update { "" }

        _url.update {
            try {
                val uri = URI(url)
                when (uri.scheme) {
                    "https", "http" -> url

                    "wwwallet" -> {
                        when (uri.host) {
                            "change-provider" -> changeProviderRequested(uri) ?: it
                            else -> url
                        }
                    }

                    "openid4vp", "haip" ->
                        URI(
                            "https",
                            URI(getBaseUrl()).host,
                            "/cb",
                            uri.query,
                            uri.fragment,
                        ).toASCIIString()

                    else -> url
                }
            } catch (uriException: URISyntaxException) {
                YOLOLogger.e(tagForLog, "URL ERROR, routing back to base url.", uriException)
                getBaseUrl()
            }
        }
    }

    fun onBackPressed() {
        _url.update { "webview://back" }
    }

    fun parseIntent(intent: Intent) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri: Uri = intent.data!!
            browseToUrl(uri.toString())
        }
    }

    fun copyToClipboard(text: String) {
        if (activity == null) {
            YOLOLogger.e(tagForLog, "NULL activity, closing.")
            return
        } else {
            val manager =
                activity!!.applicationContext.getSystemService(ClipboardManager::class.java)

            val clip = ClipData.newPlainText("wwWallet log", text)
            manager.setPrimaryClip(clip)
        }
    }

    suspend fun getBaseUrl(): String = profileStorage.restore().baseUrl

    suspend fun setBaseUrl(value: String): String {
        updateBaseUrlCanceled()

        val sanitized =
            when {
                value.startsWith("https://") -> value
                value.startsWith("http://") -> value.replace("http", "https")
                value.isNotEmpty() && value.first().isLetter() -> "https://$value" // forgot the https?
                else -> value // for direct ip addresses
            }

        profileStorage.store(profileStorage.restore().copy(baseUrl = sanitized))

        return sanitized
    }

    fun openedFromShortcut(shortcut: String?) {
        when (shortcut) {
            "shortcut_open_funke",
            "shortcut_open_demo",
            "shortcut_open_qa",
            -> {
                val endpoint = shortcut.split("_").last()
                viewModelScope.launch {
                    val url = setBaseUrl("https://$endpoint.wwwallet.org")
                    browseToUrl(url)
                }
            }

            "shortcut_open_custom" -> {
                updateBaseUrl()
            }

            else -> YOLOLogger.e(tagForLog, "'$shortcut ' is not a valid shortcut identifier!")
        }
    }

    fun updateBaseUrlCanceled() {
        _updateBaseUrl.update { null }
    }

    fun updateBaseUrl(reason: UpdateReason = UpdateReason.UserRequest) {
        _updateBaseUrl.update { reason }
    }

    fun errorReceived(description: String) {
        updateBaseUrl(
            UpdateReason.WebpageError(description),
        )
    }

    private suspend fun changeProviderRequested(uri: URI): String? {
        if (uri.query == null) {
            updateBaseUrl(reason = UpdateReason.DeeplinkRequest)
            return null
        }

        val queryParameters =
            uri.query.split("&").associate {
                val (k, v) = it.split("=")
                k to v
            }

        if ("provider" in queryParameters) {
            return setBaseUrl(queryParameters.getOrDefault("provider", getBaseUrl()))
        } else {
            updateBaseUrl(reason = UpdateReason.DeeplinkRequest)
            return null
        }
    }
}
