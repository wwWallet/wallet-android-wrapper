@file:Suppress("MissingPermission")

package io.yubicolabs.funke_explorer.bluetooth


import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import io.yubicolabs.funke_explorer.bluetooth.BleClientHandler.State.*
import io.yubicolabs.funke_explorer.bluetooth.ServiceCharacteristic.Companion.ClientToServer
import io.yubicolabs.funke_explorer.bluetooth.debug.PrintingBluetoothGattCallback
import io.yubicolabs.funke_explorer.bluetooth.debug.PrintingScanCallback
import io.yubicolabs.funke_explorer.tagForLog
import java.util.UUID

class BleClientHandler(
    private val activity: Activity,
) {
    sealed class State {
        data class Scanning(
            val serviceUuid: UUID,
            val scanner: BluetoothLeScanner,
            val successCallback: () -> Unit,
            val failureCallback: () -> Unit,
        ) : State()

        data class Connected(
            val server: BluetoothGatt,
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
        Log.d(tagForLog, "BluetoothLe is ${if (bluetoothLeAvailable) "" else "not"} available.")
    }

    val manager: BluetoothManager = activity.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? = manager.adapter

    var state: State = Disconnected

    val gattCallback = object : PrintingBluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            if (gatt != null) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(tagForLog, "Connected")
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        gatt.discoverServices()

                    } catch (e: SecurityException) {
                        Log.e(
                            tagForLog,
                            "Couldn't connect to gatt",
                            e
                        )

                        if (state is Scanning) {
                            (state as Scanning).failureCallback()
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    state.let {
                        when (it) {
                            is Connected -> {
                                it.server.close()
                            }

                            is Scanning -> {
                                it.scanner.stopScan(scanCallback)
                            }

                            Disconnected -> {
                                // Already disconnected
                            }
                        }
                    }

                    state = Disconnected
                    Log.d(tagForLog, "Disconnected")
                }
            }
        }

        override fun onReliableWriteCompleted(
            gatt: BluetoothGatt?,
            status: Int
        ) {
            super.onReliableWriteCompleted(gatt, status)
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            super.onServiceChanged(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            if (characteristic.uuid == ServiceCharacteristic.ServerToClient.uuid) {
                if (state is Connected) {
                    (state as Connected).readCallback?.invoke(value)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(
                gatt,
                characteristic,
                value,
                status
            )

            state.let {
                when (it) {
                    is Connected -> {
                        it.readCallback?.invoke(value)

                        state = it.copy(
                            readCallback = null
                        )
                    }

                    is Disconnected -> Log.e(tagForLog, "Cannot read in disconnected state.")
                    is Scanning -> Log.e(tagForLog, "Trying to read while scanning.")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            state.let {
                when (it) {
                    is Connected -> {
                        when (characteristic?.uuid) {
                            ServiceCharacteristic.ServerToClient.uuid -> {
                                it.writeCallback?.invoke()
                                state = it.copy(
                                    writeCallback = null
                                )
                            }

                            ServiceCharacteristic.ClientToServer.uuid -> {
                                it.writeCallback?.invoke()
                                state = it.copy(
                                    writeCallback = null
                                )
                            }

                            else -> Log.e(
                                tagForLog,
                                "Cannot write to UUID '${characteristic?.uuid}'."
                            )
                        }
                    }

                    is Disconnected -> Log.e(tagForLog, "Cannot write in disconnected state.")
                    is Scanning -> Log.e(tagForLog, "Trying to write while scanning.")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (descriptor?.uuid == ServiceCharacteristic.CharacteristicConfigurationDescriptorUUID) {
                val characteristic = descriptor.characteristic
                gatt?.setCharacteristicNotification(characteristic, true)
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt?,
            status: Int
        ) {
            super.onServicesDiscovered(gatt, status)

            if (status == GATT_SUCCESS && gatt != null) {
                state.let {
                    when (it) {
                        is Scanning -> {
                            val service = gatt.getService(it.serviceUuid)
                            if (service != null) {
                                val char = service
                                    .getCharacteristic(ServiceCharacteristic.ServerToClient.uuid)
                                if (char == null) {
                                    Log.e(
                                        tagForLog,
                                        "ServerToClient (${ServiceCharacteristic.ServerToClient.uuid}) not found."
                                    )
                                } else {
                                    gatt.setCharacteristicNotification(char, true)
                                }

                                state = Connected(
                                    gatt,
                                    service,
                                    gatt.device,
                                    null
                                )

                                val bleStateChar = service.getCharacteristic(
                                    ServiceCharacteristic.State.uuid
                                )
                                gatt.writeCharacteristic(
                                    bleStateChar,
                                    byteArrayOf(0x1),
                                    WRITE_TYPE_NO_RESPONSE
                                )

                                it.successCallback()
                            } else {
                                Log.e(
                                    tagForLog,
                                    "Service with ${it.serviceUuid} not found."
                                )
                                it.failureCallback()
                            }
                        }

                        else -> Log.e(tagForLog, "Discovered a service while not scanning.")
                    }
                }
            }
        }
    }

    val scanCallback: ScanCallback = object : PrintingScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult?
        ) {
            super.onScanResult(callbackType, result)
            state.let {
                if (it !is Scanning) {
                    Log.e(tagForLog, "Scanning stopped while not in scanning state.")
                } else {

                    it.scanner.stopScan(scanCallback)

                    result
                        ?.device
                        ?.connectGatt(
                            activity,
                            false,
                            gattCallback,
                            BluetoothDevice.TRANSPORT_LE
                        )
                }
            }
        }
    }

    fun status(): String {
        return state.let {
            when (it) {
                is Scanning -> "Scanning: ${it.scanner}."
                is Connected -> "Connected."
                is Disconnected -> "Disconnected."
            }
        }
    }

    fun createClient(
        serviceUuid: String,
        success: () -> Unit,
        failure: () -> Unit,
    ) {
        if (!checkBluetoothPermissions(activity, adapter)) {
            Log.e(tagForLog, "Not enough permissions, please add them and try again.")
            failure()
            return
        }

        if (adapter == null) {
            Log.e(tagForLog, "Bluetooth adapter not available")
            failure()
            return
        }

        val serviceUuid = UUID.fromString(serviceUuid)
        val settings = ScanSettings
            .Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanner = adapter.bluetoothLeScanner
        state = Scanning(
            serviceUuid,
            scanner,
            success,
            failure
        )

        val filterList = listOf(
            ScanFilter
                .Builder()
                .setServiceUuid(
                    ParcelUuid(
                        serviceUuid
                    )
                ).build()
        )

        try {
            scanner!!.startScan(
                filterList,
                settings,
                scanCallback
            )
        } catch (e: SecurityException) {
            Log.e(tagForLog, "Couldn't start scanning.", e)
            failure()
        }
    }

    fun disconnect() {
        state.let {
            when (it) {
                is Connected -> {
                    it.server.close()

                    state = Disconnected
                }

                else -> {
                    Log.e(tagForLog, "Cannot disconnect in state $it.")
                }
            }
        }
    }

    fun sendToServer(
        payload: ByteArray,
        success: () -> Unit,
        failure: () -> Unit,
    ) {
        state.let {
            when (it) {
                is Connected -> {
                    val characteristic = it.service.getCharacteristic(ClientToServer.uuid)

                    it.server.writeCharacteristic(
                        characteristic,
                        payload,
                        WRITE_TYPE_DEFAULT
                    )

                    state = it.copy(
                        writeCallback = success
                    )
                }

                else -> {
                    Log.e(
                        tagForLog,
                        "Cannot send in state ${it.javaClass.simpleName} to server."
                    )
                    failure()
                }
            }
        }
    }

    fun receiveFromServer(
        success: (ByteArray?) -> Unit,
        failure: () -> Unit,
    ) {
        state.let {
            when (it) {
                is Connected -> {
                    state = it.copy(readCallback = success)
                }

                else -> {
                    failure()
                }
            }
        }
    }
}
