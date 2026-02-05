@file:Suppress("MissingPermission")

package io.yubicolabs.wwwwallet.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt.GATT_FAILURE
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.os.ParcelUuid
import io.yubicolabs.wwwwallet.bluetooth.BleServerHandler.State.Advertising
import io.yubicolabs.wwwwallet.bluetooth.BleServerHandler.State.Connected
import io.yubicolabs.wwwwallet.bluetooth.BleServerHandler.State.Disconnected
import io.yubicolabs.wwwwallet.bluetooth.ServiceCharacteristic.Companion.ServerToClient
import io.yubicolabs.wwwwallet.bluetooth.debug.PrintingAdvertiseCallback
import io.yubicolabs.wwwwallet.bluetooth.debug.PrintingBluetoothGattServerCallback
import io.yubicolabs.wwwwallet.logging.YOLOLogger
import io.yubicolabs.wwwwallet.tagForLog
import java.util.UUID

class BleServerHandler(
    private val activity: Activity,
) {
    sealed class State {
        data class Advertising(
            val server: BluetoothGattServer,
            val service: BluetoothGattService,
            val advertiser: BluetoothLeAdvertiser,
        ) : State()

        data class Connected(
            val server: BluetoothGattServer,
            val service: BluetoothGattService,
            val device: BluetoothDevice,
            val readCallback: ((ByteArray?) -> Unit)? = null,
            val writeCallback: (() -> Unit)? = null,
        ) : State()

        object Disconnected : State()
    }

    init {
        val bluetoothLeAvailable =
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        YOLOLogger.d(tagForLog, "BluetoothLe is ${if (bluetoothLeAvailable) "" else "not "}available.")
    }

    val manager: BluetoothManager = activity.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? = manager.adapter

    var state: State = Disconnected

    fun createServer(
        serviceUuid: String,
        success: () -> Unit,
        failure: () -> Unit,
    ) {
        if (!checkBluetoothPermissions(activity, adapter)) {
            YOLOLogger.e(tagForLog, "Not enough permissions, please add them and try again.")
            failure()
        } else {
            listen(
                serviceUuid,
                success,
                failure,
            )
        }
    }

    private fun listen(
        rawServiceUuid: String,
        success: () -> Unit,
        failure: () -> Unit,
    ) {
        val serviceUuid = UUID.fromString(rawServiceUuid)

        var gattServer: BluetoothGattServer? = null
        gattServer =
            manager.openGattServer(
                activity,
                callback,
            )

        if (gattServer == null) {
            YOLOLogger.e(tagForLog, "Could not create gatt server.")
            failure()
            return
        }

        val service =
            BluetoothGattService(
                serviceUuid,
                SERVICE_TYPE_PRIMARY,
            )

        ServiceCharacteristic.toBleCharacteristics().map { characteristic ->
            service.addCharacteristic(characteristic)
        }

        try {
            gattServer.addService(service)
        } catch (e: SecurityException) {
            YOLOLogger.e(tagForLog, "Couldn't add service.", e)
        }

        val advertiser = adapter!!.bluetoothLeAdvertiser
        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

        val data =
            AdvertiseData
                .Builder()
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(serviceUuid))
                .build()

        YOLOLogger.d(
            tagForLog,
            "Started advertising UUID $serviceUuid as advertiser $advertiser on gattserver $gattServer.",
        )

        try {
            advertiser.startAdvertising(
                settings,
                data,
                printingAdvertiserCallback,
            )

            state = Advertising(gattServer, service, advertiser)
        } catch (e: SecurityException) {
            YOLOLogger.e(tagForLog, "Error while advertising", e)
            failure()
        }

        success()
    }

    fun status(): String =
        state.let {
            when (it) {
                is Advertising -> "Advertising: ${it.server}"
                is Connected -> "Connected."
                is Disconnected -> "Disconnected."
            }
        }

    fun disconnect() {
        state.let {
            when (it) {
                is Connected -> {
                    it.server.close()

                    state = Disconnected
                }

                is Advertising -> {
                    it.advertiser.stopAdvertising(printingAdvertiserCallback)
                    it.server.close()

                    state = Disconnected
                }

                else -> {
                }
            }
        }
    }

    fun sendToClient(
        payload: ByteArray,
        success: () -> Unit,
        failure: () -> Unit,
    ) {
        state.let {
            when (it) {
                is Connected -> {
                    val chara = it.service.getCharacteristic(ServerToClient.uuid)
                    val notified =
                        it.server.notifyCharacteristicChanged(it.device, chara, true, payload)

                    if (notified == BluetoothStatusCodes.SUCCESS) {
                        success()
                    } else {
                        failure()
                    }
                }

                else -> {
                    YOLOLogger.e(tagForLog, "Cannot send in state ${it.javaClass.simpleName} to client.")
                    failure()
                }
            }
        }
    }

    fun receiveFromClient(
        success: (ByteArray?) -> Unit,
        failure: () -> Unit,
    ) {
        state.let {
            when (it) {
                is Connected -> state = it.copy(readCallback = success)

                else -> failure()
            }
        }
    }

    private val callback =
        object : PrintingBluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice?,
                status: Int,
                newState: Int,
            ) {
                super.onConnectionStateChange(device, status, newState)

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        state.let {
                            if (device != null && it is Advertising) {
                                it.advertiser.stopAdvertising(printingAdvertiserCallback)

                                state =
                                    Connected(
                                        it.server,
                                        it.service,
                                        device,
                                        null,
                                    )
                            }
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        state.let {
                            if (it is Advertising) {
                                it.advertiser.stopAdvertising(
                                    PrintingAdvertiseCallback(),
                                )
                            }

                            state = Disconnected
                        }
                    }
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                super.onDescriptorWriteRequest(
                    device,
                    requestId,
                    descriptor,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value,
                )
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value,
                )

                state.let {
                    if (it is Connected) {
                        val writeState =
                            if (value != null) {
                                when (characteristic?.uuid) {
                                    ServiceCharacteristic.ClientToServer.uuid -> {
                                        // registered characteristic found, report back
                                        val msg =
                                            "Received $value (${value.toHumanReadable()}, ${
                                                String(
                                                    value,
                                                )
                                            }) from ${characteristic.uuid}"
                                        YOLOLogger.d(tagForLog, msg)

                                        it.server.notifyCharacteristicChanged(
                                            device!!,
                                            characteristic,
                                            false,
                                            value,
                                        )

                                        // check if server wanted to see what client wrote.
                                        if (it.readCallback != null) {
                                            it.readCallback(value)
                                            state = it.copy(readCallback = null)
                                        }

                                        GATT_SUCCESS
                                    }

                                    ServerToClient.uuid -> {
                                        GATT_SUCCESS
                                    }

                                    else -> {
                                        YOLOLogger.e(
                                            tagForLog,
                                            "Unexpected characteristic ($characteristic) write received.",
                                        )
                                        GATT_FAILURE
                                    }
                                }
                            } else {
                                YOLOLogger.e(tagForLog, "Received empty write request payload.")
                                GATT_FAILURE
                            }

                        if (responseNeeded) {
                            it.server.sendResponse(
                                device,
                                requestId,
                                writeState,
                                offset,
                                value,
                            )
                        }
                    } else {
                        YOLOLogger.e(tagForLog, "Cannot write in $state.")
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?,
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

                state.let {
                    if (it is Connected) {
                        if (characteristic?.uuid == ServerToClient) {
                            if (it.readCallback != null) {
                                it.readCallback(characteristic.value)
                            }
                        }
                    }
                }
            }
        }

    private val printingAdvertiserCallback = PrintingAdvertiseCallback()
}
