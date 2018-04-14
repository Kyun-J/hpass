package com.kyun.hpass.realmDb

import io.realm.RealmModel
import io.realm.annotations.RealmClass


@RealmClass
open class MyInfo : RealmModel {

    var Name : String = ""

    fun set(name : String) : MyInfo {
        Name = name

        return this
    }

}