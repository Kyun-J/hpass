package com.kyun.hpass.FriendList

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kyun.hpass.App
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Nomal.Peoples
import com.kyun.hpass.util.objects.Codes
import com.kyun.hpass.util.objects.FriendEvent
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.fragment_friend_list.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@SuppressLint("ValidFragment")
class FriendListFragment : Fragment() {

    private lateinit var mContext : Context
    private val realm by lazy { Singleton.getNomalDB()}

    private lateinit var layoutManager : LinearLayoutManager
    private lateinit var adapter : FriendListAdapter

    private val fEvent : () -> Unit = {
        adapter.replaceData(getFriendList())
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_friend_list,null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Singleton.keyCheck {
            layoutManager = LinearLayoutManager(mContext)
            adapter = FriendListAdapter(getFriendList())
            friend_list_recycler.layoutManager = layoutManager
            friend_list_recycler.adapter = adapter
            FriendEvent.addList(fEvent)
            addFriendsFromPH()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(!realm.isClosed) realm.close()
        FriendEvent.removeList(fEvent)
    }

    private fun addFriendsFromPH() {
        val cursor = Singleton.postBook(mContext)
        val json = JsonObject()
        val data = JsonArray()
        while(cursor.moveToNext()) {
            val friend = JsonObject()
            friend.addProperty("phone",cursor.getString(0).replace("-","").replace("+82 ","0"))
            friend.addProperty("name",cursor.getString(1))
            data.add(friend)
        }
        cursor.close()
        json.add("friends",data)
        if(data.size() > 0) {
            Singleton.RetroService.addFriendsByPH(Singleton.userToken,json).enqueue(object : Callback<JsonElement> {
                override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                    if (response.code() == 200) {
                        val friends = response.body()!!.asJsonArray
                        for (f in friends) {
                            val ff = f.asJsonObject
                            for (d in data) {
                                val dd = d.asJsonObject
                                if (ff.get("phone").asString == dd.get("phone").asString) {
                                    realm.executeTransaction {
                                        val fi = it.where(Peoples::class.java).equalTo("UserId", ff.get("_id").asString).findFirst()
                                        if (fi == null) it.insert(Peoples().set(ff.get("_id").asString, dd.get("name").asString, dd.get("phone").asString, true))
                                        else {
                                            fi.isFriend = true
                                            fi.PhoneNumber = dd.get("phone").asString
                                            fi.UserName = dd.get("name").asString
                                        }
                                    }
                                    break
                                }
                            }
                        }
                        adapter.replaceData(getFriendList())
                    } else if(response.code() == 202) {
                        if(response.body()!!.toString() == Codes.expireToken) {
                            Singleton.resetToken(mContext, {
                                if(!it) {
                                    Singleton.loginErrToast(mContext)
                                } else {
                                    Singleton.RetroService.addFriendsByPH(Singleton.userToken,json).enqueue(this)
                                }
                            })
                        } else Singleton.serverErrToast(mContext, response.body()!!.toString())
                    } else Singleton.serverErrToast(mContext, Codes.serverErr)
                }

                override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                    Singleton.notOnlineToast(mContext)
                }
            })
            var change = false
            realm.executeTransaction {
                for(d in data) {
                    val dd = d.asJsonObject
                    val f = it.where(Peoples::class.java).equalTo("PhoneNumber",dd.get("phone").asString).findFirst()
                    if(f != null) {
                        change = true
                        f.UserName = dd.get("name").asString
                    }
                }
            }
            if(change) adapter.replaceData(getFriendList())
        }
    }

    private fun getFriendList() : ArrayList<FriendListItem> {
        val result = ArrayList<FriendListItem>()
        val friends = realm.where(Peoples::class.java)
                .equalTo("isBan",false)
                .equalTo("isFriend",true)
                .sort("UserName").findAll()
        for(f in friends) result.add(FriendListItem().set(FriendListItem.friends,f.UserId,f.UserName,""))
        return result
    }
}