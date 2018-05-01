package com.kyun.hpass.util.objects

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.JsonElement
import com.kyun.hpass.Chatting.ChattingActivity
import com.kyun.hpass.App
import com.kyun.hpass.realmDb.Basic.UMigrations
import com.kyun.hpass.realmDb.Basic.User
import com.kyun.hpass.realmDb.BasicDB
import com.kyun.hpass.realmDb.Nomal.AMigrations
import com.kyun.hpass.realmDb.NomalDB
import com.kyun.hpass.util.retrofit.RetroService
import io.realm.Realm
import io.realm.RealmConfiguration
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import android.util.Base64

/**
 * Created by kyun on 2018. 3. 13..
 */
object Singleton {

    val kakao = 0
    val naver = 1
    val google = 2
    val facebook = 3

    var realmKey : ByteArray? = null
    private var CheckList = ArrayList<isCheck>()
    var MyId : String = ""

    val mConfig = RealmConfiguration.Builder().modules(NomalDB()).schemaVersion(0).migration(AMigrations())
    val uConfig = RealmConfiguration.Builder().modules(BasicDB()).schemaVersion(0).migration(UMigrations()).build()

    fun getRetroService() : RetroService {
        return Retrofit.Builder().baseUrl(IgnoreValues.Baseurl).addConverterFactory(GsonConverterFactory.create()).build().create(RetroService::class.java)
    }

    fun isChattingRoom(app : Application, id : String) : Boolean {
        return (((app as App).TopActivity is ChattingActivity) &&
                (app.TopActivity as ChattingActivity).RoomId == id)
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
            val hour = if(calB.get(Calendar.HOUR) == 0) 12 else calB.get(Calendar.HOUR)
            val min = if(calB.get(Calendar.MINUTE) < 10) "0"+calB.get(Calendar.MINUTE) else calB.get(Calendar.MINUTE)
            val tt = if(calB.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
            return tt + " " + hour + ":" + min
        }
    }

    fun longToTimeString(time : Long) : String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        val hour = if(cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
        val min = if(cal.get(Calendar.MINUTE) < 10) "0"+cal.get(Calendar.MINUTE) else cal.get(Calendar.MINUTE)
        val tt = if(cal.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
        return tt + " " + hour + ":" + min
    }

    fun setKey() {
        val trealm = Realm.getInstance(uConfig)
        val user = trealm.where(User::class.java).findFirst()
        if(user != null) {
            if (realmKey == null) {
                val token = trealm.where(User::class.java).findFirst()!!.k
                val retro = getRetroService()
                retro.simpleLogin(token).enqueue(object : Callback<JsonElement> {
                    override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                        if (response.isSuccessful) {
                            val json = response.body().asJsonObject
                            realmKey = Base64.decode(json.get("key").asString, Base64.DEFAULT)
                            Realm.setDefaultConfiguration(mConfig.encryptionKey(realmKey).build())
                            trealm.executeTransaction { it.where(User::class.java).findFirst()?.k = json.get("token").asString }
                            trealm.close()
                            for (c in CheckList) c.Check()
                            CheckList = ArrayList()
                        }
                    }

                    override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                        trealm.close()
                    }
                })
            } else {
                for (c in CheckList) c.Check()
                CheckList = ArrayList()
                trealm.close()
            }
        } else trealm.close()
    }

    fun addKeyCheck(check: isCheck) {
        if(realmKey != null) check.Check()
        else CheckList.add(check)
    }

    fun removeKeyCheck(check : isCheck) {
        CheckList.remove(check)
    }

    interface isCheck {
        fun Check()
    }

}