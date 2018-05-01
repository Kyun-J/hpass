package com.kyun.hpass.Main

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import android.view.inputmethod.EditorInfo
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kakao.auth.ApiResponseCallback
import com.kakao.auth.AuthService
import com.kakao.auth.ISessionCallback
import com.kakao.auth.Session
import com.kakao.auth.network.response.AccessTokenInfoResponse
import com.kakao.network.ErrorResult
import com.kakao.usermgmt.UserManagement
import com.kakao.usermgmt.callback.MeResponseCallback
import com.kakao.usermgmt.response.model.UserProfile
import com.kakao.util.exception.KakaoException
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Basic.User
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_login.*
import io.realm.Realm
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity(), ISessionCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.getCurrentSession().addCallback(this)
        Session.getCurrentSession().checkAndImplicitOpen()
        setContentView(R.layout.activity_login)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Session.getCurrentSession().handleActivityResult(requestCode, resultCode, data)) return //카카오
        super.onActivityResult(requestCode, resultCode, data)
    }

    //카카오 fail
    override fun onSessionOpenFailed(exception: KakaoException) {
        Log.e("kakaoE",exception.toString())
    }

    //카카오 성공
    override fun onSessionOpened() {
        AuthService.getInstance().requestAccessTokenInfo(object : ApiResponseCallback<AccessTokenInfoResponse>(){
            override fun onNotSignedUp() {}

            override fun onSuccess(result: AccessTokenInfoResponse) {
                Singleton.getRetroService().kakaoLogin(result.toString()).enqueue(object : Callback<JsonElement> {
                    override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                        if(response.isSuccessful) {
                            if(response.body().toString() == "n") { //등록되지 않은 유저
                                //카카오로부터 유저 정보 요청
                                UserManagement.getInstance().requestMe(object : MeResponseCallback() {
                                    override fun onSuccess(Lresult: UserProfile) {
                                        newUser(Lresult.email,Lresult.nickname,Singleton.kakao,result.toString())
                                    }

                                    override fun onSessionClosed(errorResult: ErrorResult?) {
                                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                                    }

                                    override fun onNotSignedUp() {
                                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                                    }
                                })
                            } else dofinish(response.body().asJsonObject,Singleton.kakao)
                        }
                    }
                    override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }
                })
            }

            override fun onSessionClosed(errorResult: ErrorResult) {
                Log.e("kakaoE",errorResult.toString())
            }

        })
    }

    private fun newUser(email : String, name : String, type : Int, kakaoId : String?) {
        login_email.setText(email)
        login_name.setText(name)
        login_name.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_DONE) {
                Singleton.getRetroService()
                        .newUser(email,name,type,if(kakaoId != null) kakaoId else "n")
                        .enqueue(object : Callback<JsonElement> {
                            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                if(response.isSuccessful) {
                                    dofinish(response.body().asJsonObject,type)
                                }
                            }

                            override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                            }
                        })
                true
            } else false
        }
    }


    private fun dofinish(json : JsonObject,type : Int) {
        val trealm = Realm.getInstance(Singleton.uConfig)
        trealm.executeTransaction {
            val u = User()
            u.k = json.get("token").asString
            u.l = type
            it.insert(User())
        }
        trealm.close()
        Singleton.realmKey = Base64.decode(json.get("key").asString,Base64.DEFAULT)
        Realm.setDefaultConfiguration(Singleton.mConfig.encryptionKey(Singleton.realmKey).build())
        Singleton.setKey()
        startActivity(Intent(this,MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Session.getCurrentSession().removeCallback(this)
    }
}