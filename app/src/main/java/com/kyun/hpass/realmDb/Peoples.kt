package com.kyun.hpass.realmDb

import io.realm.RealmModel
import io.realm.annotations.RealmClass

/**
 * Created by kyun on 2018. 3. 13..
 */
@RealmClass
open class Peoples : RealmModel {

    var UserId : String = ""
        internal set
    var UserName : String = ""
        internal set
    var isFriend : Boolean = false
        internal set

    fun set(id : String) : Peoples {
        UserId = id

        return this
    }

    fun set(id : String, name : String) : Peoples {
        UserName = name
        UserId = id

        return this
    }

    fun set(id : String, name : String, friend : Boolean) : Peoples {
        UserName = name
        UserId = id
        isFriend = friend

        return this
    }

}