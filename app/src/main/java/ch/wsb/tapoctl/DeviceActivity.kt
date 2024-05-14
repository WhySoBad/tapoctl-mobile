package ch.wsb.tapoctl

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.wsb.tapoctl.ui.common.Settings
import ch.wsb.tapoctl.ui.theme.TapoctlTheme
import ch.wsb.tapoctl.ui.theme.Typography
import ch.wsb.tapoctl.ui.views.SpecificDeviceView

class DeviceActivity : ComponentActivity() {
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(baseContext.Datastore)

        val deviceName = intent.extras?.getString("ch.wsb.tapoctl.device")

        setContent {
            TapoctlTheme {
                if (deviceName != null) DeviceActivityApp(baseContext, settings, deviceName)
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun DeviceActivityApp(
    context: Context,
    settings: Settings,
    deviceId: String
) {
    AppWrapper(context = context, settings = settings) { _, _, devices, _, _, infos, errors, fetchDeviceInfo ->
        val device = devices.get(deviceId)
        val info = infos[deviceId]
        val error = errors[deviceId] ?: false

        Scaffold { _ ->
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (device != null) {
                        Text(
                            text = device.capitalizedName,
                            style = Typography.titleLarge
                        )
                        SpecificDeviceView(
                            device = device,
                            devices = devices,
                            onInfoRequest = { fetchDeviceInfo(deviceId) },
                            info = info,
                            error = error
                        )
                    }
                }

            }
        }
    }
}