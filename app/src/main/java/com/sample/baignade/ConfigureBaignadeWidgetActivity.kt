package com.sample.baignade

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock.uptimeMillis
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class ConfigureBaignadeWidgetActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_APPWIDGET_ID_CUSTOM =
            "appWidgetIdCustom"
        private const val EXTRA_APPWIDGET_IS_UPDATE =
            "appWidgetIsUpdate"
        private const val USER_AGENT =
            "Baignade Widget App, Developer is Scott Hamilton <sgn.hamilton+baignade@protonmail.com>"
        private val START_POINT = GeoPoint(48.783, -3.033)
        private const val PREFS_NAME = "com.sample.baignade.ConfigureBaignadeWidgetActivity"
        private const val LAT_PREF_PREFIX = "pref_lat_"
        private const val LON_PREF_PREFIX = "pref_lon_"
        private const val POINTS_PREF_PREFIX = "pref_points_"
        private const val PORT_NAME_PREF_PREFIX = "pref_port_name_"
        private const val WATER_TEMPERATURE_IN_DEGREES_PREF_PREFIX =
            "pref_water_temperature_in_degrees_"
        private const val COEF_MIN_PREF_PREFIX = "pref_coef_min_"
        private const val COEF_MAX_PREF_PREFIX = "pref_coef_max"
        fun savePortMetadataPreference(context: Context,
                                       appWidgetId: Int,
                                       portMetadata: PortMetadata) {
            context.getSharedPreferences(PREFS_NAME, 0).edit().apply {
                putString(PORT_NAME_PREF_PREFIX + appWidgetId, portMetadata.portName)
                putInt(WATER_TEMPERATURE_IN_DEGREES_PREF_PREFIX + appWidgetId,
                    portMetadata.waterTemperatureInDegrees)
                putInt(COEF_MIN_PREF_PREFIX + appWidgetId, portMetadata.coefMin)
                putInt(COEF_MAX_PREF_PREFIX + appWidgetId, portMetadata.coefMax)
                commit()
            }
        }
        fun loadPortMetadataPreference(context: Context, appWidgetId: Int): PortMetadata {
            context.getSharedPreferences(PREFS_NAME, 0).run {
                val portName = getString(PORT_NAME_PREF_PREFIX + appWidgetId, "") ?:
                    context.getString(R.string.default_port_name)
                val waterTemperatureInDegrees = getInt(
                    WATER_TEMPERATURE_IN_DEGREES_PREF_PREFIX + appWidgetId, 0)
                val coefMin = getInt(
                    COEF_MIN_PREF_PREFIX + appWidgetId, 0)
                val coefMax = getInt(
                    COEF_MAX_PREF_PREFIX + appWidgetId, 0)
                return PortMetadata(portName, waterTemperatureInDegrees, coefMin, coefMax)
            }
        }
        fun savePointsPreference(context: Context,
                                 appWidgetId: Int,
                                 xVals: List<Float>,
                                 yVals: List<Float>) {
            if (xVals.isNotEmpty() && xVals.size == yVals.size) {
                context.getSharedPreferences(PREFS_NAME, 0).edit().apply {
                    putString(POINTS_PREF_PREFIX + appWidgetId,
                        Json.encodeToString(xVals.zip(yVals)))
                    commit()
                }
            } else {
                println("Error, data is invalid, not saving it. xVals: $xVals, yVals: $yVals")
            }
        }
        fun loadPointsPreference(context: Context,
                                 appWidgetId: Int): XYSerie {
            context.getSharedPreferences(PREFS_NAME, 0).run {
                val strPoints = getString(POINTS_PREF_PREFIX + appWidgetId, "")
                return if (strPoints?.isNotEmpty() == true) {
                    Json.decodeFromString(strPoints)
                } else {
                    listOf()
                }
            }
        }
        fun saveCoordinatesPreference(context: Context,
                                      appWidgetId: Int,
                                      coordinates: GeoPoint) {
            context.getSharedPreferences(PREFS_NAME, 0).edit().apply {
                putFloat(LON_PREF_PREFIX + appWidgetId, coordinates.longitude.toFloat())
                putFloat(LAT_PREF_PREFIX + appWidgetId, coordinates.latitude.toFloat())
                commit()
            }
        }
        fun loadCoordinatesPreference(context: Context, appWidgetId: Int): GeoPoint {
            context.getSharedPreferences(PREFS_NAME, 0).run {
                val lat = getFloat(LAT_PREF_PREFIX + appWidgetId, 0F)
                val lon = getFloat(LON_PREF_PREFIX + appWidgetId, 0F)
                return GeoPoint(lat.toDouble(), lon.toDouble())
            }
        }
        fun deleteWidgetPreferences(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            listOf(
                LAT_PREF_PREFIX,
                LON_PREF_PREFIX,
                POINTS_PREF_PREFIX,
                PORT_NAME_PREF_PREFIX,
                WATER_TEMPERATURE_IN_DEGREES_PREF_PREFIX,
                COEF_MIN_PREF_PREFIX,
                COEF_MAX_PREF_PREFIX).forEach {
                val key = it+appWidgetId
                if (prefs.contains(key)) {
                    prefs.edit().remove(key).commit()
                }
            }
        }
    }
    private var mAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var mActionIsUpdate = false
    private lateinit var map: MapView
    private var mMarkersCount = 0
    private var mMarkersCountMutex = Mutex(false)
    private val mPortUpdateAlreadyRunning = AtomicBoolean(false)
    private val mareeInfoApi: MareeInfoApi by lazy {
        MareeInfoApi(this)
    }
    private val mMapListener = object : MapListener {
        var lastMoveTimeMillis = AtomicInteger(0)
        override fun onScroll(event: ScrollEvent?): Boolean {
            lastMoveTimeMillis.set(uptimeMillis().toInt())
            return true
        }
        override fun onZoom(event: ZoomEvent?): Boolean {
            lastMoveTimeMillis.set(uptimeMillis().toInt())
            return true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.also {
            mAppWidgetId =
                it.getInt(
                    if (it.containsKey(EXTRA_APPWIDGET_ID_CUSTOM))
                        { EXTRA_APPWIDGET_ID_CUSTOM }
                    else { AppWidgetManager.EXTRA_APPWIDGET_ID},
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            mActionIsUpdate = it.getBoolean(EXTRA_APPWIDGET_IS_UPDATE)
            title = "Baignade"+if (BuildConfig.DEBUG) { " ($mAppWidgetId)" } else { "" }
        }
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent())
            finish()
        }
        setContentView(R.layout.activity_configure_baignade_widget)
        Configuration.getInstance().userAgentValue = USER_AGENT
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(6.0)
        map.addMapListener(mMapListener)
        mapController.setCenter(START_POINT)
        map.setMultiTouchControls(true)
        timer(initialDelay = 0L, period = 2_000L, action = {
            val delay = uptimeMillis() - mMapListener.lastMoveTimeMillis.get()
            if (delay in 1000..4500) {
                updatePorts()
            }
        })

    }
    private fun clearMarkers() {
        runBlocking {
            launch {
                mMarkersCountMutex.lock()
                repeat(mMarkersCount) {
                    if (map.overlays.isNotEmpty()) {
                        map.overlays.removeLast()
                    }
                }
                mMarkersCount = 0
                mMarkersCountMutex.unlock()
            }
        }
    }
    private fun updatePorts() {
        thread {
            if (mPortUpdateAlreadyRunning.compareAndSet(false, true)) {
                if (map.repository == null) {
                    mPortUpdateAlreadyRunning.set(false)
                    return@thread
                }
                val center = if (map.mapCenter != GeoPoint(0.0, 0.0)) {
                    map.mapCenter
                } else {
                    START_POINT
                }
                val ports =
                    mareeInfoApi.getPortList(center.latitude.toFloat(), center.longitude.toFloat())
                if (ports.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this, getString(R.string.fetch_port_list_toast_error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    clearMarkers()
                    ports.forEach {
                        addMarker(map, GeoPoint(it.lat.toDouble(), it.lon.toDouble()), it.nom)
                    }
                }
                mPortUpdateAlreadyRunning.set(false)
            }
        }
    }
    private fun addMarker(map: MapView?, point: GeoPoint, title: String) {
        if (isFinishing || map == null || map?.repository == null) {
            return
        }
        println("Map: $map, map repo: ${map.repository}")
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.setOnMarkerClickListener { _,_ ->
            val markerPos = marker.position
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(marker.title)
                    .setMessage(getString(R.string.alert_choose_port_title))
                    .setPositiveButton(getString(R.string.yes)
                    ) { _,_ ->
                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        saveCoordinatesPreference(this, mAppWidgetId, markerPos)
                        thread {
                            println("LAUNCHING UPDATE APP WIDGET $mAppWidgetId")
                            BaignadeWidgetProvider.updateAppWidget(
                                this@ConfigureBaignadeWidgetActivity,
                                appWidgetManager,
                                mAppWidgetId
                            )
                        }
                        val resultValue = Intent()
                        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
                        setResult(RESULT_OK, resultValue)
                        finish()
                    }
                    .setNegativeButton(getString(R.string.no))  { _, _ -> }
                    .show()
            }
            true
        }
        runBlocking {
            launch {
                mMarkersCountMutex.lock()
                map.overlays.add(marker)
                map.invalidate()
                ++mMarkersCount
                mMarkersCountMutex.unlock()
            }
        }
    }

    override fun onDestroy() {
        if (mActionIsUpdate) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            thread {
                CoroutineScope(Dispatchers.Default).launch {
                    println("Updating Widget with previous data: $mAppWidgetId")
                    BaignadeWidgetProvider.updateAppWidget(
                        this@ConfigureBaignadeWidgetActivity,
                        appWidgetManager,
                        mAppWidgetId,
                        true
                    )
                }
            }
        }
        super.onDestroy()
    }
}