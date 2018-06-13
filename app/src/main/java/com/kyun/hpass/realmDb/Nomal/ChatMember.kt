package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import io.realm.annotations.Required

/**
 * Created by kyun on 2018. 3. 21..
 */
open class ChatMember : RealmObject() {

    @Required
    var RoomId : String = ""
    @Required
    var UserId : String = ""

    fun set(roomid : String, userid : String) : ChatMember {
        RoomId = roomid
        UserId = userid
        return this
    }

}