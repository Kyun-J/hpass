package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 13..
 */
open class Peoples : RealmObject() {

    @PrimaryKey
    var UserId : String = ""
    var UserName : String = ""
    var isFriend : Boolean = false
    var PhoneNumber : String = ""
    var isBan : Boolean = false

    var pChatId : String = ""

    fun set(id : String, name : String) : Peoples {
        UserName = name
        UserId = id

        return this
    }

    fun set(id : String, name : String, isfriend : Boolean) : Peoples {
        UserName = name
        UserId = id
        isFriend = isfriend

        return this
    }

    fun set(id : String, name : String, phone : String, isfriend : Boolean) : Peoples {
        UserName = name
        UserId = id
        isFriend = isfriend
        PhoneNumber = phone

        return this
    }

    fun set(id : String, name : String, phone : String, pchat : String, isfriend : Boolean) : Peoples {
        UserName = name
        UserId = id
        isFriend = isfriend
        PhoneNumber = phone
        pChatId = pchat

        return this
    }

}