package io.yubicolabs.funke_explorer.bluetooth.debug

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_CONNECTING
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTING
import android.util.Log
import io.yubicolabs.funke_explorer.bluetooth.toHumanReadable
import io.yubicolabs.funke_explorer.tagForLog

open class PrintingBluetoothGattServerCallback() : BluetoothGattServerCallback() {
    override fun onConnectionStateChange(
        device: BluetoothDevice?,
        status: Int,
        newState: Int
    ) {
        Log.d(tagForLog, "onConnectionStateChange: $device, ${status.human}, ${newState.human}")

        super.onConnectionStateChange(device, status, newState)
    }

    override fun onServiceAdded(
        status: Int,
        service: BluetoothGattService?
    ) {
        Log.d(tagForLog, "onServiceAdded: $status, $service")

        super.onServiceAdded(status, service)
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic?
    ) {
        Log.d(
            tagForLog,
            "onCharacteristicReadRequest: $device, $requestId, $offset, $characteristic"
        )

        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice?,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic?,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) {
        Log.d(
            tagForLog,
            "onCharacteristicWriteRequest: $device, $requestId, $characteristic, $preparedWrite, $responseNeeded, $offset, ${value.toHumanReadable()}"
        )

        super.onCharacteristicWriteRequest(
            device,
            requestId,
            characteristic,
            preparedWrite,
            responseNeeded,
            offset,
            value
        )
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor?
    ) {
        Log.d(tagForLog, "onDescriptorReadRequest: $device, $requestId, $offset, $descriptor")

        super.onDescriptorReadRequest(device, requestId, offset, descriptor)
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice?,
        requestId: Int,
        descriptor: BluetoothGattDescriptor?,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) {
        Log.d(
            tagForLog,
            "onDescriptorWriteRequest: $device, $requestId, $descriptor, $preparedWrite, $responseNeeded, $offset, ${value.toHumanReadable()}"
        )

        super.onDescriptorWriteRequest(
            device,
            requestId,
            descriptor,
            preparedWrite,
            responseNeeded,
            offset,
            value
        )
    }

    override fun onExecuteWrite(
        device: BluetoothDevice?,
        requestId: Int,
        execute: Boolean
    ) {
        Log.d(tagForLog, "onExecuteWrite: $device, $requestId, $execute")

        super.onExecuteWrite(device, requestId, execute)
    }

    override fun onNotificationSent(
        device: BluetoothDevice?,
        status: Int
    ) {
        Log.d(tagForLog, "onNotificationSent: $device, $status")

        super.onNotificationSent(device, status)
    }

    override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
        Log.d(tagForLog, "onMtuChanged: $device, $mtu")

        super.onMtuChanged(device, mtu)
    }

    override fun onPhyUpdate(
        device: BluetoothDevice?,
        txPhy: Int,
        rxPhy: Int,
        status: Int
    ) {
        Log.d(tagForLog, "onPhyUpdate: $device, $txPhy, $rxPhy, $status")

        super.onPhyUpdate(device, txPhy, rxPhy, status)
    }

    override fun onPhyRead(
        device: BluetoothDevice?,
        txPhy: Int,
        rxPhy: Int,
        status: Int
    ) {
        Log.d(tagForLog, "onPhyRead $device, $txPhy, $rxPhy, $status")

        super.onPhyRead(device, txPhy, rxPhy, status)
    }
}

private val Int.human: String
    get() = when (this) {
        STATE_DISCONNECTED -> "STATE_DISCONNECTED"
        STATE_CONNECTING -> "STATE_CONNECTING"
        STATE_CONNECTED -> "STATE_CONNECTED"
        STATE_DISCONNECTING -> "STATE_DISCONNECTING"
        else -> "unknown"
    }
