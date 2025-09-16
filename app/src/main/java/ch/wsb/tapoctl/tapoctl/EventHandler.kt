package ch.wsb.tapoctl.tapoctl

import android.util.Log
import ch.wsb.tapoctl.GrpcNotConnectedException
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
    data class DeviceStateChanged(val info: Info, val device: String) : Event()
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
                            TapoOuterClass.EventType.DeviceStateChange -> {
                                Log.i("Event", "Received device state change event")
                                val mapAdapter = Gson().getAdapter(object : TypeToken<Info>() {})
                                val info = mapAdapter.fromJson(body)
                                publisher.onNext(Event.DeviceStateChanged(info, event.device))
                            }

                            TapoOuterClass.EventType.UNRECOGNIZED -> TODO()
                        }
                    }
                    .catch { e ->
                        Log.e("Event", "Received exception: ${e.localizedMessage}")
                    }
                    .collect()
            } catch (e: StatusException) {
                Log.w("Event", "Closed event stream: $e")
            } catch (_: GrpcNotConnectedException) {
                Log.e("Event", "Grpc connection not connected")
            }
        }

        return getEvents()
    }

    fun resubscribe(): Flow<Event> {
        unsubscribe()
        return subscribe()
    }

    fun unsubscribe() {
        if (job?.isActive == true) job?.cancel()
        job = null
    }

    fun getEvents(): Flow<Event> {
        return FlowAdapters.toFlowPublisher(publisher).asFlow()
    }
}