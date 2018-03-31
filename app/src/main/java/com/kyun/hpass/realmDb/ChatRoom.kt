package com.kyun.hpass.realmDb

import io.realm.RealmModel
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 20..
 */
@RealmClass
open class ChatRoom : RealmModel{

    var RoomId : String = ""
        internal set
    var RoomName : String = ""
        internal set
    var isAlarm : Boolean = true
        internal set
    var Count : Int = 0
        internal set

    fun set(roomid : String, roomname : String) : ChatRoom {
        RoomId = roomid
        RoomName = roomname
        return this
    }

}