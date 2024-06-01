package ch.wsb.tapoctl.tapoctl

import android.util.Log
import ch.wsb.tapoctl.GrpcNotConnectedException
import ch.wsb.tapoctl.ui.common.DEFAULT_SERVER_ADDRESS
import ch.wsb.tapoctl.ui.common.DEFAULT_SERVER_PORT
import ch.wsb.tapoctl.ui.common.DEFAULT_SERVER_PROTOCOL
import ch.wsb.tapoctl.ui.common.Settings
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import tapo.TapoGrpcKt
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit

class GrpcConnection(private val settings: Settings) {
    private var channel: ManagedChannel? = null

    var connected: Boolean = false
        private set
    lateinit var stub: TapoGrpcKt.TapoCoroutineStub
        private set

    /**
     * Connect to the server in a blocking manner. After the initial connection the `stub` field is initialized
     */
    @Throws(GrpcNotConnectedException::class)
    fun connect() {
        // this needs to run in blocking mode since the following code relies on the stub being set
        try {
            runBlocking {
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
                channel?.let {
                    stub = TapoGrpcKt.TapoCoroutineStub(it)
                    connected = true
                }
            }
        } catch (e: StatusException) {
            Log.e("SetupGrpc", e.toString())
            throw GrpcNotConnectedException()
        } catch (e: URISyntaxException) {
            Log.e("SetupGrpc", e.toString())
            throw GrpcNotConnectedException()
        } catch (e: IllegalArgumentException) {
            Log.e("SetupGrpc", e.toString())
            throw GrpcNotConnectedException()
        }
    }

    /**
     * Reconnect the connection to the server.
     * Internally closes and reopens the connection
     */
    @Throws(GrpcNotConnectedException::class)
    fun reconnect() {
        close()
        connect()
    }

    /**
     * Close the connection
     */
    fun close() {
        channel?.shutdownNow()
        channel?.awaitTermination(2, TimeUnit.SECONDS)
        connected = false
    }
}