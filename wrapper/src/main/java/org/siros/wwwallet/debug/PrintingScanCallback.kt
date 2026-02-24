package org.siros.wwwallet.debug

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import org.siros.wwwallet.logging.YOLOLogger
import org.siros.wwwallet.tagForLog

open class PrintingScanCallback : ScanCallback() {
    override fun onScanResult(
        callbackType: Int,
        result: ScanResult?,
    ) {
        YOLOLogger.d(tagForLog, "onScanResult: callbackType=$callbackType result=$result")

        super.onScanResult(callbackType, result)
    }

    override fun onBatchScanResults(results: List<ScanResult?>?) {
        YOLOLogger.d(tagForLog, "onBatchScanResults: results=$results")

        super.onBatchScanResults(results)
    }

    override fun onScanFailed(errorCode: Int) {
        YOLOLogger.d(tagForLog, "onScanFailed: errorCode=$errorCode")

        super.onScanFailed(errorCode)
    }
}
