package com.kyun.hpass.Main.FriendSearch

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.inputmethod.EditorInfo
import com.google.gson.JsonElement
import com.kyun.hpass.App
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.Codes
import com.kyun.hpass.util.objects.Constant
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_search.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PhoneSearchActivity : AppCompatActivity() {

    private var adapter : PhoneSearchAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(search_toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "연락처로 검색"
        search_tip.text = "- 를 제외한 연락처를 입력해 주세요."
        search_edit.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_SEARCH) {
                Search(textView.text.toString())
                true
            } else false
        }
        search_edit_btn.setOnClickListener {
            Search(search_edit.text.toString())
        }
        adapter = PhoneSearchAdapter()
        search_list.adapter = adapter
        search_list.layoutManager = LinearLayoutManager(this)
    }

    private fun Search(msg : String) {
        var count = 0
        val Tcall = Singleton.RetroService.searchFriendByPH(Singleton.userToken!!,msg)
        Tcall.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>?, response: Response<JsonElement>) {
                if(response.isSuccessful) {
                    val json = response.body()!!.asJsonObject
                    if(json.get("id").asString != Singleton.MyId)
                        adapter?.replaceData(arrayListOf(PhoneSearchItem().set(
                                json.get("name").asString,
                                json.get("id").asString,
                                json.get("phone").asString,
                                if(json.get("isfriend").asBoolean) PhoneSearchItem.myFriend
                                else PhoneSearchItem.notFriend)))
                    else adapter?.replaceData(arrayListOf())
                } else if(response.code() == 503) {
                    if(response.errorBody()!!.string() == Codes.expireToken) {
                        Singleton.resetToken(application as App, count, {
                            if(count >= Constant.retry) {
                                Singleton.loginErrToast(this@PhoneSearchActivity)
                            } else {
                                Tcall.enqueue(this)
                                count++
                            }
                        })
                    } else Singleton.serverErrToast(this@PhoneSearchActivity, response.errorBody()!!.string())
                } else Singleton.serverErrToast(this@PhoneSearchActivity, Codes.serverErr)
            }

            override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                Singleton.notOnlineToast(this@PhoneSearchActivity)
            }
        })
    }

}