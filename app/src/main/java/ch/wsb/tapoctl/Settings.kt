package ch.wsb.tapoctl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

val SERVER_ADDRESS_KEY = stringPreferencesKey("server_address")
val SERVER_PORT_KEY = intPreferencesKey("server_port")
val SERVER_PROTOCOL_KEY = stringPreferencesKey("server_protocol")

const val DEFAULT_SERVER_ADDRESS = "127.0.0.1"
const val DEFAULT_SERVER_PORT = 19191
const val DEFAULT_SERVER_PROTOCOL = "http"

class Settings(private val dataStore: DataStore<Preferences>) {
    val serverAddress = dataStore.data.map { p -> p[SERVER_ADDRESS_KEY] ?: DEFAULT_SERVER_ADDRESS }

    val serverPort = dataStore.data.map { p -> p[SERVER_PORT_KEY] ?: DEFAULT_SERVER_PORT }

    val serverProtocol = dataStore.data.map { p -> p[SERVER_PROTOCOL_KEY] ?: DEFAULT_SERVER_PROTOCOL }

    val data = dataStore.data

    fun setServerAddress(address: String) {
        runBlocking {
            dataStore.edit { preferences -> preferences[SERVER_ADDRESS_KEY] = address }
        }
    }

    fun setServerPort(port: Int) {
        runBlocking {
            dataStore.edit { preferences -> preferences[SERVER_PORT_KEY] = port }
        }
    }

    fun setServerProtocol(protocol: String) {
        runBlocking {
            dataStore.edit { preferences -> preferences[SERVER_PROTOCOL_KEY] = protocol }
        }
    }
}

