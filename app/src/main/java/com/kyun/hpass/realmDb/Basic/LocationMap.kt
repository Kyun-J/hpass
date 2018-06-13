package com.kyun.hpass.realmDb.Basic

import io.realm.RealmObject

open class LocationMap : RealmObject() {

    var UserId : String = ""

    var latitude : Double = 0.0
    var longtitude : Double = 0.0

    var time : Long = 0

    fun set(id : String, lati : Double, longti : Double, time : Long) : LocationMap {
        UserId = id
        latitude = lati
        longtitude = longti
        this.time = time
        return this
    }

}