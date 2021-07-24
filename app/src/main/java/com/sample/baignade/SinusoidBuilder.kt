package com.sample.baignade

import kotlin.math.PI
import kotlin.math.cos


class SinusoidBuilder(private val points: XYSerie) {
    private data class Sinusoid(val D: List<Float>, val A: List<Float>, val B: List<Float>, val C: List<Float>)
    private fun linspace(start: Float, stop: Float,  num: Int = 50, endpoint: Boolean = true): List<Float> {
        if (num == 1) {
            return listOf(stop)
        }
        val step = if (endpoint) {
            (stop - start) / (num - 1)
        } else {
            (stop - start) / num
        }
        return (0 until num-1).map { start+step*it }
    }
    fun getSinusoidal() : XYSerie {
        val (D, A, B, C) = sinusoid(points)
        val cubicDomain = points.map { it.first }
        val result: MutableList<Pair<Float, Float>> = mutableListOf()
        for (i in 0..cubicDomain.size-2) {
            val linearXs = linspace(cubicDomain[i], cubicDomain[i+1], endpoint=true)
            val sinFunc = linearXs.map { D[i] + A[i]*cos(B[i]*(it-C[i])) }
            result += linearXs.zip(sinFunc)
        }
        return result.toList()
    }
    private fun sinusoid(points: XYSerie): Sinusoid {
        val d: MutableList<Float> = mutableListOf()
        val a: MutableList<Float> = mutableListOf()
        val b: MutableList<Float> = mutableListOf()
        val c: MutableList<Float> = mutableListOf()

        for (i in 0..points.size-2) {
            d += (points[i].second+points[i+1].second)/2
            a += (points[i].second-points[i+1].second)/2
            b += PI.toFloat()/(points[i+1].first-points[i].first)
            c += points[i].first
        }
        return Sinusoid(d, a, b, c)
    }
}