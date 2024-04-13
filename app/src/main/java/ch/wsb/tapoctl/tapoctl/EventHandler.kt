package ch.wsb.tapoctl.tapoctl

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.grpc.StatusException
import io.reactivex.processors.ReplayProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.jdk9.asFlow
import kotlinx.coroutines.launch
import org.reactivestreams.FlowAdapters
import tapo.TapoOuterClass
import java.nio.charset.StandardCharsets

sealed class Event {
    data class DeviceAuthChanged(val device: Device) : Event()
    data class DeviceStateChanged(val info: Info) : Event()
}

class EventHandler(private val connection: GrpcConnection, private val scope: CoroutineScope) {
    private val publisher = ReplayProcessor.create<Event>()
    private var job: Job? = null

    fun subscribe(): Flow<Event> {
        job = scope.launch {
            try {
                val request = TapoOuterClass.EventRequest.newBuilder().build()
                if (!connection.connected) connection.connect()
                connection.stub.events(request)
                    .onStart { Log.i("Event", "Subscribed to events") }
                    .onCompletion { Log.i("Event", "Finished event subscription") }
                    .onEach { event ->
                        val body = String(event.body.toByteArray(), StandardCharsets.UTF_8)
                        when (event.type) {
                            TapoOuterClass.EventType.DeviceAuthChange -> {
                                Log.i("Event", "Received device auth state change event")
                                val mapAdapter = Gson().getAdapter(object : TypeToken<Device>() {})
                                val device = mapAdapter.fromJson(body)
                                publisher.onNext(Event.DeviceAuthChanged(device))
                            }

                            TapoOuterClass.EventType.DeviceStateChange -> {
                                Log.i("Event", "Received device state change event")
                                val mapAdapter = Gson().getAdapter(object : TypeToken<Info>() {})
                                val info = mapAdapter.fromJson(body)
                                publisher.onNext(Event.DeviceStateChanged(info))
                            }

                            TapoOuterClass.EventType.UNRECOGNIZED -> TODO()
                        }
                    }
                    .collect()
            } catch (e: StatusException) {
                Log.w("Event", "Closed event stream: $e")
            }
        }
        return getEvents()
    }

    fun resubscribe() {
        unsubscribe()
        subscribe()
    }

    fun unsubscribe() {
        job?.cancel()
        job = null
    }

    fun getEvents(): Flow<Event> {
        return FlowAdapters.toFlowPublisher(publisher).asFlow()
    }
}