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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import ch.wsb.tapoctl.ui.common.ScaleTransitionDirection
import ch.wsb.tapoctl.ui.common.Settings
import ch.wsb.tapoctl.ui.common.scaleIntoContainer
import ch.wsb.tapoctl.ui.common.scaleOutOfContainer
import ch.wsb.tapoctl.ui.theme.TapoctlTheme
import ch.wsb.tapoctl.ui.theme.Typography
import ch.wsb.tapoctl.ui.views.DevicesView
import ch.wsb.tapoctl.ui.views.SettingsView
import ch.wsb.tapoctl.ui.views.SpecificDeviceView
import kotlinx.coroutines.launch

val Context.Datastore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(baseContext.Datastore)

        setContent {
            TapoctlTheme {
                TapoctlApp(
                    context = baseContext,
                    settings = settings,
                )
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapoctlApp(
    context: Context,
    settings: Settings,
    navController: NavHostController = rememberNavController()
) {
    AppWrapper(context = context, settings = settings) { scope, _, devices, _, snackbarHostState, infos, errors, fetchDeviceInfo ->
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
                            infos = infos,
                            errors = errors,
                            onDeviceNavigate = { navController.navigate("device/$it") },
                            onDeviceInfoRequest = { fetchDeviceInfo(it) }
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
                                scope.launch { snackbarHostState.showSnackbar("Device $deviceName not found") }
                                navController.navigate("devices")
                            } else {
                                SpecificDeviceView(
                                    device = device,
                                    devices = devices,
                                    info = infos[deviceName],
                                    error = errors[deviceName] ?: false,
                                    onInfoRequest = { fetchDeviceInfo(deviceName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}