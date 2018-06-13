package com.kyun.hpass.People

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.kyun.hpass.Chatting.Activity.ChattingActivity
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.realmDb.Nomal.ChatRoom
import com.kyun.hpass.realmDb.Nomal.Peoples
import com.kyun.hpass.util.objects.Singleton

class PeopleInfoDialog(val mContext: Context) : AlertDialog(mContext) {

    private lateinit var Hs : HService
    private var isBind : Boolean = false
    private val realm = Singleton.getNomalDB()

    private lateinit var mView : View

    private lateinit var name : String
    private lateinit var id : String

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mView = LayoutInflater.from(mContext).inflate(R.layout.dialog_people_info,null)
        setContentView(mView)
        mView.layoutParams.height = Singleton.weightdp(false,10.toDouble(),3.toDouble())
        mContext.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onStop() {
        super.onStop()
        if(isBind) mContext.unbindService(conn)
        realm.close()
    }

    override fun show() {
        super.show()
        mView.findViewById<TextView>(R.id.p_dia_name)?.text = name
        mView.findViewById<Button>(R.id.p_dia_chat)?.setOnClickListener {
            val p = realm.where(Peoples::class.java).equalTo("UserId",id).findFirst()
            if(p!!.pChatId == "") {
                if(isBind && Hs.isMqttAlive()) Hs.makeOnO(p)
                this.dismiss()
            }
            else {
                val isExist = realm.where(ChatRoom::class.java).equalTo("RoomId",p.pChatId).findFirst()
                if(isExist != null) {
                    mContext.startActivity(Intent(mContext, ChattingActivity::class.java).putExtra("id", p.pChatId))
                    this.dismiss()
                } else {
                    if(isBind) Hs.remakeOnO(p,p.pChatId)
                    this.dismiss()
                }
            }
        }
    }

    fun setData(id : String, name : String) : PeopleInfoDialog{
        this.id = id
        this.name = name
        return this
    }
}