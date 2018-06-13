package com.kyun.hpass.FriendList

import com.chad.library.adapter.base.entity.MultiItemEntity

class FriendListItem : MultiItemEntity {

    companion object {
        val friends = 0
        val item = 1
    }

    var itemtype = 0
    var name = ""
    var addInfo = ""
    var id = ""

    fun set (itemtype : Int, id : String, name : String, addInfo : String) : FriendListItem {
        this.itemtype = itemtype
        this.id = id
        this.name = name
        this.addInfo = addInfo
        return this
    }

    override fun getItemType(): Int {
        return itemtype
    }
}