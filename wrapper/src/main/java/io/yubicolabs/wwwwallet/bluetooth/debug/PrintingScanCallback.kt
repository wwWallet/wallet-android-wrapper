package io.yubicolabs.wwwwallet.bluetooth.debug

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import io.yubicolabs.wwwwallet.tagForLog

open class PrintingScanCallback : ScanCallback() {
    override fun onScanResult(
        callbackType: Int,
        result: ScanResult?,
    ) {
        Log.d(tagForLog, "onScanResult: callbackType=$callbackType result=$result")

        super.onScanResult(callbackType, result)
    }

    override fun onBatchScanResults(results: List<ScanResult?>?) {
        Log.d(tagForLog, "onBatchScanResults: results=$results")

        super.onBatchScanResults(results)
    }

    override fun onScanFailed(errorCode: Int) {
        Log.d(tagForLog, "onScanFailed: errorCode=$errorCode")

        super.onScanFailed(errorCode)
    }
}
