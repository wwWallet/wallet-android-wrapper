package io.yubicolabs.funke_explorer.bluetooth.debug

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.util.Log
import io.yubicolabs.funke_explorer.tagForLog

open class PrintingAdvertiseCallback : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
        Log.d(tagForLog, "onStartSuccess: settingsInEffect=$settingsInEffect")

        super.onStartSuccess(settingsInEffect)
    }

    override fun onStartFailure(errorCode: Int) {
        Log.d(tagForLog, "onStartFailure: errorCode=$errorCode")

        super.onStartFailure(errorCode)
    }
}