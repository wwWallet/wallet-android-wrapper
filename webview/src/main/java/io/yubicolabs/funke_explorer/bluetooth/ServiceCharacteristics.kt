package io.yubicolabs.funke_explorer.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID


sealed class ServiceCharacteristic(
    val name: String,
    val uuid: UUID,

    val broadcastProperty: Boolean = false,
    val readProperty: Boolean = false,
    val writeNoResponseProperty: Boolean = false,
    val writeProperty: Boolean = false,
    val notifyProperty: Boolean = false,
    val indicateProperty: Boolean = false,
    val signedWriteProperty: Boolean = false,

    val readPermission: Boolean = false,
    val readEncryptedPermission: Boolean = false,
    val readEncryptedMitmPermission: Boolean = false,
    val writePermission: Boolean = false,
    val writeEncryptedPermission: Boolean = false,
    val writeEncryptedMitmPermission: Boolean = false,
    val writeSignedPermission: Boolean = false,
    val writeSignedMitmPermission: Boolean = false,
) {
    companion object {
        // Change protocol phase on the fly
        var mode: Mode = Mode.MDocReader

        val CharacteristicConfigurationDescriptorUUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private fun all(): List<ServiceCharacteristic> =
            when (mode) {
                Mode.MDoc -> listOf(
                    MDoc.State,
                    MDoc.ServerToClient,
                    MDoc.ClientToServer
                )

                Mode.MDocReader -> listOf(
                    MDocReader.State,
                    MDocReader.ServerToClient,
                    MDocReader.ClientToServer
                )
            }

        val ClientToServer: ServiceCharacteristic
            get() = when (mode) {
                Mode.MDoc -> MDoc.ClientToServer
                Mode.MDocReader -> MDocReader.ClientToServer
            }

        val ServerToClient: ServiceCharacteristic
            get() = when (mode) {
                Mode.MDoc -> MDoc.ServerToClient
                Mode.MDocReader -> MDocReader.ServerToClient
            }

        val State: ServiceCharacteristic
            get() = when (mode) {
                Mode.MDoc -> MDoc.State
                Mode.MDocReader -> MDocReader.State
            }

        fun toBleCharacteristics(): List<BluetoothGattCharacteristic> = all().map { characteristic ->
            val bleCharacteristic = BluetoothGattCharacteristic(
                characteristic.uuid,
                characteristic.bleProperties,
                characteristic.blePermissions,
            )
            if (characteristic.isASpecialSnowflake) {
                val descriptor = BluetoothGattDescriptor(
                    CharacteristicConfigurationDescriptorUUID,
                    BluetoothGattDescriptor.PERMISSION_WRITE
                )
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                bleCharacteristic.addDescriptor(descriptor)
            }

            bleCharacteristic
        }
    }

    private data object MDoc {
        object State : ServiceCharacteristic(
            uuid = UUID.fromString("00000001-A123-48CE-896B-4C76973373E6"),
            name = "State",
            notifyProperty = true,
            writeNoResponseProperty = true,
            writePermission = true,
        )

        object ClientToServer : ServiceCharacteristic(
            uuid = UUID.fromString("00000002-A123-48CE-896B-4C76973373E6"),
            name = "Client To Server",
            writeNoResponseProperty = true,
            notifyProperty = true,
            writePermission = true,
        )

        object ServerToClient : ServiceCharacteristic(
            uuid = UUID.fromString("00000003-A123-48CE-896B-4C76973373E6"),
            name = "Server To Client",
            notifyProperty = true,
            writePermission = true,
        )
    }

    private data object MDocReader {
        object State : ServiceCharacteristic(
            uuid = UUID.fromString("00000005-A123-48CE-896B-4C76973373E6"),
            name = "State",
            notifyProperty = true,
            writeNoResponseProperty = true,
            writePermission = true,
        )

        object ClientToServer : ServiceCharacteristic(
            uuid = UUID.fromString("00000006-A123-48CE-896B-4C76973373E6"),
            name = "Client To Server",
            writeNoResponseProperty = true,
            notifyProperty = true,
            writePermission = true,
        )

        object ServerToClient : ServiceCharacteristic(
            uuid = UUID.fromString("00007-A123-48CE-896B-4C76973373E6"),
            name = "Server To Client",
            notifyProperty = true,
            writePermission = true,
        )

        object Ident : ServiceCharacteristic(
            uuid = UUID.fromString("00008-A123-48CE-896B-4C76973373E6"),
            name = "Ident",
            readProperty = true,
        )
    }

    enum class Mode {
        MDocReader,
        MDoc
    }
}

val ServiceCharacteristic.bleProperties: Int
    get() = (if (broadcastProperty) BluetoothGattCharacteristic.PROPERTY_BROADCAST else 0) or
            (if (readProperty) BluetoothGattCharacteristic.PROPERTY_READ else 0) or
            (if (writeNoResponseProperty) BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE else 0) or
            (if (writeProperty) BluetoothGattCharacteristic.PROPERTY_WRITE else 0) or
            (if (notifyProperty) BluetoothGattCharacteristic.PROPERTY_NOTIFY else 0) or
            (if (indicateProperty) BluetoothGattCharacteristic.PROPERTY_INDICATE else 0) or
            (if (signedWriteProperty) BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE else 0)

val ServiceCharacteristic.blePermissions: Int
    get() = (if (readPermission) BluetoothGattCharacteristic.PERMISSION_READ else 0) or
            (if (readEncryptedPermission) BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED else 0) or
            (if (readEncryptedMitmPermission) BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM else 0) or
            (if (writePermission) BluetoothGattCharacteristic.PERMISSION_WRITE else 0) or
            (if (writeEncryptedPermission) BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED else 0) or
            (if (writeEncryptedMitmPermission) BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM else 0) or
            (if (writeSignedPermission) BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED else 0) or
            (if (writeSignedMitmPermission) BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM else 0)

val ServiceCharacteristic.isASpecialSnowflake: Boolean
    get() = this.notifyProperty