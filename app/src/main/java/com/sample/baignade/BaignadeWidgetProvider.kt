package com.sample.baignade

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.*
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import java.util.*
import kotlin.concurrent.thread

class BaignadeWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_OPEN_CONFIGURATION =
            "com.sample.baignade.action.ACTION_OPEN_CONFIGURATION"
        private fun getMareeInfos(mareeInfoApi: MareeInfoApi): Pair<List<Float>, List<Float>> {
            val mareeInfoResults = mareeInfoApi.getPortInformation()
            val subList5OrLess = if (mareeInfoResults.size > 6) {
                mareeInfoResults.subList(0, 5)
            } else {
                mareeInfoResults
            }
            val xVals = subList5OrLess.map {
                (it.dayOffset*24L*60L+it.hour*60L+it.minutes).toFloat()
            }
            val yVals = subList5OrLess.map {
                it.height
            }
            return xVals to yVals
        }
        fun updateAppWidget(context: Context,
                         appWidgetManager: AppWidgetManager,
                         appWidgetId: Int,
                         usePreviousDataIfAvailable: Boolean = false
        ) {
            val remoteViews = BaignadeWidgetRemoteViews(
                context.packageName, R.layout.baignade_widget)
            remoteViews.setOpenConfigureOnClick(context, appWidgetId)

            val coordinates =
                ConfigureBaignadeWidgetActivity.loadCoordinatesPreference(context, appWidgetId)
            if (coordinates == GeoPoint(0.0, 0.0)) {
                return
            }
            val mareeInfoApi = MareeInfoApi(
                context,
                coordinates.latitude.toFloat(),
                coordinates.longitude.toFloat()
            )
            val (xVals, yVals) = runBlocking {
                withContext(Dispatchers.Default) {
                    async {
                        remoteViews.updateViewsWithLoadingImage(context)
                        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                    }
                }
                return@runBlocking getMareeInfos(mareeInfoApi)
            }
            if (usePreviousDataIfAvailable) {
                TODO("SAVE points to pref for reusing")
            } else {
                remoteViews.updateViews(
                    context,
                    appWidgetId,
                    xVals,
                    yVals,
                    mareeInfoApi.getPortName(),
                    mareeInfoApi.getWaterTemperatureInDegrees(),
                    mareeInfoApi.getCoefMin(),
                    mareeInfoApi.getCoefMax()
                )
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }

        }
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_CONFIGURATION -> {
//                println("Clicked Alleluia !!!!: WIdget Id is ${intent.extras?.getInt(EXTRA_APPWIDGET_ID)}")
            }
        }
        super.onReceive(context, intent)
    }
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each App Widget that belongs to this provider
        appWidgetIds.forEach { appWidgetId ->
            // Create an Intent to launch ExampleActivity
            thread {
                CoroutineScope(Dispatchers.Default).launch {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        appWidgetIds?.forEach { appWidgetId ->
            context?.let { context ->
                ConfigureBaignadeWidgetActivity.deleteCoordinatesPreference(context, appWidgetId)
            }
        }
        super.onDeleted(context, appWidgetIds)
    }
}