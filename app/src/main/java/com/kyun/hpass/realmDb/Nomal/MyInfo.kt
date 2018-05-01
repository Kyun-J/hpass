package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.annotations.RealmClass


@RealmClass
open class MyInfo : RealmModel {

    var Name : String = ""
    var Email : String = ""

    fun set(name : String, email : String) : MyInfo {
        Name = name
        Email = email
        return this
    }

}