package ch.wsb.tapoctl.tapoctl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import tapo.TapoOuterClass
import tapo.TapoOuterClass.IntegerValueChange
import tapo.TapoOuterClass.SessionStatus

// Bodies for the EventResponse from the grpc server. Those
// additional definitions are needed since the body is returned
// as byte array.
// The response body can be parsed to a specific body using gson.

@Parcelize
data class Device(
    val name: String,
    val type: String,
    val address: String,
    val status: SessionStatus
) : Parcelable

@Parcelize
data class Info(
    val device_on: Boolean,
    val on_time: Long,
    val overheated: Boolean,
    val brightness: Int,
    val hue: Int,
    val saturation: Int,
    val temperature: Int,
    val dynamic_effect_id: String,
    val color: Rgb,
    val name: String
) : Parcelable {
    constructor(info: TapoOuterClass.InfoResponse) : this(
        info.deviceOn,
        info.onTime,
        info.overheated,
        if (info.hasBrightness()) info.brightness else 1,
        if (info.hasHue()) info.hue else 1,
        if (info.hasSaturation()) info.saturation else 1,
        if (info.hasTemperature()) info.temperature else 2500,
        info.dynamicEffectId,
        Rgb(info.color.red, info.color.green, info.color.blue),
        info.name
    )
}

@Parcelize
data class Rgb(val red: Int, val green: Int, val blue: Int) : Parcelable


@Parcelize
data class HueSaturation(val hue: Int, val saturation: Int, val hueAbsolute: Boolean = true, val saturationAbsolute: Boolean = true) : Parcelable {
    fun toGrpcObject(): TapoOuterClass.HueSaturation {
        return TapoOuterClass.HueSaturation
            .newBuilder()
            .setSaturation(IntegerValueChange.newBuilder().setValue(saturation).setAbsolute(saturationAbsolute))
            .setHue(IntegerValueChange.newBuilder().setValue(hue).setAbsolute(hueAbsolute))
            .build()
    }
}