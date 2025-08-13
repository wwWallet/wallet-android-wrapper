@file:Suppress("Deprecation")

package io.yubicolabs.wwwwallet.bluetooth.debug

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog

open class PrintingBluetoothGattCallback : BluetoothGattCallback() {
    override fun onPhyUpdate(
        gatt: BluetoothGatt?,
        txPhy: Int,
        rxPhy: Int,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onPhyUpdate: $gatt, $txPhy, $rxPhy, $status")
        super.onPhyUpdate(gatt, txPhy, rxPhy, status)
    }

    override fun onPhyRead(
        gatt: BluetoothGatt?,
        txPhy: Int,
        rxPhy: Int,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onPhyRead: $gatt, $txPhy, $rxPhy, $status")
        super.onPhyRead(gatt, txPhy, rxPhy, status)
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int,
    ) {
        YOLOLogger.d(tagForLog, "onConnectionStateChange: $gatt, $status, $newState")
        super.onConnectionStateChange(gatt, status, newState)
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt?,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onServicesDiscovered: $gatt, $status")
        super.onServicesDiscovered(gatt, status)
    }

    @Deprecated("")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onCharacteristicRead: $gatt, $characteristic, $status")
        super.onCharacteristicRead(gatt, characteristic, status)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onCharacteristicRead: $gatt, $characteristic, $value, $status")
        super.onCharacteristicRead(gatt, characteristic, value, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int,
    ) {
        YOLOLogger.d(
            tagForLog,
            "onCharacteristicWrite: $gatt, $characteristic(${characteristic?.value}), $status",
        )
        super.onCharacteristicWrite(gatt, characteristic, status)
    }

    @Deprecated("")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        YOLOLogger.d(tagForLog, "onCharacteristicChanged: $gatt, $characteristic")
        super.onCharacteristicChanged(gatt, characteristic)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        YOLOLogger.d(tagForLog, "onCharacteristicChanged: $gatt, $characteristic, $value")
        super.onCharacteristicChanged(gatt, characteristic, value)
    }

    @Deprecated("")
    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onDescriptorRead: $gatt, $descriptor, $status")
        super.onDescriptorRead(gatt, descriptor, status)
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray,
    ) {
        YOLOLogger.d(tagForLog, "onDescriptorRead: $gatt, $descriptor, $status, $value")
        super.onDescriptorRead(gatt, descriptor, status, value)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onDescriptorWrite: $gatt, $descriptor, $status")
        super.onDescriptorWrite(gatt, descriptor, status)
    }

    override fun onReliableWriteCompleted(
        gatt: BluetoothGatt?,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onReliableWriteCompleted: $gatt, $status")
        super.onReliableWriteCompleted(gatt, status)
    }

    override fun onReadRemoteRssi(
        gatt: BluetoothGatt?,
        rssi: Int,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onReadRemoteRssi: $gatt, $rssi, $status")
        super.onReadRemoteRssi(gatt, rssi, status)
    }

    override fun onMtuChanged(
        gatt: BluetoothGatt?,
        mtu: Int,
        status: Int,
    ) {
        YOLOLogger.d(tagForLog, "onMtuChanged: $gatt, $mtu, $status")
        super.onMtuChanged(gatt, mtu, status)
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        YOLOLogger.d(tagForLog, "onServiceChanged: $gatt")
        super.onServiceChanged(gatt)
    }
}
