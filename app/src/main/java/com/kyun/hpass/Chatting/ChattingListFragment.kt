package com.kyun.hpass.Chatting

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService

/**
 * Created by kyun on 2018. 3. 13..
 */
@SuppressLint("ValidFragment")
class ChattingListFragment : Fragment(),HService.ChatCallBack {

    var mContext : Context? = null
    var adapter : ChattingListRecyclerAdapter? = null

    var Hs : HService? = null

    val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            Hs = (p1 as HService.MqttBinder).getService()
            Hs!!.registerCallback(this@ChattingListFragment)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        mContext = context

    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater!!.inflate(R.layout.fragment_chatting_list,null)


        //adapter = ChattingListRecyclerAdapter(null)
        //chat_list_recycler.adapter = adapter


        return v

    }

    override fun ArriveChat(RoomId: String, UserName: String, Content: String, Time: String) { // 새 대화 도착
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun DetectChange(RoomId: String) { // 여기서 사용 안함
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onResume() {
        super.onResume()
        mContext!!.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onStop() {
        super.onStop()
        Hs!!.unregisterCallback(this)
        mContext!!.unbindService(conn)
    }
}