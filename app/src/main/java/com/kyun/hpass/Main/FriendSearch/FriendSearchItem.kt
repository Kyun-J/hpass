package com.kyun.hpass.Main.FriendSearch

class FriendSearchItem {

    companion object {
        val notFriend = 0
        val myFriend = 1
        val BanFriend = 2
    }

    var name : String = ""
    var id : String = ""
    var phone : String = ""
    var status : Int = 0

    fun set(name : String, id : String, phone : String, status : Int) : FriendSearchItem {
        this.name = name
        this.phone = phone
        this.id = id
        this.status = status

        return this
    }
}