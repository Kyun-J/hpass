package com.kyun.hpass.util.objects

import android.app.Activity
import android.app.Service
import android.content.Context
import com.kyun.hpass.Chatting.ChattingActivity
import com.kyun.hpass.App
import com.kyun.hpass.realmDb.AMigrations
import com.kyun.hpass.util.retrofit.RetroService
import io.realm.RealmConfiguration
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

/**
 * Created by kyun on 2018. 3. 13..
 */
object Singleton {

    val mConfig = RealmConfiguration.Builder().schemaVersion(0).migration(AMigrations()).build()

    var MyId : String = ""

    fun getRetroService(context : Context) : RetroService {
        return Retrofit.Builder().baseUrl(IgnoreValues.Baseurl).addConverterFactory(GsonConverterFactory.create()).build().create(RetroService::class.java)
    }

    fun isChattingRoom(context : Activity, id : String) : Boolean {
        return (((context.application as App).TopActivity is ChattingActivity) &&
                ((context.application as App).TopActivity as ChattingActivity).RoomId == id)
    }

    fun isChattingRoom(context : Service, id : String) : Boolean {
        return (((context.application as App).TopActivity is ChattingActivity) &&
                ((context.application as App).TopActivity as ChattingActivity).RoomId == id)
    }

    fun longToDateString(time : Long) : String {
        val calT = Calendar.getInstance()
        val calB = Calendar.getInstance()
        calB.timeInMillis = time
        if(calT.timeInMillis - calB.timeInMillis >= 2*24*60*60*1000) { //2일 이상전
            val month = (calB.get(Calendar.MONTH)+1).toString()
            val date = calB.get(Calendar.DATE).toString()
            if(calT.get(Calendar.YEAR) != calB.get(Calendar.YEAR)) return calB.get(Calendar.YEAR).toString()+". "+month+". "+date
            else return month+"월 "+date+"일"
        } else if(calT.get(Calendar.DAY_OF_WEEK) != calB.get(Calendar.DAY_OF_WEEK)) { //하루 전
            return "어제"
        } else {
            val hour = calB.get(Calendar.HOUR)
            val min = if(calB.get(Calendar.MINUTE) < 0) "0"+calB.get(Calendar.MINUTE) else calB.get(Calendar.MINUTE)
            val tt = if(calB.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
            return tt + " " + hour + ":" + min
        }
    }

    fun longToTimeString(time : Long) : String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        val hour = cal.get(Calendar.HOUR)
        val min = if(cal.get(Calendar.MINUTE) < 0) "0"+cal.get(Calendar.MINUTE) else cal.get(Calendar.MINUTE)
        val tt = if(cal.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
        return tt + " " + hour + ":" + min
    }
}