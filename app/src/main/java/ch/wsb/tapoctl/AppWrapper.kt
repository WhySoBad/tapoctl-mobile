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
import tapo.TapoOuterClass

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
            } else {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_device_not_found, deviceId)) }
            }
        }
    }

    LaunchedEffect(Unit) {
        // update gRPC connection on every settings change
        settings.data.onEach {
            connection.reconnect()
            devices.fetchDevices()
            devices.iterator().forEach { scope.launch { fetchDeviceInfo(it.key) } }
            eventHandler.resubscribe()
        }.collect()
    }

    LaunchedEffect(Unit) {
        eventHandler.subscribe().onEach {
            if (it is Event.DeviceStateChanged) {
                deviceInfos[it.info.name] = it.info
                deviceErrors[it.info.name] = false
            } else if(it is Event.DeviceAuthChanged) {
                if (it.device.status != TapoOuterClass.SessionStatus.Authenticated) {
                    deviceErrors[it.device.name] = true;
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.error_invalid_session),
                            actionLabel = context.getString(R.string.action_refresh)
                        )
                        when (result) {
                            SnackbarResult.ActionPerformed -> fetchDeviceInfo(it.device.name)
                            SnackbarResult.Dismissed -> {}
                        }
                    }
                } else deviceErrors[it.device.name] = false;
            }
        }.collect()
    }

    children(scope, connection, devices, eventHandler, snackbarHostState, deviceInfos, deviceErrors, ::fetchDeviceInfo)
}
