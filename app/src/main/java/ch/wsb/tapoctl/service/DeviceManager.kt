package ch.wsb.tapoctl.service

import android.content.Context
import android.util.Log
import io.grpc.StatusException
import tapo.TapoGrpcKt
import tapo.TapoOuterClass

class DeviceManager(private val stub: TapoGrpcKt.TapoCoroutineStub, private val context: Context) {
    private val devices = HashMap<String, DeviceControl>()

    fun get(deviceId: String): DeviceControl? {
        return devices[deviceId]
    }

    fun getDeviceByControl(controlId: String): DeviceControl? {
        return devices.values.find { ctrl -> ctrl.name == controlId }
    }

    fun iterator(): Iterable<Map.Entry<String, DeviceControl>> {
        return devices.asIterable()
    }

    fun exists(deviceId: String): Boolean {
        return devices.keys.contains(deviceId)
    }

    suspend fun fetchDevices() {
        try {
            val devicesRequest = TapoOuterClass.Empty.newBuilder().build()
            val response = stub.devices(devicesRequest)
            devices.clear()
            for (dev in response.devicesList) {
                val ctrl = DeviceControl(dev, context)
                devices[ctrl.id] = ctrl
            }
        } catch (e: StatusException) {
            Log.e("DeviceManager", e.toString())
        }
    }
}