package com.kyun.hpass.realmDb

import io.realm.RealmModel
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 13..
 */
@RealmClass
open class ChatList : RealmModel {

    var RoomId : String = ""
        internal set
    var ChatId : Int = 0
        internal set
    var UserId : String = ""
        internal set
    var Content : String = ""
        internal set
    var Time : String = ""
        internal set

    fun set(roomid : String, chatid : Int, userid : String, content : String , time : String) : ChatList {
        RoomId = roomid
        ChatId = chatid
        UserId = userid
        Content = content
        Time = time

        return this
    }
}