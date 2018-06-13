package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 13..
 */
open class ChatList : RealmObject() {

    var RoomId : String = ""
    var ChatId : Int = 0
    var UserId : String = ""
    var Content : String = ""
    var Time : Long = 0
    var check : Boolean = false

    fun set(roomid : String, chatid : Int, userid : String, content : String , time : Long) : ChatList {
        RoomId = roomid
        ChatId = chatid
        UserId = userid
        Content = content
        Time = time

        return this
    }
}