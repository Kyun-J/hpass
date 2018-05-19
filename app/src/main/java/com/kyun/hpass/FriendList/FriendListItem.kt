package com.kyun.hpass.FriendList

import com.chad.library.adapter.base.entity.MultiItemEntity

class FriendListItem : MultiItemEntity {

    companion object {
        val friends = 0
        val item = 1
    }

    var itemtype = 0
    var contents = ""
    var addInfo = ""

    fun set (itemtype : Int, contents : String, addInfo : String) : FriendListItem {
        this.itemtype = itemtype
        this.contents = contents
        this.addInfo = addInfo
        return this
    }

    override fun getItemType(): Int {
        return itemtype
    }
}