package io.yubicolabs.wwwwallet

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.storage.ProfileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _showUrlRow: MutableStateFlow<Boolean> =
        MutableStateFlow(BuildConfig.SHOW_URL_ROW)
    var showUrlRow: StateFlow<Boolean> = _showUrlRow.asStateFlow()

    fun updateUrl(url: String) {
        _url.update { url }
    }

    suspend fun browseToUrl(url: String) {
        _url.update { "" }

        _url.update {
            try {
                val uri = URI(url)
                when (uri.scheme) {
                    "https", "http" -> url

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

    fun showUrlRow(visible: Boolean) {
        _showUrlRow.update { visible }
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

    suspend fun setBaseUrl(value: String) {
        profileStorage.store(profileStorage.restore().copy(baseUrl = value))
    }
}
