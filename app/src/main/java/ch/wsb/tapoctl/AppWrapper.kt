package ch.wsb.tapoctl

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ch.wsb.tapoctl.tapoctl.DeviceManager
import ch.wsb.tapoctl.tapoctl.Info
import ch.wsb.tapoctl.tapoctl.Event
import ch.wsb.tapoctl.tapoctl.EventHandler
import ch.wsb.tapoctl.tapoctl.GrpcConnection
import ch.wsb.tapoctl.ui.common.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun AppWrapper(
    context: Context,
    settings: Settings,
    children: @Composable (
        scope: CoroutineScope,
        connection: GrpcConnection,
        devices: DeviceManager,
        eventHandler: EventHandler,
        snackbarHostState: SnackbarHostState,
        infos: SnapshotStateMap<String, Info>,
        errors: SnapshotStateMap<String, Boolean>,
        fetchDeviceInfo: (deviceId: String) -> Unit
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    val connection = remember { GrpcConnection(settings) }
    val devices = remember { DeviceManager(connection, context) }
    val eventHandler = remember { EventHandler(connection, scope) }

    val snackbarHostState = remember { SnackbarHostState() }

    val deviceInfos = remember { mutableStateMapOf<String, Info>() }
    val deviceErrors = remember { mutableStateMapOf<String, Boolean>() }

    fun fetchDeviceInfo(deviceId: String) {
        scope.launch {
            val device = devices.get(deviceId)
            if (device != null) {
                try {
                    val info = device.getInfo()
                    if (info != null) {
                        deviceInfos[device.name] = info
                        deviceErrors[device.name] = false
                    } else {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(R.string.error_device_info),
                                actionLabel = context.getString(R.string.action_retry)
                            )
                            when (result) {
                                SnackbarResult.ActionPerformed -> {
                                    val info = device.getInfo()
                                    if (info != null) {
                                        deviceInfos[device.name] = info
                                        deviceErrors[device.name] = false
                                    } else deviceErrors[device.name] = true
                                }
                                SnackbarResult.Dismissed -> deviceErrors[device.name] = true
                            }
                        }
                    }
                } catch (_: GrpcNotConnectedException) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_grpc_not_connected)) }
                }
            } else {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_device_not_found, deviceId)) }
            }
        }
    }

    fun handleEvent(event: Event) {
        if (event is Event.DeviceStateChanged) {
            deviceInfos[event.device] = event.info
            deviceErrors[event.device] = false
        }
    }

    LaunchedEffect(Unit) {
        // update gRPC connection on every settings change
        try {
            settings.data.onEach {
                connection.reconnect()
                devices.fetchDevices()
                devices.iterator().forEach { scope.launch { fetchDeviceInfo(it.key) } }
                eventHandler.resubscribe().onEach { handleEvent(it) }.collect()
            }.collect()
        } catch (_: GrpcNotConnectedException) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_grpc_not_connected)) }
        }
    }

    LaunchedEffect(Unit) {
        eventHandler.subscribe().onEach { handleEvent(it) }.collect()
    }

    children(scope, connection, devices, eventHandler, snackbarHostState, deviceInfos, deviceErrors, ::fetchDeviceInfo)
}