package com.kyun.hpass.Main.FriendSearch

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.inputmethod.EditorInfo
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kyun.hpass.App
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.Codes
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_search.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PhoneSearchActivity : AppCompatActivity() {

    private lateinit var adapter : FriendSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(search_toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "연락처로 추가"
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
        adapter = FriendSearchAdapter()
        search_list.adapter = adapter
        search_list.layoutManager = LinearLayoutManager(this)
    }

    private fun Search(msg : String) {
        Singleton.RetroService.searchFriendByPH(Singleton.userToken,msg).enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                if(response.code() == 200) {
                    if(response.body() is JsonObject) {
                        val json = response.body()!!.asJsonObject
                        if (json.get("id").asString != Singleton.MyId)
                            adapter.replaceData(arrayListOf(FriendSearchItem().set(
                                    json.get("name").asString,
                                    json.get("id").asString,
                                    json.get("phone").asString,
                                    if (json.get("isfriend").asBoolean) FriendSearchItem.myFriend
                                    else FriendSearchItem.notFriend)))
                        else adapter.replaceData(arrayListOf())
                    }
                } else if(response.code() == 202) {
                    if(response.body()!!.toString() == Codes.expireToken) {
                        Singleton.resetToken(this@PhoneSearchActivity, {
                            if(!it) {
                                Singleton.loginErrToast(this@PhoneSearchActivity)
                            } else {
                                Singleton.RetroService.searchFriendByPH(Singleton.userToken,msg).enqueue(this)
                            }
                        })
                    } else Singleton.serverErrToast(this@PhoneSearchActivity, response.body()!!.toString() )
                } else Singleton.serverErrToast(this@PhoneSearchActivity, Codes.serverErr)
            }

            override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                Singleton.notOnlineToast(this@PhoneSearchActivity)
            }
        })
    }

}