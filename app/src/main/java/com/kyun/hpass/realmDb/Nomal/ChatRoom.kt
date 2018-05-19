package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 20..
 */
@RealmClass
open class ChatRoom : RealmModel{

    @PrimaryKey
    var RoomId : String = ""
    var RoomName : String = ""
    var isAlarm : Boolean = true
    var Count : Int = 0

    fun set(roomid : String, roomname : String) : ChatRoom {
        RoomId = roomid
        RoomName = roomname
        return this
    }

}