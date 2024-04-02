package ch.wsb.tapoctl.service

import tapo.TapoOuterClass.SessionStatus

// Bodies for the EventResponse from the grpc server. Those
// additional definitions are needed since the body is returned
// as byte array.
// The response body can be parsed to a specific body using gson.

data class Device(
    val name: String,
    val type: String,
    val address: String,
    val status: SessionStatus
)

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
)

data class Rgb(val red: Int, val green: Int, val blue: Int)
