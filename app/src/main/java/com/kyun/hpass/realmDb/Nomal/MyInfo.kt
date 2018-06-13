package com.kyun.hpass.realmDb.Nomal

import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass


open class MyInfo : RealmObject() {

    var Name : String = ""
    @PrimaryKey
    var Id : String = ""

    fun set(name : String, id : String) : MyInfo {
        Name = name
        Id = id
        return this
    }

}