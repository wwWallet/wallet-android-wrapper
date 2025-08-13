package io.yubicolabs.wwwwallet.bluetooth.debug

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog

open class PrintingAdvertiseCallback : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
        YOLOLogger.d(tagForLog, "onStartSuccess: settingsInEffect=$settingsInEffect")

        super.onStartSuccess(settingsInEffect)
    }

    override fun onStartFailure(errorCode: Int) {
        YOLOLogger.d(tagForLog, "onStartFailure: errorCode=$errorCode")

        super.onStartFailure(errorCode)
    }
}
