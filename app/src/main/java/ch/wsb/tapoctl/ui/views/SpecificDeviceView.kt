package ch.wsb.tapoctl.ui.views

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ch.wsb.tapoctl.service.DeviceControl
import ch.wsb.tapoctl.service.DeviceManager
import ch.wsb.tapoctl.service.HueSaturation
import ch.wsb.tapoctl.service.Info
import ch.wsb.tapoctl.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun SpecificDeviceView(
    device: DeviceControl,
    devices: DeviceManager,
    error: Boolean,
    info: Info?,
    onInfoRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var refreshing by remember { mutableStateOf(false) }
    fun refresh() = scope.launch {
        refreshing = true
        onInfoRequest()
        delay(1000)
        refreshing = false
    }
    val refreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh =  ::refresh
    )

    var localBrightness by remember { mutableStateOf(info?.brightness) }
    var localHueSaturation by remember { mutableStateOf(info?.let { if (it.temperature == 0) HueSaturation(it.hue, it.saturation) else null }) }
    var localTemperature by remember { mutableStateOf(info?.let { if (it.temperature == 0) null else it.temperature }) }

    LaunchedEffect(info) {
        localBrightness = info?.brightness
        localHueSaturation = info?.let { if (it.temperature == 0) HueSaturation(it.hue, it.saturation) else null }
        localTemperature = info?.let { if (it.temperature == 0) null else it.temperature }
    }

    Box(
        modifier = Modifier.pullRefresh(refreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    // modifier = Modifier.padding(PaddingValues(16.dp))
                ) {
                    BrightnessSlider(
                        brightness = localBrightness,
                        onChange =  { localBrightness = it },
                        onChangeFinished = { scope.launch { device.set(brightness = localBrightness) }  }
                    )
                }
            }
            item {
                Card(
                    // modifier = Modifier.padding(PaddingValues(16.dp))
                ) {
                    ColorTemperatureSlider(
                        temperature = localTemperature,
                        onChange = { localTemperature = it },
                        onChangeFinished = { scope.launch { device.set(temperature = localTemperature) } }
                    )
                }
            }
            item {
                Card(
                    // modifier = Modifier.padding(PaddingValues(16.dp))
                ) {
                    HueSaturationField(
                        hue = localHueSaturation?.hue,
                        saturation = localHueSaturation?.saturation,
                        onChange = { localHueSaturation = it },
                        onChangeFinished = { scope.launch { device.set(hueSaturation = localHueSaturation?.toGrpcObject()) } }
                    )
                }
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // TODO: Reset device
                    }
                ) {
                    Text("Reset device")
                }
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
@Composable
fun BrightnessSlider(
    brightness: Int?,
    onChange: (Int) -> Unit,
    onChangeFinished: () -> Unit
) {
    Slider(
        value = brightness?.toFloat() ?: 1f,
        onValueChange = { onChange(it.roundToInt()) },
        onValueChangeFinished = { onChangeFinished() },
        steps = 100,
        valueRange = 1f..100f,
        track = { FillableTrack(it) },
        thumb = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorTemperatureSlider(
    temperature: Int?,
    onChange: (Int) -> Unit,
    onChangeFinished: () -> Unit
) {
    Slider(
        value = temperature?.toFloat() ?: 2500f,
        onValueChange = { onChange(it.roundToInt()) },
        onValueChangeFinished = { onChangeFinished() },
        steps = 800,
        valueRange = 2500f..6500f,
        track = {
            val brush = Brush.linearGradient(listOf(Temperature2500, Temperature3500, Temperature4500, Temperature5500, Temperature6500))
            val start = it.activeRange.start
            val end = it.activeRange.endInclusive

            val colorScheme = MaterialTheme.colorScheme

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val canvasHeight = size.height
                val canvasWidth = size.width
                val offsetX =
                    if (end * canvasWidth + 5.dp.toPx() > canvasWidth) canvasWidth - 10.dp.toPx()
                    else if(end * canvasWidth - 5.dp.toPx() < start * canvasWidth) start * canvasWidth
                    else end * canvasWidth - 5.dp.toPx()

                drawRoundRect(
                    topLeft = Offset(x = 0f, y = 0f),
                    size = Size(canvasWidth, canvasHeight),
                    brush = brush,
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawRoundRect(
                    topLeft = Offset(x = start * canvasWidth, y = 0f),
                    size = Size(end * canvasWidth, canvasHeight),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    color = Color.Unspecified
                )
                if(temperature != null) {
                    drawRoundRect(
                        topLeft = Offset(x = offsetX, y = 0f),
                        size = Size(10.dp.toPx(), canvasHeight),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        color = Color.White
                    )

                    drawRoundRect(
                        topLeft = Offset(x = offsetX + 2.dp.toPx(), y = 2.dp.toPx()),
                        size = Size(6.dp.toPx(), canvasHeight - 4.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        color = colorScheme.primary
                    )
                }
            }
        },
        thumb = {}
    )
}

@Composable
fun FillableTrack(positions: SliderPositions) {
    val start = positions.activeRange.start
    val end = positions.activeRange.endInclusive

    val colorScheme = MaterialTheme.colorScheme

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        drawRoundRect(
            topLeft = Offset(x = 0f, y = 0f),
            size = Size(canvasWidth, canvasHeight),
            color = colorScheme.onSurfaceVariant,
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            topLeft = Offset(x = start * canvasWidth, y = 0f),
            size = Size(end * canvasWidth, canvasHeight),
            color = colorScheme.primary,
            cornerRadius = CornerRadius(8.dp.toPx())
        )
    }
}

@Composable
fun HueSaturationField(
    hue: Int?,
    saturation: Int?,
    onChange: (hueSaturation: HueSaturation) -> Unit,
    onChangeFinished: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val center = Offset(width / 2, height / 2)

        var currentPosition by remember {
            mutableStateOf(center)
        }

        val showPosition = hue != null && saturation != null

        if (showPosition) {
            val posX = hue!!.toFloat() / 360 * width
            val posY = (1 - saturation!!.toFloat() / 100) * height
            currentPosition = Offset(posX, posY)
        }

        fun handleDrag(change: Offset) {
            val hueChange = (change.x / width).coerceIn(0f, 1f) * 360f
            val saturationChange = (1 - (change.y / height)).coerceIn(0f, 1f) * 100f
            onChange(HueSaturation(hueChange.roundToInt(), saturationChange.roundToInt()))
        }

        val canvasModifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val hueChange = (position.x / width).coerceIn(0f, 1f) * 360f
                    val saturationChange = (1 - (position.y / height)).coerceIn(0f, 1f) * 100f
                    onChange(HueSaturation(hueChange.roundToInt(), saturationChange.roundToInt()))
                    onChangeFinished()
                }

            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onChangeFinished() },
                    onDragStart = { handleDrag(it) },
                    onVerticalDrag = { change, _ ->
                        val position = change.position
                        handleDrag(position)
                        change.consume()
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onChangeFinished() },
                    onDragStart = { handleDrag(it) },
                    onHorizontalDrag = { change, _ ->
                        val position = change.position
                        handleDrag(position)
                        change.consume()
                    }
                )
            }
            .fillMaxWidth()
            .fillMaxHeight()

        Canvas(
            modifier = canvasModifier
        ) {
            drawRect(
                topLeft = Offset(x = 0f, y = 0f),
                brush = Brush.linearGradient(
                    colors = gradientColorScaleHSL(),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            )
            drawRect(
                topLeft = Offset(x = 0f, y = 0f),
                brush = transparentToGrayVerticalGradient()
            )
            if(showPosition) {
                drawCircle(
                    center = currentPosition,
                    color = Color.White,
                    radius = 6.dp.toPx(),
                )

                drawCircle(
                    center = currentPosition,
                    color = colorScheme.primary,
                    radius = 4.dp.toPx(),
                )
            }
        }
    }
}

fun gradientColorScaleHSL(
    saturation: Float = 1f,
    lightness: Float = .5f,
    alpha: Float = 1f
) = listOf(
    Color.hsl(hue = 0f, saturation = saturation, lightness = lightness, alpha = alpha),
    Color.hsl(hue = 60f, saturation = saturation, lightness = lightness, alpha = alpha),
    Color.hsl(hue = 120f, saturation = saturation, lightness = lightness, alpha = alpha),
    Color.hsl(hue = 180f, saturation = saturation, lightness = lightness, alpha = alpha),
    Color.hsl(hue = 240f, saturation = saturation, lightness = lightness, alpha = alpha),
    Color.hsl(hue = 300f, saturation = saturation, lightness = lightness, alpha = alpha),
    Color.hsl(hue = 360f, saturation = saturation, lightness = lightness, alpha = alpha)
)

fun transparentToGrayVerticalGradient(
    startY: Float = 0.0f,
    endY: Float = Float.POSITIVE_INFINITY
): Brush {
    return Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Gray),
        startY = startY,
        endY = endY
    )
}