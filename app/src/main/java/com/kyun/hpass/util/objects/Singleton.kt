package com.kyun.hpass.util.objects

import android.app.Activity
import android.app.Application
import android.content.Context
import android.database.Cursor
import android.preference.PreferenceManager
import android.provider.ContactsContract
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
import com.kyun.hpass.Service.HService
import com.kyun.hpass.realmDb.Basic.TMigrations
import com.kyun.hpass.realmDb.Basic.Token
import com.kyun.hpass.realmDb.BasicDB
import com.kyun.hpass.realmDb.Nomal.MyInfo
import retrofit2.*

/**
 * Created by kyun on 2018. 3. 13..
 */
object Singleton {

    lateinit var realmKey : ByteArray //realmdb key
    lateinit var userToken : String //token to server
    var MyId : String = "" //user id
    var MyN : String = "" //user name

    private var CheckList = ArrayList<()->Unit>() //check is realmkey is exsist
    val mChats = ArrayList<HService.ChatCallBack>()

    private var isIniting : Boolean = false

    private val mConfig = RealmConfiguration.Builder() //realm config without key
            .name("Nomal.realm")
            .modules(NomalDB())
            .schemaVersion(0)
            .migration(AMigrations())
    private val tConfig = RealmConfiguration.Builder() //token realm config
            .name("Basic.realm")
            .modules(BasicDB())
            .schemaVersion(1)
            .migration(TMigrations())
            .build()

    val RetroService = Retrofit.Builder()
            .baseUrl(IgnoreValues.BaseUrl)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build().create(RetroService::class.java)

    fun isinit() : Boolean {
        return ::realmKey.isInitialized && realmKey.isNotEmpty()
    }

    fun postBook(context : Context) : Cursor {
        return context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),null,null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")
    }

    fun toPhoneNum(ph : String) : String {
        return ph.replace("-","").replace("+82 ","0")
    }

    fun isChattingRoom(app : Application, id : String) : Boolean { //check on top view is chattingactivity
        return ((app as App).isForeground() && app.TopActivity is ChattingActivity && (app.TopActivity as ChattingActivity).RoomId == id)
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

    fun longToDateTimeString(time : Long) : String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        val year = cal.get(Calendar.YEAR).toString()
        val month = cal.get(Calendar.MONTH) + 1
        val date = cal.get(Calendar.DATE)
        val hour = if(cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
        val min = if(cal.get(Calendar.MINUTE) < 10) "0"+cal.get(Calendar.MINUTE) else cal.get(Calendar.MINUTE)
        val tt = if(cal.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
        return year + "-" + month + "-" + date + " " + tt + " " + hour + ":" + min
    }

    fun init(context: Context) {
        if(!isIniting) {
            val type = PreferenceManager.getDefaultSharedPreferences(context).getString("type", "")
            if (type != "" && (!::realmKey.isInitialized || realmKey.isNotEmpty())) {
                val trealm = getBasicDB()
                val token = trealm.where(Token::class.java).findFirst()?.token
                trealm.close()
                if (token != null) {
                    isIniting = true
                    isNetwork.listen {
                        if (it)
                            RetroService.simpleLogin(token).enqueue(object : Callback<JsonElement> {
                                override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                    if (response.isSuccessful)
                                        doinit(response.body()!!.asJsonObject)
                                    else if (response.errorBody()!!.string() == Codes.userFail) {
                                        serverErrToast(context, Codes.userFail)
                                        isIniting = false
                                    } else {
                                        networkErrToast(context)
                                        isIniting = false
                                    }
                                }

                                override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                                    notOnlineToast(context)
                                    isIniting = false
                                }
                            })
                    }
                }
            }
        }
    }

    private fun doinit(json : JsonObject) {
        val trealm = getBasicDB()
        trealm.executeTransaction {
            val to = it.where(Token::class.java).findFirst()
            if(to == null) {
                val nto = Token()
                nto.token = json.get("token").asString
                it.insert(nto)
            } else to.token = json.get("token").asString
        }
        trealm.close()
        userToken = json.get("token").asString
        realmKey = Base64.decode(json.get("key").asString,Base64.DEFAULT)
        val realm = Singleton.getNomalDB()
        MyN = realm.where(MyInfo::class.java).findFirst()!!.Name
        MyId = realm.where(MyInfo::class.java).findFirst()!!.Id
        realm.close()
        dokeyCheck()
        isIniting = false
    }

    fun getNomalDB() : Realm {
        return Realm.getInstance(mConfig.encryptionKey(realmKey).build())
    }

    fun getBasicDB() : Realm {
        return Realm.getInstance(tConfig)
    }


    fun keyCheck(check: () -> Unit) {
        if(::realmKey.isInitialized && realmKey.isNotEmpty()) check()
        else CheckList.add(check)
    }

    fun dokeyCheck() {
        for (c in CheckList) c()
        CheckList = ArrayList()
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

    fun resetToken(context: Context, res : (Boolean) -> Unit) {
        if(::userToken.isInitialized && userToken.isNotBlank()) {
            isNetwork.listen {
                if (it) {
                    RetroService.simpleLogin(userToken).enqueue(object : Callback<JsonElement> {
                        override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                            if (response.code() == 200) {
                                doinit(response.body()!!.asJsonObject)
                                res(true)
                            } else if (response.code() == 202 && response.body().toString() == Codes.userFail) {
                                loginErrToast(context)
                                res(false)
                            } else {
                                serverErrToast(context, Codes.serverErr)
                                res(false)
                            }
                        }

                        override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                            networkErrToast(context)
                            res(false)
                        }
                    })
                }
            }
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

    fun weightdp(isWidth : Boolean, maxWeight : Double, TargetWeight : Double) : Int {
        if(isWidth)
            return (Status.widthpix / (maxWeight*(1/TargetWeight))).toInt()
        else
            return ((Status.heightpix - Status.statusBarH) / (maxWeight*(1/TargetWeight))).toInt()
    }
}