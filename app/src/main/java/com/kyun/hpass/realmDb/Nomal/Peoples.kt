package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 13..
 */
@RealmClass
open class Peoples : RealmModel {

    @PrimaryKey
    var UserId : String = ""
    var UserName : String = ""
    var isFriend : Boolean = false
    var PhoneNumber : String = ""
    var doRequest : Boolean = false
    var Requested : Boolean = false
    var isBan : Boolean = false

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

}