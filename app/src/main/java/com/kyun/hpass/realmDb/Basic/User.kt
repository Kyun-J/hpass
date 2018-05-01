package com.kyun.hpass.realmDb.Basic

import io.realm.RealmModel
import io.realm.annotations.RealmClass


@RealmClass
open class User : RealmModel {

    var k : String = ""

    var l : Int = -1

}