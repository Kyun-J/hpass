package com.kyun.hpass.objects

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.telephony.TelephonyManager
import com.kyun.hpass.retrofit.RetroService
import io.realm.RealmConfiguration
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Created by kyun on 2018. 3. 13..
 */
object Singleton {

    val mConfig = RealmConfiguration.Builder().schemaVersion(0).build()

    var HServiceId : Int = 0

    fun getRetroService(context : Context) :RetroService {
        return Retrofit.Builder().baseUrl(IgnoreValues.Baseurl).addConverterFactory(GsonConverterFactory.create()).build().create(RetroService::class.java)
    }

    @SuppressLint("MissingPermission")
    fun getUserId(context : Context) : String {
        return (context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager).line1Number
    }


}