package ch.wsb.tapoctl

import android.app.PendingIntent
import android.content.Intent
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import io.reactivex.processors.ReplayProcessor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.coroutines.runBlocking
import org.reactivestreams.FlowAdapters
import tapo.TapoGrpcKt
import tapo.TapoOuterClass
import tapo.TapoOuterClass.EventResponse
import tapo.TapoOuterClass.HueSaturation
import tapo.TapoOuterClass.IntegerValueChange
import java.nio.charset.StandardCharsets
import java.util.concurrent.Flow
import java.util.function.Consumer

class TapoctlControlService : ControlsProviderService() {
    private lateinit var channel: ManagedChannel;
    private lateinit var stub: TapoGrpcKt.TapoCoroutineStub;
    private lateinit var publisher: ReplayProcessor<Control>
    private lateinit var pending: PendingIntent
    private lateinit var eventThread: Thread

    private lateinit var devices: HashMap<String, DeviceControl>

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        val publisher = flow {
            for ((_, ctrl) in devices) {
                if (ctrl.canControlBrightness()) emit(ctrl.getStatelessControl(DeviceControl.POWER_ID, "Power, Brightness"))
                else emit(ctrl.getStatelessControl(DeviceControl.POWER_ID, "Power"))
                if (ctrl.canControlTemperature()) emit(ctrl.getStatelessControl(DeviceControl.TEMPERATURE_ID, "Temperature"))
                if (ctrl.canControlColor()) emit(ctrl.getStatelessControl(DeviceControl.HUE_ID, "Hue"))
                devices[ctrl.id] = ctrl
            }
        }

        return publisher.asPublisher()
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        runBlocking {
            for (compositeId in controlIds) {
                val deviceId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[0]
                val controlId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[1]

                val ctrl = if (devices[deviceId] != null) devices[deviceId]!! else {
                    publisher.onNext(DeviceControl.getUnavailableControl(deviceId, controlId, baseContext))
                    continue
                }

                val request = TapoOuterClass.DeviceRequest.newBuilder().setDevice(ctrl.name).build()
                val response = stub.info(request)

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
                else Log.w("DeviceControl", "Control $controlId for $deviceId did not match any known control id")
            }
        }

        return FlowAdapters.toFlowPublisher(publisher)
    }

    override fun performControlAction(compositeId: String, action: ControlAction, consumer: Consumer<Int>) {
        val deviceId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[0]
        val controlId = compositeId.split(DEVICE_IDENTIFIER_SEPARATOR)[1]

        val ctrl = devices[deviceId]!!

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

        runBlocking {
            try {
                val request = builder.build()
                val info = stub.set(request)

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
                else Log.w("DeviceControl", "Control $controlId for $deviceId did not match any known control id")
            } catch (e: StatusException) {
                consumer.accept(ControlAction.RESPONSE_FAIL)
                Log.e("DeviceControl", e.toString())
            }
        }
    }

    override fun onCreate() {
        Log.i("System", "Created")
        super.onCreate()
        channel = ManagedChannelBuilder.forAddress("192.168.1.173", 19191).usePlaintext().build()
        stub = TapoGrpcKt.TapoCoroutineStub(channel)
        publisher = ReplayProcessor.create()
        pending = PendingIntent.getActivity(baseContext, 1, Intent(), PendingIntent.FLAG_IMMUTABLE)
        runBlocking { loadDevices() }
        startEventThread()
    }

    override fun onDestroy() {
        Log.i("System", "Destroyed")
        super.onDestroy()
        if (::eventThread.isInitialized) eventThread.interrupt()
    }

    private fun handleEvent(event: EventResponse) {

        // Log.i("Event", body.toString())

        when (event.type) {
            TapoOuterClass.EventType.DeviceAuthChange -> {
                Log.i("Event", "Received device auth state change event")
            }
            TapoOuterClass.EventType.DeviceStateChange -> {
                Log.i("Event", "Received device state change event")

                val mapAdapter = Gson().getAdapter(object: TypeToken<Info>() {})
                val info = mapAdapter.fromJson(String(event.body.toByteArray(), StandardCharsets.UTF_8))
                val ctrl = devices.values.find { ctrl -> ctrl.name == info.name }

                Log.i("Event", info.color.toString())

                if (ctrl != null) {
                    if (ctrl.canControlBrightness()) publisher.onNext(ctrl.getPowerBrightnessControl(info.device_on, info.brightness).build())
                    else publisher.onNext(ctrl.getPowerControl(info.device_on).build())
                    if (ctrl.canControlTemperature()) publisher.onNext(ctrl.getTemperatureControl(info.temperature).build())
                    if (ctrl.canControlColor()) publisher.onNext(ctrl.getHueControl(info.hue).build())
                } else Log.w("Event", "Device ${info.name} not found")
            }
            TapoOuterClass.EventType.UNRECOGNIZED -> TODO()
        }
    }

    private fun startEventThread() {
        eventThread = Thread {
            try {
                runBlocking {
                    val request = TapoOuterClass.EventRequest.newBuilder().build()
                    try {
                        stub.events(request).onEach { event -> handleEvent(event) }.collect()
                    } catch (e: StatusException) {
                        Log.i("Event", "Stream closed: $e")
                    }
                }
            } catch (e: InterruptedException) {
                Log.i("Event", "Interrupted event thread")
                return@Thread
            }
        }
        eventThread.start()
    }

    private suspend fun loadDevices() {
        try {
            devices = HashMap()
            val devicesRequest = TapoOuterClass.Empty.newBuilder().build()
            val response = stub.devices(devicesRequest)
            for (dev in response.devicesList) {
                val ctrl = DeviceControl(dev, baseContext)
                devices[ctrl.id] = ctrl
            }
        } catch (e: StatusException) {
            Log.e("DeviceControl", e.toString())
        }
    }
}