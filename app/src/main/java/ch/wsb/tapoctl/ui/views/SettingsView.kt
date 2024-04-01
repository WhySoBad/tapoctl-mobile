package ch.wsb.tapoctl.ui.views

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ch.wsb.tapoctl.Datastore
import ch.wsb.tapoctl.R
import ch.wsb.tapoctl.ui.theme.Typography
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// The whole `SettingsView` is subject to change
// It seems like there is no clean implementation of xml `PreferencesScreen` for
// compose (yet?)
// Maybe implement the xml `PreferencesScreen` and then use it with `AndroidView()` composable

val SERVER_ADDRESS_KEY = stringPreferencesKey("server_address")
val SERVER_PORT_KEY = intPreferencesKey("server_port")

@Composable
fun SettingsView(context: Context) {
    val address = context.Datastore.data.map { preferences -> preferences[SERVER_ADDRESS_KEY] ?: "http://127.0.0.1" }
    val port = context.Datastore.data.map { preferences -> preferences[SERVER_PORT_KEY] ?: 19191 }

    SettingsSection(
        title = "Server"
    ) {
        OutlinedTextField(
            label = { Text(stringResource(R.string.pref_server_address)) },
            value = address.collectAsState("").value,
            singleLine = true,
            onValueChange = { value ->
                runBlocking {
                    context.Datastore.edit { settings ->
                        settings[SERVER_ADDRESS_KEY] = value
                    }
                }
            },
            supportingText = { Text(stringResource(R.string.pref_server_address_desc)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            label = { Text(stringResource(R.string.pref_server_port)) },
            value = port.collectAsState("").value.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            onValueChange = { value: String ->
                try {
                    val port = value.toInt()
                    if (port in 0..65535) {
                        runBlocking {
                            context.Datastore.edit { settings ->
                                settings[SERVER_PORT_KEY] = port
                            }
                        }
                    }
                } catch (_: NumberFormatException) {
                }
            },
            supportingText = { Text(stringResource(R.string.pref_server_port_desc)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    children: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PaddingValues(8.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(PaddingValues(12.dp))
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = Typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.padding(4.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                children()
            }
        }
    }
}