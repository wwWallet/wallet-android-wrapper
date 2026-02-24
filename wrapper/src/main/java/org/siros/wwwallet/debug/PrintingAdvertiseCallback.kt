package org.siros.wwwallet.debug

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import org.siros.wwwallet.logging.YOLOLogger
import org.siros.wwwallet.tagForLog

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
