package com.sample.baignade

import kotlinx.serialization.Serializable

typealias XYSerie = List<Pair<Float, Float>>
data class PortMetadata(val portName: String,
                        val waterTemperatureInDegrees: Int,
                        val coefMin: Int,
                        val coefMax: Int)
data class ResultMareeInfo(val dayOffset: Int, val hour: Int, val minutes: Int, val height: Float)

@Serializable
data class PortListPort(val nom: String, val lat: Float, val lon: Float)
@Serializable
data class PortListInfo(val contenu: List<PortListPort>)