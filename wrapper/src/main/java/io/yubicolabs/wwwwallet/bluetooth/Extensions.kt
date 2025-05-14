package io.yubicolabs.wwwwallet.bluetooth

import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.util.Log

fun ByteArray?.toHumanReadable(): String =
    if (this == null) {
        byteArrayOf()
    } else {
        this
    }.joinToString(separator = " ") { b ->
        "0x%02x".format(b)
    }

fun checkBluetoothPermissions(
    activity: Activity,
    adapter: BluetoothAdapter?,
): Boolean {
    if (adapter == null) {
        Log.e("BLEPERM", "No bluetooth device.")
        return false
    }

    if (activity.checkSelfPermission(BLUETOOTH_CONNECT) == PERMISSION_DENIED) {
        activity.requestPermissions(arrayOf(BLUETOOTH_CONNECT), 100)
        return false
    }

    if (activity.checkSelfPermission(BLUETOOTH_SCAN) == PERMISSION_DENIED) {
        activity.requestPermissions(arrayOf(BLUETOOTH_SCAN), 1001)
        return false
    }

    if (activity.checkSelfPermission(BLUETOOTH_ADVERTISE) == PERMISSION_DENIED) {
        activity.requestPermissions(arrayOf(BLUETOOTH_ADVERTISE), 1002)
        return false
    }

    if (!adapter.isEnabled) {
        activity.startActivityForResult(Intent(ACTION_REQUEST_ENABLE), 4711)
        return false
    }

    return true
}
