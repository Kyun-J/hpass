package com.kyun.hpass.util.objects

import android.util.DisplayMetrics

object Status {

    lateinit var displayMetrics : DisplayMetrics
    var widthpix = 0
    var heightpix = 0
    var density = 0.toFloat()
    var statusBarH = 0

    var findLocationInterval : Long = 30*60*1000
}

