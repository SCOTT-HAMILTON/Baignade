package com.sample.baignade

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock.uptimeMillis
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
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
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val lat = prefs.getFloat(LAT_PREF_PREFIX + appWidgetId, 0F)
            val lon = prefs.getFloat(LON_PREF_PREFIX + appWidgetId, 0F)
            return GeoPoint(lat.toDouble(), lon.toDouble())
        }
        fun deleteCoordinatesPreference(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            listOf(LAT_PREF_PREFIX, LON_PREF_PREFIX).forEach {
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
//
//    override fun onNewIntent(intent: Intent?) {
//        println("NEW INTENT: $intent, extras: ${intent?.extras}")
//        super.onNewIntent(intent)
//    }
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
            println("IS UPDATE: ${it.getBoolean(EXTRA_APPWIDGET_IS_UPDATE)}")
            println("INTENT mAppWidgetId: $mAppWidgetId")
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

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            println("INVALID intent: $mAppWidgetId")
            setResult(RESULT_OK, Intent())
            finish()
        }
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
                clearMarkers()
                ports.forEach {
                    addMarker(map, GeoPoint(it.lat.toDouble(), it.lon.toDouble()), it.nom)
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
                            CoroutineScope(Dispatchers.Default).launch {
                                BaignadeWidgetProvider.updateAppWidget(
                                    this@ConfigureBaignadeWidgetActivity,
                                    appWidgetManager,
                                    mAppWidgetId
                                )
                            }
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
                    BaignadeWidgetProvider.updateAppWidget(
                        this@ConfigureBaignadeWidgetActivity,
                        appWidgetManager,
                        mAppWidgetId,
//                        usePreviousDataIfAvailable =  true
                    )
                }
            }
        }
        super.onDestroy()
    }
}