package com.kyun.hpass.Main.MakeRoom

class MakeRoomItem {

    var id = ""
    var name = ""
    var checked = false

    fun set(id : String, name : String) : MakeRoomItem {
        this.id = id
        this.name = name
        return this
    }

}