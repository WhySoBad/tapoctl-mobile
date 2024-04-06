package ch.wsb.tapoctl

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ch.wsb.tapoctl.service.DeviceManager
import ch.wsb.tapoctl.service.Event
import ch.wsb.tapoctl.service.EventThread
import ch.wsb.tapoctl.service.Info
import ch.wsb.tapoctl.ui.common.ScaleTransitionDirection
import ch.wsb.tapoctl.ui.common.scaleIntoContainer
import ch.wsb.tapoctl.ui.common.scaleOutOfContainer
import ch.wsb.tapoctl.ui.theme.TapoctlTheme
import ch.wsb.tapoctl.ui.theme.Typography
import ch.wsb.tapoctl.ui.views.DevicesView
import ch.wsb.tapoctl.ui.views.SettingsView
import ch.wsb.tapoctl.ui.views.SpecificDeviceView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tapo.TapoOuterClass

val Context.Datastore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var connection: GrpcConnection
    private lateinit var devices: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(baseContext.Datastore)
        connection = GrpcConnection(settings)
        devices = DeviceManager(connection, baseContext)

        setContent {
            TapoctlTheme {
                TapoctlApp(
                    settings = settings,
                    connection = connection,
                    devices = devices
                )
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapoctlApp(
    connection: GrpcConnection,
    settings: Settings,
    devices: DeviceManager,
    navController: NavHostController = rememberNavController()
) {
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val deviceInfos = rememberSaveable(
        saver = mapSaver(
            save = { it },
            restore = { SnapshotStateMap<String, Info>().apply { it } }
        )
    ) { mutableStateMapOf() }
    val deviceErrors = rememberSaveable(
        saver = mapSaver(
            save = { it },
            restore = { SnapshotStateMap<String, Boolean>().apply { it } }
        )
    ) { mutableStateMapOf() }

    fun updateDeviceInfo(event: Event) {
        if (event is Event.DeviceStateChanged) {
            deviceInfos[event.info.name] = event.info
            deviceErrors[event.info.name] = false
        } else if(event is Event.DeviceAuthChanged) {
            deviceErrors[event.device.name] = event.device.status != TapoOuterClass.SessionStatus.Authenticated
        }
    }

    fun fetchDeviceInfo(deviceId: String) {
        scope.launch {
            val device = devices.get(deviceId)
            if (device != null) {
                val info = device.getInfo()
                if (info != null) {
                    deviceInfos[device.name] = info
                    deviceErrors[device.name] = false
                } else deviceErrors[device.name] = true
            }
        }
    }

    var eventThread by remember { mutableStateOf(EventThread(connection, ::updateDeviceInfo)) }

    LaunchedEffect(Unit) {
        // update gRPC connection on every settings change
        settings.data.onEach {
            connection.reconnect()
            devices.fetchDevices()
            devices.iterator().forEach { scope.launch { fetchDeviceInfo(it.key) } }
            eventThread.interrupt()
            eventThread = EventThread(connection, ::updateDeviceInfo)
            eventThread.start()
        }.collect()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val view = backStackEntry?.destination?.route ?: "devices"

    val title = when(view) {
        "devices" -> stringResource(R.string.nav_devices)
        "settings" -> stringResource(R.string.nav_settings)
        "device/{device}" -> backStackEntry?.arguments?.getString("device")?.let { devices.get(it)?.capitalizedName } ?: "Device"
        else -> ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {  Text(text = title, style = Typography.titleLarge) },
                colors = TopAppBarDefaults.mediumTopAppBarColors(),
            )
        },
        bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            label = { Text(stringResource(R.string.nav_devices), style = Typography.labelMedium) },
                            selected = view == "devices",
                            onClick = { navController.navigate("devices") },
                            icon = { Icon(Icons.Filled.Lightbulb, contentDescription = stringResource(R.string.nav_devices_desc)) }
                        )
                        NavigationBarItem(
                            label = { Text(stringResource(R.string.nav_settings), style = Typography.labelMedium) },
                            selected = view == "settings",
                            onClick = { navController.navigate("settings") },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings_desc)) }
                        )
                    }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            NavHost(
                startDestination = "devices",
                navController = navController,
                modifier = Modifier.padding(PaddingValues(8.dp, 0.dp)),
                enterTransition = { scaleIntoContainer() },
                exitTransition = { scaleOutOfContainer(direction = ScaleTransitionDirection.OUTWARDS) },
                popEnterTransition = { scaleIntoContainer(direction = ScaleTransitionDirection.INWARDS) },
                popExitTransition = { scaleOutOfContainer() }
            ) {
                composable("devices") {
                    DevicesView(
                        devices = devices,
                        infos = deviceInfos,
                        errors = deviceErrors,
                        onDeviceNavigate = { navController.navigate("device/$it") },
                        onDeviceInfoRequest = ::fetchDeviceInfo
                    )
                }
                composable("settings") {
                    SettingsView(settings)
                }
                composable("device/{device}") { entry ->
                    val deviceName = entry.arguments?.getString("device")
                    if (deviceName == null) {
                        scope.launch { snackbarHostState.showSnackbar("Navigated to unknown device") }
                        navController.navigate("devices")
                    } else {
                        val device = devices.get(deviceName)
                        if (device == null) {
                            scope.launch { snackbarHostState.showSnackbar("Cannot find device '$deviceName'") }
                            navController.navigate("devices")
                        } else {
                            SpecificDeviceView(
                                device = device,
                                devices = devices,
                                info = deviceInfos[deviceName],
                                error = deviceErrors[deviceName] ?: false,
                                onInfoRequest = { fetchDeviceInfo(deviceName) }
                            )
                        }
                    }
                }
            }
        }
    }
}