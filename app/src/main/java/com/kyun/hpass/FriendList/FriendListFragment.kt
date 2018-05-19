package com.kyun.hpass.FriendList

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
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
import com.kyun.hpass.util.objects.Constant
import com.kyun.hpass.util.objects.FriendEvent
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.fragment_friend_list.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@SuppressLint("ValidFragment")
class FriendListFragment : Fragment() {

    private var mContext : Context? = null
    private var realm = Singleton.getNomalDB()

    private var layoutManager : LinearLayoutManager? = null
    private var adapter : FriendListAdapter? = null

    private val fEvent : () -> Unit = {
        adapter?.replaceData(getFriendList())
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
        layoutManager = LinearLayoutManager(mContext)
        adapter = FriendListAdapter(getFriendList())
        friend_list_recycler.layoutManager = layoutManager
        friend_list_recycler.adapter = adapter
        FriendEvent.addList(fEvent)
        addFriendsFromPH()
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
        FriendEvent.removeList(fEvent)
    }

    private fun addFriendsFromPH() {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val cursor = mContext!!.contentResolver.query(uri,projection,null,null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")
        val json = JsonObject()
        val data = JsonArray()
        json.addProperty("user_token",Singleton.userToken)
        while(cursor.moveToNext()) {
            val friend = JsonObject()
            friend.addProperty("phone",cursor.getString(0).replace("-",""))
            friend.addProperty("name",cursor.getString(1))
            data.add(friend)
        }
        cursor.close()
        json.add("friends",data)
        if(data.size() > 0) {
            var count = 0
            val Tcall = Singleton.RetroService.addFriendsByPH(json)
            Tcall.enqueue(object : Callback<JsonElement> {
                override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                    if (response.isSuccessful) {
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
                        adapter?.replaceData(getFriendList())
                    } else if(response.code() == 503) {
                        if(response.errorBody()!!.string() == Codes.expireToken) {
                            Singleton.resetToken(mContext as App, count, {
                                if(count >= Constant.retry) {
                                    Singleton.loginErrToast(mContext!!)
                                } else {
                                    Tcall.enqueue(this)
                                    count++
                                }
                            })
                        } else Singleton.serverErrToast(mContext!!, response.errorBody()!!.string())
                    } else Singleton.serverErrToast(mContext!!, Codes.serverErr)
                }

                override fun onFailure(call: Call<JsonElement>?, t: Throwable?) {
                    Singleton.notOnlineToast(mContext!!)
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
            if(change) adapter?.replaceData(getFriendList())
        }
    }

    private fun getFriendList() : ArrayList<FriendListItem> {
        val result = ArrayList<FriendListItem>()
        val friends = realm.where(Peoples::class.java)
                .equalTo("isBan",false)
                .equalTo("isFriend",true)
                .sort("UserName").findAll()
        for(f in friends) result.add(FriendListItem().set(0,f.UserName,""))
        return result
    }
}