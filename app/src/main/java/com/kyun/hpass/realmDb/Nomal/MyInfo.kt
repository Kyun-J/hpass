package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass


@RealmClass
open class MyInfo : RealmModel {

    var Name : String = ""
    @PrimaryKey
    var Id : String = ""

    fun set(name : String, id : String) : MyInfo {
        Name = name
        Id = id
        return this
    }

}