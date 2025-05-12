package io.yubicolabs.wwwwallet

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.EmptyCoroutineContext

@SuppressLint("StaticFieldLeak")
class MainViewModel : ViewModel() {
    var activity: MainActivity? = null

    private val _url: MutableStateFlow<String> = MutableStateFlow(BuildConfig.BASE_URL)
    var url: StateFlow<String?> = _url.asStateFlow()

    private val _showUrlRow: MutableStateFlow<Boolean> = MutableStateFlow(BuildConfig.SHOW_URL_ROW)
    var showUrlRow: StateFlow<Boolean> = _showUrlRow.asStateFlow()

    fun setUrl(url: String) {
        _url.update { "" }

        _url.update {
            when {
                url.isBlank() or
                        url.startsWith("http://") or
                        url.startsWith("https://")
                    -> url

                url.startsWith("openid4vp://") -> {
                    url.replace("openid4vp://", BuildConfig.BASE_URL)
                }

                else -> "https://$url"
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
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            val uri: Uri = intent.data!!
            _url.update { uri.toString() }
        }
    }
}
