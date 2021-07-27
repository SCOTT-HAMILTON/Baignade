package com.sample.baignade

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import com.velli20.materialunixgraph.Line
import com.velli20.materialunixgraph.LineGraph
import com.velli20.materialunixgraph.LinePoint
import java.util.*
import kotlin.math.absoluteValue
import kotlin.random.Random.Default.nextInt

class BaignadeWidgetRemoteViews(packageName: String, layoutId: Int):
    RemoteViews(packageName, layoutId) {
    companion object {
        private const val EXTRA_APPWIDGET_ID_CUSTOM =
            "appWidgetIdCustom"
        private const val EXTRA_APPWIDGET_IS_UPDATE =
            "appWidgetIsUpdate"
        fun dpToPix(context: Context, value: Float): Float {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
        }
        private fun findCurrentLevel(points: XYSerie, currentTime: Int): Pair<Float, Float> {
            return points.map {
                Triple((it.first-currentTime).absoluteValue, it.first, it.second)
            }.minByOrNull { it.first }?.let { it.second to it.third } ?: 0F to 0F
        }
    }
    fun updateViews(context: Context,
                    appWidgetId: Int,
                    points: XYSerie,
                    portMetadata: PortMetadata) {
        val (xVals, _) = points.unzip()
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
        graph.title = context.getString(R.string.graphTitle) + if (BuildConfig.DEBUG) {
            " ($appWidgetId)"
        } else { "" }
        graph.subtitle = portMetadata.portName
        graph.legendSubtitle = "${portMetadata.waterTemperatureInDegrees}°C"
        graph.legendSubsubtitle = "${portMetadata.coefMin}-${portMetadata.coefMax}"
        graph.dataCopyright = context.getString(R.string.data_credits_text)

        val currentTime = Calendar.getInstance()
        val hours = currentTime.get(Calendar.HOUR_OF_DAY)
        val minutes = currentTime.get(Calendar.MINUTE)
        val (time, currentLevel) = findCurrentLevel(
            sinusoidPoints,
            hours * 60 + minutes
        )
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

        setImageViewBitmap(R.id.imgView, bitmapImage)
    }
    fun updateViewsWithLoadingImage(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            println("Updating Views With Loading Image")
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
                setImageViewBitmap(R.id.imgView, scaledPreviewImage)
            }
        }
    }
    fun setOpenConfigureOnClick(context: Context,
                                appWidgetId: Int) {
        println("\n\nSetting On New On Click Listener for Widget $appWidgetId")
        val intent = Intent(context, ConfigureBaignadeWidgetActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(EXTRA_APPWIDGET_ID_CUSTOM, appWidgetId)
            putExtra(EXTRA_APPWIDGET_IS_UPDATE, true)
            flags = FLAG_ACTIVITY_NEW_TASK
        }
        val rc = nextInt(0, 10_000)
        val pendingIntent = PendingIntent.getActivity(context, rc, intent,
        PendingIntent.FLAG_UPDATE_CURRENT
        )
        setOnClickPendingIntent(R.id.imgView, pendingIntent)
    }
}