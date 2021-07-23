package com.sample.baignade

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.LayoutInflater
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import com.velli20.materialunixgraph.Line
import com.velli20.materialunixgraph.LineGraph
import com.velli20.materialunixgraph.LinePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.osmdroid.util.GeoPoint
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.absoluteValue

class BaignadeWidgetProvider : AppWidgetProvider() {
    companion object {
        fun dpToPix(context: Context, value: Float): Float {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            return TypedValue.applyDimension(COMPLEX_UNIT_DIP, value, metrics)
        }
        private fun setOpenConfigureOnClick(context: Context,
                                    appWidgetId: Int) {
            val intent = Intent(context, ConfigureBaignadeWidgetActivity::class.java).apply {
                action = ACTION_APPWIDGET_CONFIGURE
                extras?.putInt(EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            RemoteViews(
                context.packageName,
                R.layout.baignade_widget
            ).apply {
                setOnClickPendingIntent(R.layout.baignade_widget, pendingIntent)
            }
        }
        private fun updateWidgetWithLoadingImage(context: Context,
                                                 appWidgetManager: AppWidgetManager,
                                                 appWidgetId: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val drawable = context.resources.getDrawableForDensity(
                    R.mipmap.ic_baignade_widget_preview,
                    DisplayMetrics.DENSITY_XXXHIGH,
                    context.resources.newTheme()
                )
                val previewImage = drawable?.toBitmap()
                previewImage?.let {
                    val imageRation = previewImage.width.toFloat() / previewImage.height.toFloat()
                    val height = 500
                    val scaledPreviewImage = previewImage.scale((height * imageRation).toInt(), height)
                    val remoteViews: RemoteViews = RemoteViews(
                        context.packageName,
                        R.layout.baignade_widget
                    ).apply {
                        setImageViewBitmap(R.id.imgView, scaledPreviewImage)
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                }
            }
        }
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
        private fun findCurrentLevel(points: XYSerie, currentTime: Int): Pair<Float, Float> {
            return points.map {
                Triple((it.first-currentTime).absoluteValue, it.first, it.second)
            }.minByOrNull { it.first }?.let { it.second to it.third } ?: 0F to 0F
        }
        private fun updateWidgetViews(context: Context,
                                 appWidgetManager: AppWidgetManager,
                                 appWidgetId: Int,
                                 xVals: List<Float>,
                                 yVals: List<Float>,
                                 portName: String,
                                 waterTemperatureInDegrees: Int,
                                 coefMin: Int,
                                 coefMax: Int) {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            val w = dpToPix(context, 335F)
            val h = dpToPix(context, 300F)

            val view = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(LayoutInflater::class.java).
                inflate(R.layout.widget_layout, null, false)
            } else {
                null
            }
            if (view == null) {
                return
            }
            val points: XYSerie = xVals.zip(yVals)
            val (sinusoidXVals, sinusoidYVals) = SinusoidBuilder(points).getSinusoidal().unzip()
            val graph: LineGraph = view.findViewById(R.id.graph)

            val line = Line()
            val sinusoidPoints = sinusoidXVals.zip(sinusoidYVals)
            sinusoidPoints.forEach {
                val point = LinePoint(it.first.toLong()*60L*1000L, it.second)
                point.drawPoint = false
                line.addPoint(point)
            }
            line.lineColor = Color.parseColor("#00b0ff")
            line.fillLine = true
            line.setFillAlpha(60) /* Set alpha of the fill color 0-255 */
            line.lineStrokeWidth = 5F
            graph.maxVerticalAxisValue = points.maxByOrNull { it.second }?.second
                ?.times(1.2F) ?: 10F
            graph.dateAxisTicks = ArrayList(xVals.map { (it*60L*1000L).toLong() }
                .dropLast(1))
            graph.title = context.getString(R.string.graphTitle)
            graph.subtitle = portName
            graph.legendSubtitle = "$waterTemperatureInDegrees°C"
            graph.legendSubsubtitle = "$coefMin-$coefMax"
            graph.dataCopyright = context.getString(R.string.data_credits_text)

            val currentTime = Calendar.getInstance()
            val hours = currentTime.get(Calendar.HOUR_OF_DAY)
            val minutes = currentTime.get(Calendar.MINUTE)
            val (time, currentLevel) = findCurrentLevel(sinusoidPoints, hours*60+minutes)
            val levelLine = Line()
            levelLine.lineColor = Color.parseColor("#00FF00")
            levelLine.lineStrokeWidth = 10F
            levelLine.addPoint(
                LinePoint(time.toLong()*60L*1000L, currentLevel).
                apply { drawPoint = true })

            graph.addLine(line)
            graph.addLine(levelLine)

            graph.layout(0, 0, w.toInt(), h.toInt())
            val bitmapImage = graph.drawToBitmap()

            val remoteViews: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.baignade_widget
            ).apply {
                setImageViewBitmap(R.id.imgView, bitmapImage)
            }
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
        fun updateAppWidget(context: Context,
                         appWidgetManager: AppWidgetManager,
                         appWidgetId: Int
        ) {
            setOpenConfigureOnClick(context, appWidgetId)
            updateWidgetWithLoadingImage(context, appWidgetManager, appWidgetId)
            val coordinates =
                ConfigureBaignadeWidgetActivity.loadCoordinatesPreference(context, appWidgetId)
            if (coordinates == GeoPoint(0.0, 0.0)) {
                return
            }
            val mareeInfoApi = MareeInfoApi(
                context,
                coordinates.latitude.toFloat(),
                coordinates.longitude.toFloat())
            val (xVals, yVals) = getMareeInfos(mareeInfoApi)
            updateWidgetViews(
                context,
                appWidgetManager,
                appWidgetId,
                xVals,
                yVals,
                mareeInfoApi.getPortName(),
                mareeInfoApi.getWaterTemperatureInDegrees(),
                mareeInfoApi.getCoefMin(),
                mareeInfoApi.getCoefMax()
            )
        }
    }
    private val mUpdateMutex = Mutex(false)
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
                    mUpdateMutex.lock()
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                    mUpdateMutex.unlock()
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