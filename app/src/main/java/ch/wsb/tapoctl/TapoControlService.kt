package ch.wsb.tapoctl

import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.util.Log
import ch.wsb.tapoctl.tapoctl.*
import ch.wsb.tapoctl.ui.common.*
import io.grpc.StatusException
import io.reactivex.processors.ReplayProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.reactivestreams.FlowAdapters
import tapo.TapoOuterClass
import tapo.TapoOuterClass.HueSaturation
import tapo.TapoOuterClass.IntegerValueChange
import java.util.concurrent.Flow
import java.util.function.Consumer

class TapoControlService : ControlsProviderService() {
    private lateinit var settings: Settings
    private lateinit var connection: GrpcConnection
    private lateinit var devices: DeviceManager
    private lateinit var publisher: ReplayProcessor<Control>
    private lateinit var scope: CoroutineScope
    private var eventHandler: EventHandler? = null


    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        ensureServiceRunning(events = false)

        val publisher = flow {
            for ((_, ctrl) in devices.iterator()) {
                if (ctrl.canControlBrightness()) emit(ctrl.getStatelessControl(DeviceControl.POWER_ID, "Power, Brightness"))
                else emit(ctrl.getStatelessControl(DeviceControl.POWER_ID, "Power"))
                if (ctrl.canControlTemperature()) emit(ctrl.getStatelessControl(DeviceControl.TEMPERATURE_ID, "Temperature"))
                if (ctrl.canControlColor()) emit(ctrl.getStatelessControl(DeviceControl.HUE_ID, "Hue"))
            }
        }

        return publisher.asPublisher()
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        ensureServiceRunning(events = true)

        scope.launch {
            for (compositeId in controlIds) {
                val deviceId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[0]
                val controlId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[1]

                val ctrl = if (devices.exists(deviceId)) devices.get(deviceId)!! else {
                    publisher.onNext(DeviceControl.getUnavailableControl(deviceId, controlId, baseContext, Control.STATUS_ERROR))
                    continue
                }

                try {
                    val request = TapoOuterClass.DeviceRequest.newBuilder().setDevice(ctrl.name).build()
                    val response = connection.stub.info(request)

                    val builder = when (controlId) {
                        DeviceControl.POWER_ID -> {
                            if (ctrl.canControlBrightness()) ctrl.getPowerBrightnessControl(response.deviceOn, response.brightness)
                            else ctrl.getPowerControl(response.deviceOn)
                        }
                        DeviceControl.HUE_ID -> ctrl.getHueControl(response.hue)
                        DeviceControl.TEMPERATURE_ID -> {
                            if (response.hasTemperature()) ctrl.getTemperatureControl(response.temperature)
                            else ctrl.getTemperatureControl(2500).setStatus(Control.STATUS_DISABLED)
                        }
                        else -> null
                    }

                    if (builder != null) publisher.onNext(builder.build())
                    else Log.w("TapoControlService", "Control $controlId for $deviceId did not match any known control id")
                } catch (e: StatusException) {
                    Log.e("TapoControlService", e.toString())
                    publisher.onNext(DeviceControl.getUnavailableControl(deviceId, controlId, baseContext, Control.STATUS_ERROR))
                }
            }
        }

