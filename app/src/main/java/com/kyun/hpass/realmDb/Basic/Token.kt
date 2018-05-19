package com.kyun.hpass.realmDb.Basic

import io.realm.RealmModel
import io.realm.annotations.RealmClass

@RealmClass
open class Token : RealmModel {

    var token : String = ""
}