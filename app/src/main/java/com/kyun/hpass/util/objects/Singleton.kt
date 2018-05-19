package com.kyun.hpass.util.objects

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AppCompatActivity
import com.google.gson.JsonElement
import com.kyun.hpass.Chatting.Activity.ChattingActivity
import com.kyun.hpass.App
import com.kyun.hpass.realmDb.Nomal.AMigrations
import com.kyun.hpass.realmDb.NomalDB
import com.kyun.hpass.util.retrofit.RetroService
import io.realm.Realm
import io.realm.RealmConfiguration
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.GoogleApiClient
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.kakao.auth.ISessionCallback
import com.kakao.auth.KakaoSDK
import com.kakao.auth.Session
import com.kakao.util.exception.KakaoException
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Basic.TMigrations
import com.kyun.hpass.realmDb.Basic.Token
import com.kyun.hpass.realmDb.BasicDB
import com.kyun.hpass.realmDb.Nomal.MyInfo
import com.kyun.hpass.util.isNetwrok
import retrofit2.*

/**
 * Created by kyun on 2018. 3. 13..
 */
object Singleton {

    private var networkAsync : isNetwrok? = null

    var realmKey : ByteArray? = null //realmdb key
    var userToken : String? = null //token to server
    var CheckList = ArrayList<()->Unit>() //check is realmkey is exsist
    var MyId : String = "" //user id
    var MyN : String = "" //user name

    private val mConfig = RealmConfiguration.Builder() //realm config without key
            .name("Nomal.realm")
            .modules(NomalDB())
            .schemaVersion(0)
            .migration(AMigrations())
    val tConfig = RealmConfiguration.Builder() //token realm config
            .name("Basic.realm")
            .modules(BasicDB())
            .schemaVersion(0)
            .migration(TMigrations())
            .build()

    val RetroService = Retrofit.Builder()
            .baseUrl(IgnoreValues.BaseUrl)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build().create(RetroService::class.java)

    fun isChattingRoom(app : Application, id : String) : Boolean { //check on top view is chattingactivity
        return ((app as App).TopActivity as ChattingActivity).RoomId == id
    }