        return FlowAdapters.toFlowPublisher(publisher)
    }

    override fun performControlAction(compositeId: String, action: ControlAction, consumer: Consumer<Int>) {
        val deviceId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[0]
        val controlId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[1]

        val ctrl = devices.get(deviceId)!!

        val builder = TapoOuterClass.SetRequest.newBuilder().setDevice(ctrl.name)

        if (action is BooleanAction) {
            when (controlId) {
                DeviceControl.POWER_ID -> builder.setPower(action.newState)
            }
        } else if (action is FloatAction) {
            val change = IntegerValueChange.newBuilder().setAbsolute(true).setValue(action.newValue.toInt())
            when (controlId) {
                DeviceControl.HUE_ID -> builder.setHueSaturation(HueSaturation.newBuilder().setHue(change).setSaturation(IntegerValueChange.newBuilder().setAbsolute(true).setValue(50)))
                DeviceControl.BRIGHTNESS_ID, DeviceControl.POWER_ID -> builder.setBrightness(change)
                DeviceControl.TEMPERATURE_ID -> builder.setTemperature(change)
            }
        }

        scope.launch {
            try {
                val request = builder.build()
                val info = connection.stub.set(request)

                val control = when (controlId) {
                    DeviceControl.POWER_ID -> {
                        if (ctrl.canControlBrightness()) ctrl.getPowerBrightnessControl(info.deviceOn, info.brightness)
                        else ctrl.getPowerControl(info.deviceOn)
                    }
                    DeviceControl.HUE_ID -> ctrl.getHueControl(info.hue)
                    DeviceControl.TEMPERATURE_ID -> ctrl.getTemperatureControl(info.temperature)
                    else -> null
                }

                consumer.accept(ControlAction.RESPONSE_OK)
                if (control != null) publisher.onNext(control.build())
                else Log.w("TapoControlService", "Control $controlId for $deviceId did not match any known control id")
            } catch (e: StatusException) {
                consumer.accept(ControlAction.RESPONSE_FAIL)
                Log.e("TapoControlService", e.toString())
            }
        }
    }

    override fun onCreate() {
        Log.i("TapoControlService", "Created")
        scope = MainScope()
        settings = Settings(baseContext.Datastore)
        connection = GrpcConnection(settings)
        devices = DeviceManager(connection, baseContext)
        publisher = ReplayProcessor.create()
        eventHandler = EventHandler(connection, scope)
        scope.launch {
            eventHandler?.getEvents()?.onEach { handleEvent(it) }?.collect()
        }
        ensureServiceRunning(events = true)
    }

    override fun onDestroy() {
        Log.i("TapoControlService", "Destroyed")
        eventHandler?.unsubscribe()
        connection.close()
    }

    private fun ensureServiceRunning(events: Boolean) {
        try {
            connection.reconnect()
            if (events) eventHandler?.resubscribe()
            runBlocking { devices.fetchDevices() }
        } catch (e: GrpcNotConnectedException) {
            Log.e("TapoControlService", "Cannot ensure all services running since grpc is not connected")
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is Event.DeviceAuthChanged -> {
                val device = event.device
                val ctrl = devices.getDeviceByControl(device.name)

                if (ctrl == null) {
                    Log.w("Event", "Device ${device.name} not found")
                    return
                }

                if (device.status === TapoOuterClass.SessionStatus.Authenticated) {
                    // TODO: Update with current device info
                } else {
                    if (ctrl.canControlTemperature()) publisher.onNext(DeviceControl.getUnavailableControl(device.name, DeviceControl.TEMPERATURE_ID, baseContext, Control.STATUS_ERROR))
                    else if (ctrl.canControlColor()) publisher.onNext(DeviceControl.getUnavailableControl(device.name, DeviceControl.HUE_ID, baseContext, Control.STATUS_ERROR))
                    else publisher.onNext(DeviceControl.getUnavailableControl(device.name, DeviceControl.POWER_ID, baseContext, Control.STATUS_ERROR))
                }
            }
            is Event.DeviceStateChanged -> {
                val info = event.info
                val ctrl = devices.getDeviceByControl(info.name)

                if (ctrl != null) {
                    if (ctrl.canControlBrightness()) publisher.onNext(ctrl.getPowerBrightnessControl(info.device_on, info.brightness).build())
                    else publisher.onNext(ctrl.getPowerControl(info.device_on).build())
                    if (ctrl.canControlTemperature()) publisher.onNext(ctrl.getTemperatureControl(info.temperature).build())
                    if (ctrl.canControlColor()) publisher.onNext(ctrl.getHueControl(info.hue).build())
                } else Log.w("Event", "Device ${info.name} not found")
            }
        }
    }
}