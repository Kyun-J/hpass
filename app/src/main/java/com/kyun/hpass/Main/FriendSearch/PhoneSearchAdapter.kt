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

class PhoneSearchAdapter : BaseQuickAdapter<PhoneSearchItem,BaseViewHolder> {

    constructor() : super(R.layout.item_phone_search) {
        this.setOnItemChildClickListener { adapter, view, position ->
            val item = getItem(position)
            if(item != null) {
                var count = 0
                val Tcall = Singleton.RetroService.addFriend(Singleton.userToken!!,item.id,item.phone)
                Tcall.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>?, response: Response<ResponseBody>) {
                        if(response.isSuccessful) {
                            val realm = Singleton.getNomalDB()
                            realm.executeTransaction {
                                val f = it.where(Peoples::class.java).equalTo("UserId",item.id).findFirst()
                                if(f == null) it.insert(Peoples().set(item.id,item.name,item.phone,true))
                                else {
                                    f.UserName = item.name
                                    f.PhoneNumber = item.phone
                                    f.isFriend = true
                                }
                                item.status = PhoneSearchItem.myFriend
                                this@PhoneSearchAdapter.setData(position,item)
                                com.kyun.hpass.util.objects.FriendEvent.Event()
                            }
                            realm.close()
                        } else if(response.code() == 503) {
                            if(response.errorBody()!!.string() == Codes.expireToken) {
                                Singleton.resetToken(mContext as App, count, {
                                    if(count >= 5) {
                                        Singleton.loginErrToast(mContext)
                                    } else {
                                        Tcall.enqueue(this)
                                        count++
                                    }
                                })
                            } else Singleton.serverErrToast(mContext,response.errorBody()!!.string())
                        } else Singleton.serverErrToast(mContext,Codes.serverErr)
                    }

                    override fun onFailure(call: Call<ResponseBody>?, t: Throwable?) {
                        Singleton.notOnlineToast(mContext)
                    }
                })
            }
        }
    }

    override fun convert(helper: BaseViewHolder, item: PhoneSearchItem) {
        helper.setText(R.id.item_phone_search_name,item.name)
        when(item.status) {
            PhoneSearchItem.notFriend -> {
                helper.setText(R.id.item_phone_search_btn,"추가")
                helper.addOnClickListener(R.id.item_phone_search_btn)
            }
            PhoneSearchItem.myFriend -> {
                helper.setText(R.id.item_phone_search_btn,"지인")
            }
            PhoneSearchItem.BanFriend -> {
                helper.setText(R.id.item_phone_search_btn,"차단됨")
            }
        }
    }
}