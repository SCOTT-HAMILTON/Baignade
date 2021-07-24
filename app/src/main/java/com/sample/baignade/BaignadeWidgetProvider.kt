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
            val (points, portMetaData) = if (usePreviousDataIfAvailable) {
                val points = ConfigureBaignadeWidgetActivity
                    .loadPointsPreference(context, appWidgetId).unzip()
                val portMetadata = ConfigureBaignadeWidgetActivity
                    .loadPortMetadataPreference(context, appWidgetId)
                points to portMetadata
            } else {
                val (points, portMetaData) = runBlocking {
                    withContext(Dispatchers.Default) {
                        async {
                            remoteViews.updateViewsWithLoadingImage(context)
                            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                        }
                    }
                    val coordinates =
                        ConfigureBaignadeWidgetActivity.loadCoordinatesPreference(context, appWidgetId)
                    if (coordinates == GeoPoint(0.0, 0.0)) {
                        return@runBlocking (listOf<Float>() to listOf<Float>()) to
                                PortMetadata("", 0, 0, 0)
                    }
                    val mareeInfoApi = MareeInfoApi(
                        context,
                        coordinates.latitude.toFloat(),
                        coordinates.longitude.toFloat()
                    )
                    val points = getMareeInfos(mareeInfoApi)
                    val portMetaData = PortMetadata(
                        mareeInfoApi.getPortName(),
                        mareeInfoApi.getWaterTemperatureInDegrees(),
                        mareeInfoApi.getCoefMin(),
                        mareeInfoApi.getCoefMax())
                    return@runBlocking points to portMetaData
                }
                ConfigureBaignadeWidgetActivity.savePointsPreference(
                    context,appWidgetId, points.first, points.second)
                ConfigureBaignadeWidgetActivity.savePortMetadataPreference(
                    context, appWidgetId, portMetaData)
                points to portMetaData
            }
            remoteViews.updateViews(
                context,
                appWidgetId,
                points.first.zip(points.second),
                portMetaData
            )
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
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
                ConfigureBaignadeWidgetActivity.deleteWidgetPreferences(context, appWidgetId)
            }
        }
        super.onDeleted(context, appWidgetIds)
    }
}