    fun longToDateString(time : Long) : String { //return date string
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

    fun longToTimeString(time : Long) : String { //return time string
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        val hour = if(cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
        val min = if(cal.get(Calendar.MINUTE) < 10) "0"+cal.get(Calendar.MINUTE) else cal.get(Calendar.MINUTE)
        val tt = if(cal.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
        return tt + " " + hour + ":" + min
    }

    fun init(app : App) {
        isOnNetwork(app, {
            if(it) {
                val type = PreferenceManager.getDefaultSharedPreferences(app).getString("type","")
                if (type != "") {
                    if(Singleton.realmKey == null) {
                        val trealm = getBasicDB()
                        val token = trealm.where(Token::class.java).findFirst()?.token
                        trealm.close()
                        if(token != null)
                        RetroService.simpleLogin(token).enqueue(object : Callback<JsonElement> {
                            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                if(response.isSuccessful)
                                    doinit(response.body()!!.asJsonObject)
                                else if(response.errorBody()!!.string() == Codes.userFail)
                                    serverErrToast(app,Codes.userFail)
                                else networkErrToast(app)
                            }

                            override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                                notOnlineToast(app)
                            }
                        })
                    }
                }
            }
        })
    }

    fun getNomalDB() : Realm {
        return Realm.getInstance(mConfig.encryptionKey(realmKey).build())
    }

    fun getBasicDB() : Realm {
        return Realm.getInstance(tConfig)
    }

    private fun doinit(json : JsonObject) {
        val trealm = getBasicDB()
        trealm.executeTransaction {
            it.where(Token::class.java).findFirst()!!.token = json.get("token").asString
        }
        trealm.close()
        userToken = json.get("token").asString
        realmKey = Base64.decode(json.get("key").asString,Base64.DEFAULT)
        val realm = Singleton.getNomalDB()
        MyN = realm.where(MyInfo::class.java).findFirst()!!.Name
        MyId = realm.where(MyInfo::class.java).findFirst()!!.Id
        realm.close()
        dokeyCheck()
    }

    fun keyCheck(check: () -> Unit) {
        if(realmKey != null) check()
        else CheckList.add(check)
    }

    fun dokeyCheck() {
        for (c in CheckList) c()
        CheckList = ArrayList()
    }

    fun isOnNetwork(app : App, listener : (Boolean) -> Unit) {
        if(networkAsync == null || networkAsync!!.status == AsyncTask.Status.FINISHED) {
            networkAsync = isNetwrok(app)
            networkAsync?.addListener(listener)
            networkAsync?.execute()
        } else if(networkAsync!!.status == AsyncTask.Status.PENDING) {
            networkAsync?.addListener(listener)
            networkAsync?.execute()
        }
        else networkAsync?.addListener(listener)
    }


    fun notOnlineToast(context: Context) {
        Toast.makeText(context,"인터넷이 연결되어 있지 않습니다. 인터넷에 연결해 주세요.", Toast.LENGTH_SHORT).show()
    }

    fun networkErrToast(context: Context) {
        Toast.makeText(context,"오류가 발생했습니다. 관리자에게 문의해주세요", Toast.LENGTH_SHORT).show()
    }

    fun serverErrToast(context: Context, code : String) {
        Toast.makeText(context,"오류가 발생했습니다. 관리자에게 문의해주세요 오류코드 - " + code, Toast.LENGTH_SHORT).show()
    }

    fun noMqttErrToast(context: Context) {
        Toast.makeText(context,"서버에 연결되지 않았습니다. 관리자에게 문의해주세요", Toast.LENGTH_SHORT).show()
    }

    fun loginErrToast(context: Context) {
        Toast.makeText(context,"로그인에 실패했습니다.",Toast.LENGTH_SHORT).show()
    }

    fun resetToken(app : App, count : Int, res : (Boolean) -> Unit) {
        if(count < Constant.retry) {
            isOnNetwork(app, {
                if (it) {
                    RetroService.simpleLogin(userToken!!).enqueue(object : Callback<JsonElement> {
                        override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                            if (response.isSuccessful) {
                                doinit(response.body()!!.asJsonObject)
                                res(true)
                            } else if (response.code() == 503 && response.errorBody()!!.string() == Codes.userFail) {
                                loginErrToast(app)
                                res(false)
                            } else {
                                serverErrToast(app, Codes.serverErr)
                                res(false)
                            }
                        }

                        override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                            networkErrToast(app)
                            res(false)
                        }
                    })
                }
            })
        }
    }

    fun slientLogin(activity : AppCompatActivity, type : String, done: (Boolean) -> Unit) {
        if(type == Codes.kakao) slientKakaoLogin(activity,done)
        else if(type == Codes.google) slientGoogleLogin(activity,done)
    }

    fun slientGoogleLogin(activity : FragmentActivity, done: (Boolean) -> Unit) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(activity.getString(R.string.server_client_id))
                .requestEmail()
                .build()
        // [END configure_signin]

        // Build GoogleAPIClient with the Google Sign-In API and the above options.
        val mGoogleApiClient = GoogleApiClient.Builder(activity)
                .enableAutoManage(activity /* FragmentActivity */, {
                    done(false)
                    loginErrToast(activity)
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build()

        val opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient)

        if (opr.isDone()) {
            val result = opr.get()
            if(result.signInAccount?.idToken != null) {
                RetroService.googleLogin(result.signInAccount?.idToken!!).enqueue(object : Callback<JsonElement> {
                    override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                        if(response.isSuccessful) {
                            doinit(response.body()!!.asJsonObject)
                            done(true)
                        } else {
                            done(false)
                            serverErrToast(activity,response.errorBody()!!.string())
                        }
                    }

                    override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                        done(false)
                        networkErrToast(activity)
                    }
                })
            } else {
                done(false)
                loginErrToast(activity)
                networkErrToast(activity)
            }
        } else {
            opr.setResultCallback{
                if(it.signInAccount?.idToken != null) {
                    RetroService.googleLogin(it.signInAccount?.idToken!!).enqueue(object : Callback<JsonElement> {
                        override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                            if (response.isSuccessful) {
                                doinit(response.body()!!.asJsonObject)
                                done(true)
                            } else {
                                done(false)
                                serverErrToast(activity,response.errorBody()!!.string())
                            }
                        }

                        override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                            done(false)
                            networkErrToast(activity)
                        }
                    })
                } else {
                    done(false)
                    loginErrToast(activity)
                    networkErrToast(activity)
                }
            }
        }
    }

    fun slientKakaoLogin(activity : Activity, done : (Boolean) -> Unit) {
        Session.getCurrentSession().addCallback(object : ISessionCallback {
            override fun onSessionOpened() {
                Session.getCurrentSession().removeCallback(this)
                RetroService.kakaoLogin(Session.getCurrentSession().tokenInfo.accessToken).enqueue(object : Callback<JsonElement> {
                    override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                        if(response.isSuccessful) {
                            doinit(response.body()!!.asJsonObject)
                            done(true)
                        } else {
                            done(false)
                            serverErrToast(activity,response.errorBody()!!.string())
                        }
                    }

                    override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                        done(false)
                        networkErrToast(activity)
                    }
                })
            }

            override fun onSessionOpenFailed(exception: KakaoException?) {
                done(false)
                loginErrToast(activity)
                Session.getCurrentSession().removeCallback(this)
                Log.e("kakaoE",exception.toString())
            }
        })
        Session.getCurrentSession().checkAndImplicitOpen()
        Session.getCurrentSession().open(KakaoSDK.getAdapter().getSessionConfig().getAuthTypes()[0],activity)
    }
}