package com.kyun.hpass.Chatting

import android.support.v7.app.AppCompatActivity
import com.kyun.hpass.Service.HService

/**
 * Created by kyun on 2018. 3. 19..
 */
class ChattingActivity : AppCompatActivity(), HService.ChatCallBack {

    var RoomId : String = ""



    override fun ArriveChat(RoomId: String, UserName: String, Content: String, Time: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun DetectChange(RoomId: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}