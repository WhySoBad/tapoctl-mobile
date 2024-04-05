package ch.wsb.tapoctl

import android.util.Log
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import tapo.TapoGrpcKt

class GrpcConnection(private val settings: Settings) {
    private lateinit var channel: ManagedChannel

    var connected: Boolean = false
        private set
    lateinit var stub: TapoGrpcKt.TapoCoroutineStub
        private set

    fun connect() {
        runBlocking {
            try {
                val address = settings.serverAddress.firstOrNull() ?: DEFAULT_SERVER_ADDRESS
                val port = settings.serverPort.firstOrNull() ?: DEFAULT_SERVER_PORT
                val protocol = settings.serverProtocol.firstOrNull() ?: DEFAULT_SERVER_PROTOCOL

                val builder = ManagedChannelBuilder
                    .forAddress(address, port)
                    .enableRetry()
                    .maxRetryAttempts(5)

                if (protocol == "https") builder.useTransportSecurity()
                else builder.usePlaintext()

                channel = builder.build()
                stub = TapoGrpcKt.TapoCoroutineStub(channel)
                connected = true
            } catch (e: StatusException) {
                Log.e("SetupGrpc", e.toString())
            }
        }
    }

    fun reconnect() {
        close()
        connect()
    }

    fun close() {
        if(::channel.isInitialized) channel.shutdownNow()
        connected = false
    }
}