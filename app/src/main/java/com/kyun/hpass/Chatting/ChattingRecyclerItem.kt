package com.kyun.hpass.Chatting

import com.chad.library.adapter.base.entity.MultiItemEntity

class ChattingRecyclerItem : MultiItemEntity {

    companion object {
        val Istarted = 0
        val Icontinued = 1
        val Ystarted = 2
        val Ycontinued = 3
        val noti = 4
        val day = 5
    }

    var itemtype : Int = 0
    var contents : String = ""
    var name : String = ""
    var time : Long = 0


    override fun getItemType(): Int {
        return itemtype
    }
}