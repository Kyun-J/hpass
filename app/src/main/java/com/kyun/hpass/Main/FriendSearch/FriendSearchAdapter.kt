package com.kyun.hpass.Main.FriendSearch

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.App
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Nomal.Peoples
import com.kyun.hpass.util.objects.Codes
import com.kyun.hpass.util.objects.Singleton
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FriendSearchAdapter : BaseQuickAdapter<FriendSearchItem,BaseViewHolder> {

    constructor() : super(R.layout.item_friend_search) {
        this.setOnItemChildClickListener { adapter, view, position ->
            val item = getItem(position)
            if(item != null) {
                Singleton.RetroService.addFriend(Singleton.userToken,item.name,item.id,item.phone).enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if(response.code() == 200) {
                            val realm = Singleton.getNomalDB()
                            realm.executeTransaction {
                                val f = it.where(Peoples::class.java).equalTo("UserId",item.id).findFirst()
                                if(f == null) it.insert(Peoples().set(item.id,item.name,item.phone,true))
                                else {
                                    f.UserName = item.name
                                    f.PhoneNumber = item.phone
                                    f.isFriend = true
                                }
                                item.status = FriendSearchItem.myFriend
                                this@FriendSearchAdapter.setData(position,item)
                                com.kyun.hpass.util.objects.FriendEvent.Event()
                            }
                            realm.close()
                        } else if(response.code() == 202) {
                            if(response.body()!!.toString() == Codes.expireToken) {
                                Singleton.resetToken(mContext, {
                                    if(!it) {
                                        Singleton.loginErrToast(mContext)
                                    } else {
                                        Singleton.RetroService.addFriend(Singleton.userToken,item.name,item.id,item.phone).enqueue(this)
                                    }
                                })
                            } else Singleton.serverErrToast(mContext,response.body()!!.toString())
                        } else Singleton.serverErrToast(mContext,Codes.serverErr)
                    }

                    override fun onFailure(call: Call<ResponseBody>?, t: Throwable?) {
                        Singleton.notOnlineToast(mContext)
                    }
                })
            }
        }
    }

    override fun convert(helper: BaseViewHolder, item: FriendSearchItem) {
        helper.setText(R.id.item_friend_search_name,item.name)
        when(item.status) {
            FriendSearchItem.notFriend -> {
                helper.setText(R.id.item_friend_search_btn,"추가")
                helper.addOnClickListener(R.id.item_friend_search_btn)
            }
            FriendSearchItem.myFriend -> {
                helper.setText(R.id.item_friend_search_btn,"지인")
            }
            FriendSearchItem.BanFriend -> {
                helper.setText(R.id.item_friend_search_btn,"차단됨")
            }
        }
    }
}