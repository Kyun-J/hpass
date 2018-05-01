package com.kyun.hpass.realmDb.Nomal

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
    var doRequest : Boolean = false
        internal set
    var Requested : Boolean = false
        internal set
    var isBan : Boolean = false
        internal set

    fun set(id : String, name : String) : Peoples {
        UserName = name
        UserId = id

        return this
    }

}