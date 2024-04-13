package ch.wsb.tapoctl.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ch.wsb.tapoctl.R
import ch.wsb.tapoctl.ui.common.CardWithTitle
import ch.wsb.tapoctl.ui.common.Settings

// The whole `SettingsView` is subject to change
// It seems like there is no clean implementation of xml `PreferencesScreen` for
// compose (yet?)
// Maybe implement the xml `PreferencesScreen` and then use it with `AndroidView()` composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(settings: Settings) {
    var protocolOpen by remember { mutableStateOf(false) }

    CardWithTitle("Server") {
        OutlinedTextField(
            label = { Text(stringResource(R.string.pref_server_address)) },
            value = settings.serverAddress.collectAsState("").value,
            singleLine = true,
            prefix = { Text("${settings.serverProtocol.collectAsState("").value}://") },
            onValueChange = { settings.setServerAddress(it) },
            supportingText = { Text(stringResource(R.string.pref_server_address_desc)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            label = { Text(stringResource(R.string.pref_server_port)) },
            value = settings.serverPort.collectAsState("").value.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            onValueChange = { value: String ->
                try {
                    val port = value.toInt()
                    if (port in 0..65535) settings.setServerPort(port)
                } catch (_: NumberFormatException) {
                }
            },
            supportingText = { Text(stringResource(R.string.pref_server_port_desc)) },
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenuBox(
            modifier = Modifier.fillMaxWidth(),
            expanded = protocolOpen,
            onExpandedChange = { protocolOpen = it },
        ) {
            OutlinedTextField(
                label = { Text(stringResource(R.string.pref_server_protocol)) },
                supportingText = { Text(stringResource(R.string.pref_server_protocol_desc)) },
                value = settings.serverProtocol.collectAsState("").value,
                readOnly = true,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolOpen) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = protocolOpen,
                onDismissRequest = { protocolOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("https") },
                    onClick = {
                        settings.setServerProtocol("https")
                        protocolOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("http") },
                    onClick = {
                        settings.setServerProtocol("http")
                        protocolOpen = false
                    },
                )
            }
        }
    }
}