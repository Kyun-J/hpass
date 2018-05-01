package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 21..
 */
@RealmClass
open class ChatMember : RealmModel {

    var RoomId : String = ""
    var UserId : String = ""

    fun set(roomid : String, userid : String) : ChatMember {
        RoomId = roomid
        UserId = userid
        return this
    }

}