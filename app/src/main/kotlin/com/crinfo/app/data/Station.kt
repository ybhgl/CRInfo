package com.crinfo.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 一座车站。code 是 12306 电报码，接口间都以它为主键。 */
@Serializable
data class Station(
    @SerialName("n") val name: String,
    @SerialName("c") val code: String,
    @SerialName("p") val pinyin: String,
    @SerialName("j") val initials: String,
    @SerialName("g") val city: String,
)
