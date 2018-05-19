package com.kyun.hpass.Main.Splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.tasks.Task
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kakao.auth.*
import com.kakao.network.ErrorResult
import com.kakao.usermgmt.UserManagement
import com.kakao.usermgmt.callback.MeResponseCallback
import com.kakao.usermgmt.response.model.UserProfile
import com.kakao.util.exception.KakaoException
import com.kyun.hpass.Main.MainActivity
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Basic.Token
import com.kyun.hpass.realmDb.Nomal.MyInfo
import com.kyun.hpass.util.objects.Codes
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_login.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity(), ISessionCallback, GoogleApiClient.OnConnectionFailedListener {

    //구글
    private var mGoogleApiClient : GoogleApiClient? = null
    private val RC_GET_TOKEN = 9002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        Session.getCurrentSession().addCallback(this)
        Session.getCurrentSession().checkAndImplicitOpen()

        login_google.setOnClickListener {
            getgoogleIdToken()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Session.getCurrentSession().handleActivityResult(requestCode, resultCode, data)) //카카오
            return
        else if(requestCode == RC_GET_TOKEN) //구글
            handlegoogleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(data))
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

    //구글 실패
    override fun onConnectionFailed(result: ConnectionResult) {
        Log.e("google",result.toString())
    }

    private fun makeId(id : String, type : String) : String {
        return type+'&'+id
    }

    //새 유저 등록
    private fun newUser(id : String, type: String, name : String) {
        login_new_user_info.visibility = View.VISIBLE
        login_buttons.visibility = View.GONE
        val telManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        var PhoneNum : String? =
                if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                    telManager.getLine1Number()
                else
                    "0"
        if(PhoneNum == null) PhoneNum = "0"
        else if(PhoneNum.length < 8) PhoneNum = "0"
        else PhoneNum.replace("-","")

        PhoneNum = "010100"

        if(PhoneNum != "0") login_phone.setText(PhoneNum)

        login_name.setText(name)

        login_name.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_DONE) {
                Singleton.RetroService
                        .newUser(id,textView.text.toString(),PhoneNum)
                        .enqueue(object : Callback<JsonElement> {
                            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                if(response.isSuccessful) {
                                    if(response.body().toString() == Codes.alreadyPH)
                                        Toast.makeText(this@LoginActivity,"이미 존재하는 번호입니다",Toast.LENGTH_SHORT).show()
                                    else {
                                        val json = response.body()!!.asJsonObject
                                        json.addProperty("name", textView.text.toString())
                                        dofinish(json, id, type)
                                    }
                                }
                            }

                            override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                                Singleton.networkErrToast(this@LoginActivity)
                            }
                        })
                true
            } else false
        }
        login_done.setOnClickListener {
            Singleton.RetroService
                    .newUser(id,login_name.text.toString(),PhoneNum)
                    .enqueue(object : Callback<JsonElement> {
                        override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                            if(response.isSuccessful) {
                                if(response.body().toString() == Codes.alreadyPH)
                                    Toast.makeText(this@LoginActivity,"이미 존재하는 번호입니다",Toast.LENGTH_SHORT).show()
                                else {
                                    val json = response.body()!!.asJsonObject
                                    json.addProperty("name", login_name.text.toString())
                                    dofinish(json, id, type)
                                }
                            }

                        }

                        override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                            Singleton.networkErrToast(this@LoginActivity)
                        }
                    })
        }
    }

    //구글 로그인 시도
    private fun getgoogleIdToken() {
        // Show an account picker to let the user choose a Google account from the device.
        // If the GoogleSignInOptions only asks for IDToken and/or profile and/or email then no
        // consent screen will be shown here.
        if (mGoogleApiClient == null) {
            // [START configure_signin]
            // Request only the user's ID token, which can be used to identify the
            // user securely to your backend. This will contain the user's basic
            // profile (name, profile picture URL, etc) so you should not need to
            // make an additional call to personalize your application.
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.server_client_id))
                    .requestEmail()
                    .build()
            // [END configure_signin]

            // Build GoogleAPIClient with the Google Sign-In API and the above options.
            mGoogleApiClient = GoogleApiClient.Builder(this)
                    .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .build()
        }

        val signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient)
        startActivityForResult(signInIntent, RC_GET_TOKEN)
    }


    //서버에 구글 로그인
    private fun handlegoogleSignInResult(result : Task<GoogleSignInAccount>) {
        try {
            val account = result.getResult(ApiException::class.java)
            Singleton.RetroService.googleLogin(account.idToken!!).enqueue(object : Callback<JsonElement> {
                override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                    if (response.isSuccessful) {
                        val id = makeId(account.id!!,Codes.google)
                        if (response.body().toString() == Codes.notUser) { //등록되지 않은 유저
                            newUser(id,Codes.google, account.displayName!!)
                        } else dofinish(response.body()!!.asJsonObject, id, Codes.google)
                    }
                }

                override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                    Singleton.networkErrToast(this@LoginActivity)
                }
            })

        } catch (e : ApiException) {
            Log.w("googleE", "handleSignInResult:error", e);
        }
    }

    //서버에 카카오 로그인 시도
    private fun loginAskakao() {
        val token = Session.getCurrentSession().tokenInfo.accessToken
        Singleton.RetroService.kakaoLogin(token).enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                if(response.isSuccessful) {
                    //카카오로부터 유저 정보 요청
                    UserManagement.getInstance().requestMe(object : MeResponseCallback() {
                        override fun onSuccess(Lresult: UserProfile) {
                            val id = makeId(Lresult.id.toString(),Codes.kakao)
                            if(response.body().toString() == Codes.notUser) { //등록되지 않은 유저
                                newUser(id,Codes.kakao,Lresult.nickname)
                            } else dofinish(response.body()!!.asJsonObject,id,Codes.kakao)
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
                Singleton.networkErrToast(this@LoginActivity)
            }
        })
    }

    //카카오측에 Signup. signup해야 유저 정보 가져오기 가능
    private fun kakaoSignUp (token : String) {
        UserManagement.getInstance().requestSignup(object : ApiResponseCallback<Long>() {
            override fun onSuccess(result: Long) {
                UserManagement.getInstance().requestMe(object : MeResponseCallback() {
                    override fun onSuccess(Lresult: UserProfile) {
                        newUser(makeId(Lresult.id.toString(), Codes.kakao),Codes.kakao,Lresult.nickname)
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

    private fun dofinish(json : JsonObject, id : String, type : String) {
        val edit = PreferenceManager.getDefaultSharedPreferences(this).edit()
        edit.putString("type",type)
        edit.commit()

        val utoken = json.get("token").asString

        Singleton.userToken = utoken
        Singleton.realmKey = Base64.decode(json.get("key").asString,Base64.DEFAULT)
        val trealm = Singleton.getBasicDB()
        trealm.executeTransaction {
            val to = it.where(Token::class.java).findFirst()
            if(to == null) {
                val nto = Token()
                nto.token = utoken
                it.insert(nto)
            } else
                to.token= utoken
        }
        trealm.close()
        val realm = Singleton.getNomalDB()
        realm.executeTransaction {
            val my = it.where(MyInfo::class.java).findFirst()
            if(my == null) {
                it.insert(MyInfo().set(json.get("name").asString, id))
                Singleton.MyN = json.get("name").asString
            }
            Singleton.MyId = id
        }
        realm.close()
        Singleton.dokeyCheck()

        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Session.getCurrentSession().removeCallback(this)
    }
}