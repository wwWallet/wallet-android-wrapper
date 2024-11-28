package io.yubicolabs.funke_explorer

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

    private val _url: MutableStateFlow<String> = MutableStateFlow("https://funke.wwwallet.org")
    var url: StateFlow<String?> = _url.asStateFlow()

    private val _qrcode: MutableStateFlow<String?> = MutableStateFlow(null)
    var qrcode: StateFlow<String?> = _qrcode.asStateFlow()

    private val _navigation: MutableStateFlow<String> = MutableStateFlow("")
    var navigation: StateFlow<String?> = _navigation.asStateFlow()

    private val _showUrlRow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var showUrlRow: StateFlow<Boolean> = _showUrlRow.asStateFlow()

    fun reinjectAndroidBridge() {
        _navigation.update {
            "navigation"
        }
    }

    fun setUrl(url: String) {
        _url.update { "" }

        _url.update {
            if (
                url.startsWith("http://") or
                url.startsWith("https://")
            ) {
                url
            } else {
                "https://$url"
            }
        }
    }

    fun onBackPressed() {
        _navigation.update { "" }
        _navigation.update { "back" }
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