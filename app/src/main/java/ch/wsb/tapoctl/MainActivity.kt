package ch.wsb.tapoctl

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import ch.wsb.tapoctl.ui.common.ScaleTransitionDirection
import ch.wsb.tapoctl.ui.common.scaleIntoContainer
import ch.wsb.tapoctl.ui.common.scaleOutOfContainer
import ch.wsb.tapoctl.ui.theme.TapoctlTheme
import ch.wsb.tapoctl.ui.theme.Typography
import ch.wsb.tapoctl.ui.views.DevicesView
import ch.wsb.tapoctl.ui.views.SettingsView

enum class Views(@StringRes val title: Int) {
    Settings(title = R.string.nav_settings),
    Devices(title = R.string.nav_devices)
}

val Context.Datastore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TapoctlTheme {
                TapoctlApp(context = baseContext)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun TapoctlApp(
    context: Context,
    navController: NavHostController = rememberNavController()
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val backStackEntry by navController.currentBackStackEntryAsState()
    val view = Views.valueOf(backStackEntry?.destination?.route ?: Views.Devices.name)

    val fontSize = Typography.titleLarge.fontSize.times(1 - scrollBehavior.state.collapsedFraction.times(0.25f))

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(view.title), style = Typography.titleLarge, fontSize = fontSize) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(),
            )
        },
        bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            label = { Text(stringResource(R.string.nav_devices), style = Typography.labelMedium) },
                            selected = view == Views.Devices,
                            onClick = { navController.navigate(Views.Devices.name) },
                            icon = { Icon(Icons.Filled.Lightbulb, contentDescription = stringResource(R.string.nav_devices_desc)) }
                        )
                        NavigationBarItem(
                            label = { Text(stringResource(R.string.nav_settings), style = Typography.labelMedium) },
                            selected = view == Views.Settings,
                            onClick = { navController.navigate(Views.Settings.name) },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings_desc)) }
                        )
                    }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            NavHost(
                startDestination = Views.Devices.name,
                navController = navController,
                modifier = Modifier.padding(PaddingValues(8.dp, 0.dp)),
                enterTransition = { scaleIntoContainer() },
                exitTransition = { scaleOutOfContainer(direction = ScaleTransitionDirection.OUTWARDS) },
                popEnterTransition = { scaleIntoContainer(direction = ScaleTransitionDirection.INWARDS) },
                popExitTransition = { scaleOutOfContainer() }
            ) {
                composable(Views.Devices.name) {
                    DevicesView()
                }
                composable(Views.Settings.name) {
                    SettingsView(context)
                }
            }
        }
    }
}