package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 20..
 */
open class ChatRoom : RealmObject(){

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