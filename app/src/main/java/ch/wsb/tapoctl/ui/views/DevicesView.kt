package ch.wsb.tapoctl.ui.views

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.wsb.tapoctl.tapoctl.DeviceControl
import ch.wsb.tapoctl.tapoctl.DeviceManager
import ch.wsb.tapoctl.tapoctl.Info
import ch.wsb.tapoctl.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun DevicesView(
    devices: DeviceManager,
    onDeviceNavigate: (device: String) -> Unit,
    onDeviceInfoRequest: (device: String) -> Unit,
    infos: SnapshotStateMap<String, Info>,
    errors: SnapshotStateMap<String, Boolean>
) {
    val scope = rememberCoroutineScope()
    val deviceList = devices.getDevices().collectAsState(emptyList())

    var refreshing by remember { mutableStateOf(false) }
    fun refresh() = scope.launch {
        refreshing = true
        delay(1000)
        refreshing = false
    }
    val refreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh =  ::refresh
    )

    /**
     * Try to set icon color to higher/lower alpha than box content and border theme dependent
     * For compromising on icon alpha one could put a white icon below it to work
     */

    Box(
        modifier = Modifier.pullRefresh(refreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(deviceList.value) {
                DeviceCard(
                    device = it,
                    refreshing = refreshing,
                    error = errors[it.name] ?: false,
                    info = infos[it.name],
                    onNavigate = { onDeviceNavigate(it.name) },
                    onInfoRequest = { onDeviceInfoRequest(it.name) }
                )
            }
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            backgroundColor = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun DeviceCard(
    device: DeviceControl,
    refreshing: Boolean,
    info: Info?,
    error: Boolean,
    onNavigate: () -> Unit,
    onInfoRequest: () -> Unit
) {
    val color = animateColorAsState(
        targetValue = info?.color?.let { Color(it.red, it.green, it.blue, 255) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(1000),
        label = "Device border color"
    )

    val iconColor = animateColorAsState(
        targetValue = info?.color?.let { Color(it.red, it.green, it.blue, 100) } ?: MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(1000),
        label = "Device icon color"
    )

    val deviceRunning = info?.device_on ?: false

    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshing) { if (refreshing) onInfoRequest() }

    Card(
        onClick = { if (info != null && !error) onNavigate() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(16.dp, 12.dp))

        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = color.value,
                            ),
                        ) {
                            Box {
                                Icon(
                                    if (deviceRunning) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                                    contentDescription = "Light bulb",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(8.dp),
                                    tint = Color.White,
                                )
                                Icon(
                                    if (deviceRunning) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                                    contentDescription = "Light bulb",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(8.dp),
                                    tint = iconColor.value,
                                )
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = device.capitalizedName,
                                style = Typography.titleMedium,
                            )
                            Text(
                                text = device.type,
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = { scope.launch { device.setPower(info?.device_on?.not() ?: false) }},
                        content = {
                            if (error) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = "Device error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.PowerSettingsNew,
                                    contentDescription = "Toggle device power"
                                )
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (!deviceRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = if (!deviceRunning) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                        ),
                        enabled = !error && info != null
                    )
                }
            }

        }
    }
}