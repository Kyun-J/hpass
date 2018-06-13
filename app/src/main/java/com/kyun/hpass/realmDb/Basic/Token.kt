package com.kyun.hpass.realmDb.Basic

import io.realm.RealmObject

open class Token : RealmObject() {

    var token : String = ""
}