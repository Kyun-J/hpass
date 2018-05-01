package com.kyun.hpass.Main

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kakao.auth.ApiResponseCallback
import com.kakao.auth.ISessionCallback
import com.kakao.auth.Session
import com.kakao.network.ErrorResult
import com.kakao.usermgmt.UserManagement
import com.kakao.usermgmt.callback.MeResponseCallback
import com.kakao.usermgmt.response.model.UserProfile
import com.kakao.util.exception.KakaoException
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Basic.User
import com.kyun.hpass.realmDb.Nomal.MyInfo
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_login.*
import io.realm.Realm
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.ArrayList

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
        loginAskakao()
    }


    private fun newUser(email : String, name : String, type : Int, kakaoId : Long?) {
        login_new_user_info.visibility = View.VISIBLE
        login_buttons.visibility = View.GONE
        login_email.setText(email)
        login_name.setText(name)
        login_name.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_DONE) {
                Singleton.RetroService
                        .newUser(email,name,type,if(kakaoId != null) kakaoId else -1)
                        .enqueue(object : Callback<JsonElement> {
                            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                if(response.isSuccessful) {
                                    dofinish(response.body().asJsonObject,type,name,email)
                                }
                            }

                            override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                            }
                        })
                true
            } else false
        }
        login_done.setOnClickListener {
            Singleton.RetroService
                    .newUser(email,name,type,if(kakaoId != null) kakaoId else -1)
                    .enqueue(object : Callback<JsonElement> {
                        override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                            if(response.isSuccessful) {
                                dofinish(response.body().asJsonObject,type,name,email)
                            }
                        }

                        override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                        }
                    })
        }
    }

    private fun loginAskakao() {
        val token = Session.getCurrentSession().tokenInfo.accessToken
        Singleton.RetroService.kakaoLogin(token).enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                if(response.isSuccessful) {
                    //카카오로부터 유저 정보 요청
                    UserManagement.getInstance().requestMe(object : MeResponseCallback() {
                        override fun onSuccess(Lresult: UserProfile) {
                            if(response.body().toString() == "\"n\"") { //등록되지 않은 유저
                                newUser(Lresult.email,Lresult.nickname,Singleton.kakao,Lresult.id)
                            } else dofinish(response.body().asJsonObject,Singleton.kakao,Lresult.nickname,Lresult.email)
                        }

                        override fun onSessionClosed(errorResult: ErrorResult?) {
                            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                        }

                        override fun onNotSignedUp() {
                            kakaoSignUp(token)
                        }
                    })
                }
            }
            override fun onFailure(call: Call<JsonElement>?, t: Throwable) {
                Log.e("retroE",t.toString())
            }
        })
    }

    private fun kakaoSignUp (token : String) {
        UserManagement.getInstance().requestSignup(object : ApiResponseCallback<Long>() {
            override fun onSuccess(result: Long) {
                UserManagement.getInstance().requestMe(object : MeResponseCallback() {
                    override fun onSuccess(Lresult: UserProfile) {
                        newUser(Lresult.email,Lresult.nickname,Singleton.kakao,Lresult.id)
                    }

                    override fun onSessionClosed(errorResult: ErrorResult?) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun onNotSignedUp() {
                        kakaoSignUp(token)
                    }
                })
            }

            override fun onSessionClosed(errorResult: ErrorResult?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onNotSignedUp() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        },null)
    }

    private fun dofinish(json : JsonObject, type : Int, name : String, email : String) {
        val trealm = Realm.getInstance(Singleton.uConfig)
        trealm.executeTransaction {
            var isU = it.where(User::class.java).findFirst()
            if(isU == null) {
                isU = User()
                isU.k = json.get("token").asString
                isU.l = type
                it.insert(isU)
            } else {
                isU.k = json.get("token").asString
                isU.l = type
            }
        }
        trealm.close()
        Singleton.realmKey = Base64.decode(json.get("key").asString,Base64.DEFAULT)
        val realm = Singleton.getNomalDB()
        realm.executeTransaction {
            if (it.where(MyInfo::class.java).findFirst() == null) {
                it.insert(MyInfo().set(name,email))
            }
        }
        realm.close()
        for (c in Singleton.CheckList) c.Check()
        Singleton.CheckList = ArrayList()
        startActivity(Intent(this@LoginActivity,MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Session.getCurrentSession().removeCallback(this)
    }
}