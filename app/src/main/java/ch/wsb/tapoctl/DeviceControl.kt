package ch.wsb.tapoctl

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.controls.Control
import android.service.controls.Control.StatefulBuilder
import android.service.controls.Control.StatelessBuilder
import android.service.controls.DeviceTypes
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import tapo.TapoOuterClass.Device
import java.util.*

// random-ish separator which must not be contained in any device name
const val DEVICE_IDENTIFIER_SEPARATOR: String = "[]_!_(&)"

class DeviceControl(private val device: Device, context: Context) {
    companion object {
        private val COLORED_LIGHT_BULBS = listOf("L530", "L630", "L900")
        private val LIGHT_BULBS = listOf("L510", "L520", "L610")
        const val TEMPERATURE_ID = "temperature"
        const val POWER_ID = "power"
        const val BRIGHTNESS_ID = "brightness"
        const val HUE_ID = "hue"

        fun getUnavailableControl(deviceId: String, controlId: String, context: Context): Control {
            val intent: PendingIntent = PendingIntent.getActivity(context, 1, Intent(), PendingIntent.FLAG_IMMUTABLE)
            val compositeId = createCompositeId(deviceId, controlId)
            return StatefulBuilder(compositeId, intent)
                .setDeviceType(DeviceTypes.TYPE_LIGHT)
                .setStatus(Control.STATUS_UNKNOWN)
                .setTitle(getTextForControl(controlId))
                .build()
        }

        private fun createCompositeId(deviceId: String, controlId: String): String {
            return "$deviceId${DEVICE_IDENTIFIER_SEPARATOR}$controlId"
        }

        private fun getTextForControl(controlId: String): String {
            return when (controlId) {
                HUE_ID -> "Hue"
                BRIGHTNESS_ID -> "Brightness"
                POWER_ID -> "Power"
                TEMPERATURE_ID -> "Temperature"
                else -> ""
            }
        }
    }

    val id: String = device.name
    val name: String = device.name

    private val intent: PendingIntent = PendingIntent.getActivity(context, 1, Intent(), PendingIntent.FLAG_IMMUTABLE)
    private val structure = device.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private fun composeId(identifier: String): String {
        return createCompositeId(id, identifier)
    }

    fun getStatelessControl(id: String, title: String): Control {
        return StatelessBuilder(composeId(id), intent)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setTitle(title)
            .setSubtitle(device.type)
            .setStructure(structure)
            .build()
    }

    fun getTemperatureControl(temperature: Int): StatefulBuilder {
        // TODO: Do something with this boolean
        val invalid = temperature < 2500 || temperature > 6500f
        val rangeTemplate = RangeTemplate(composeId(TEMPERATURE_ID), 2500f, 6500f, temperature.toFloat().coerceIn(2500f, 6500f), 5f, "%.0fK")

        return StatefulBuilder(composeId(TEMPERATURE_ID), intent)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setControlTemplate(rangeTemplate)
            .setStatus(Control.STATUS_OK)
            .setTitle(getTextForControl(TEMPERATURE_ID))
            .setSubtitle(device.type)
            .setStructure(structure)
    }

    fun getPowerBrightnessControl(power: Boolean, brightness: Int): StatefulBuilder {
        // TODO: Do something with this boolean
        val invalid = brightness < 1 || brightness > 100
        val rangeTemplate = RangeTemplate(composeId(BRIGHTNESS_ID), 1f, 100f, brightness.toFloat().coerceIn(1f, 100f), 1f, "%.0f%%")
        val toggleTemplate = ControlButton(power, "Toggle the device power state")

        return StatefulBuilder(composeId(POWER_ID), intent)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setControlTemplate(ToggleRangeTemplate(composeId(POWER_ID), toggleTemplate, rangeTemplate))
            .setStatus(Control.STATUS_OK)
            .setSubtitle(device.type)
            .setTitle(getTextForControl(BRIGHTNESS_ID))
            .setStructure(structure)
    }

    fun getPowerControl(power: Boolean): StatefulBuilder {
        val toggleTemplate = ControlButton(power, "Toggle the device power state")

        return StatefulBuilder(composeId(POWER_ID), intent)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setControlTemplate(ToggleTemplate(composeId(POWER_ID), toggleTemplate))
            .setStatus(Control.STATUS_OK)
            .setSubtitle(device.type)
            .setTitle(getTextForControl(POWER_ID))
            .setStructure(structure)
    }

    fun getHueControl(hue: Int): StatefulBuilder {
        // TODO: Do something with this boolean
        val invalid = hue < 1 || hue > 360
        val rangeTemplate = RangeTemplate(composeId(HUE_ID), 1f, 360f, hue.toFloat().coerceIn(1f, 360f), 1f, "%.0f")

        return StatefulBuilder(composeId(HUE_ID), intent)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setControlTemplate(rangeTemplate)
            .setStatus(Control.STATUS_OK)
            .setSubtitle(device.type)
            .setTitle(getTextForControl(HUE_ID))
            .setStructure(structure)
    }

    private fun isColorLightBulb(): Boolean {
        return COLORED_LIGHT_BULBS.contains(device.type)
    }

    private fun isNormalLightBulb(): Boolean {
        return LIGHT_BULBS.contains(device.type)
    }

    fun isResettable(): Boolean {
        return isColorLightBulb() || isNormalLightBulb()
    }

    fun canControlColor(): Boolean {
        return isColorLightBulb()
    }

    fun canControlBrightness(): Boolean {
        return isColorLightBulb() || isNormalLightBulb()
    }

    fun canControlTemperature(): Boolean {
        return isColorLightBulb()
    }

    fun canGetUsage(): Boolean {
        return isColorLightBulb() || isNormalLightBulb()
    }
}