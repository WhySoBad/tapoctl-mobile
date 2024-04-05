package ch.wsb.tapoctl.service

import android.content.Context
import android.util.Log
import ch.wsb.tapoctl.GrpcConnection
import io.grpc.StatusException
import io.reactivex.processors.ReplayProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.jdk9.asFlow
import org.reactivestreams.FlowAdapters
import tapo.TapoOuterClass

class DeviceManager(private val connection: GrpcConnection, private val context: Context) {
    private val devices = HashMap<String, DeviceControl>()
    private val publisher = ReplayProcessor.create<List<DeviceControl>>()

    fun getDevices(): Flow<List<DeviceControl>> {
        return FlowAdapters.toFlowPublisher(publisher).asFlow()
    }

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

    suspend fun fetchDevices(): List<DeviceControl> {
        try {
            devices.clear()
            if(!connection.connected) connection.connect()
            val devicesRequest = TapoOuterClass.Empty.newBuilder().build()
            val response = connection.stub.devices(devicesRequest)
            for (dev in response.devicesList) {
                val ctrl = DeviceControl(dev, context, connection)
                devices[ctrl.id] = ctrl
            }
        } catch (e: StatusException) {
            Log.e("DeviceManager", e.toString())
        }

        publisher.onNext(devices.values.toList())
        return devices.values.toList()
    }
}