package com.sample.baignade

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MareeInfoApi(private val context: Context,
                   lat: Float = 0F,
                   lon: Float = 0F) {
    @Serializable
    private data class PortInfoEtale(val datetime: String, val hauteur: Float, val type_etale: String, val coef: Int = -1)
    @Serializable
    private data class PortInfoMaree(val datetime: String, val lieu: String, val etales: List<PortInfoEtale>)
    @Serializable
    private data class PortInfoPrevisDetail(val teau: Int)
    @Serializable
    private data class PortInfoPrevis(val detail: List<PortInfoPrevisDetail>)
    @Serializable
    private data class PortInfoContenu(val marees: List<PortInfoMaree>, val previs: PortInfoPrevis)
    @Serializable
    private data class PortInfo(val contenu: PortInfoContenu)

    private data class FullDataResult(val portName: String,
                              val waterTemperatureInDegrees: Int,
                              val coefMin: Int,
                              val coefMax: Int,
                              val results: List<ResultMareeInfo>)
    private var mPortName: String = context.getString(R.string.default_port_name)
    private var mWaterTemperatureInDegrees = 0
    private var mCoefMin = -1
    private var mCoefMax = -1
    private val mJson = Json { ignoreUnknownKeys = true }
    companion object {
        private const val DOMAIN = "http://webservices.meteoconsult.fr"
    }
    // The first url should always work, url2 is a fallback in case of
    private var url1: String =
        "$DOMAIN/meteoconsultmarine/androidtab/115/fr/v20/previsionsSpot.php?lat=$lat&lon=$lon"
//    private var url2: String =
//        "$DOMAIN/meteoconsultmarine/android/100/fr/v20/previsionsSpot.php?lat=$lat&lon=$lon"
    private fun getJson(strUrl: String): String {
        val url = URL(strUrl)
        val httpConn: HttpURLConnection = url.openConnection() as HttpURLConnection
        httpConn.requestMethod = "GET"
        httpConn.setRequestProperty(
            "User-Agent",
            "Application Android Baignade développées par Scott Hamilton <sgn.hamilton+baignade@protonmail.com>"
        )
        httpConn.setRequestProperty("Accept", "application/json")
        val responseStream: InputStream =
            if (httpConn.responseCode / 100 == 2) httpConn.inputStream else httpConn.errorStream
        val s: Scanner = Scanner(responseStream).useDelimiter("\\A")
        val response = if (s.hasNext()) s.next() else ""
        println("Answer from request url $strUrl: $response")
        return response
    }
    fun getPortName(): String {
        return mPortName.substringBefore('©').substringBefore(" - ")
    }
    fun getWaterTemperatureInDegrees(): Int {
        return mWaterTemperatureInDegrees
    }
    fun getCoefMin(): Int {
        return mCoefMin
    }
    fun getCoefMax(): Int {
        return mCoefMax
    }
    fun getPortList(lat: Float, lon: Float): List<PortListPort> {
        val portListUrl =
            "http://webservices.meteoconsult.fr/meteoconsultmarine/android/100/fr/v20/recherche.php?lat=$lat&lon=$lon"
        return runBlocking {
            return@runBlocking withContext(coroutineContext) {
                return@withContext async(Dispatchers.IO) {
                    val info = mJson.decodeFromString<PortListInfo>(getJson(portListUrl))
                    return@async info.contenu
                }.await()
            }
        }
    }
    fun getPortInformation(): List<ResultMareeInfo> {
        val result = runBlocking {
            return@runBlocking withContext(coroutineContext) {
                return@withContext async(Dispatchers.IO) {
                    val info = mJson.decodeFromString<PortInfo>(getJson(url1))
                    println("Extracted Json From Port Info Request: $info")
                    val (portName, coefs) = if (info.contenu.marees.isNotEmpty()) {
                        info.contenu.marees[0].lieu to
                                info.contenu.marees[0].etales.map { it.coef }.filter { it > 0 }
                    } else {
                        context.getString(R.string.default_port_name) to listOf(0, 0)
                    }
                    val waterTemperatureInDegrees = if (info.contenu.previs.detail.isNotEmpty()) {
                        info.contenu.previs.detail.first().teau
                    } else {
                        0
                    }
                    val result: MutableList<ResultMareeInfo> = mutableListOf()
                    val subList5OrLess = if (info.contenu.marees.size > 6) {
                        info.contenu.marees.subList(0, 5)
                    } else {
                        info.contenu.marees
                    }
                    subList5OrLess.forEachIndexed { jourIndex, maree ->
                        maree.etales.forEach { etale ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val horaire = LocalDateTime.parse(
                                    etale.datetime,
                                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
                                )
                                result += ResultMareeInfo(
                                    jourIndex,
                                    horaire.hour,
                                    horaire.minute,
                                    etale.hauteur)
                            }
                        }
                    }
                    return@async FullDataResult(
                        portName,
                        waterTemperatureInDegrees,
                        coefs.minOrNull() ?: -1,
                        coefs.maxOrNull() ?: -1,
                        result.toList())
                }.await()
            }
        }
        mPortName = result.portName
        mWaterTemperatureInDegrees = result.waterTemperatureInDegrees
        mCoefMin = result.coefMin
        mCoefMax = result.coefMax
        return result.results
    }
}